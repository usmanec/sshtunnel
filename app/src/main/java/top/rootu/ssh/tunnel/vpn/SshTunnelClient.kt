package top.rootu.ssh.tunnel.vpn

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.ChannelWindowControl
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import top.rootu.ssh.tunnel.data.model.AuthType
import top.rootu.ssh.tunnel.data.model.SshServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Properties

/**
 * SSH session used as the transport for the SOCKS5 proxy ("ssh -D").
 *
 * @param protectSocket excludes the SSH socket from the VPN (VpnService.protect); null for tests
 * outside the VPN service.
 */
class SshTunnelClient(
    private val protectSocket: ((Socket) -> Boolean)?,
    private val onLog: (String, Boolean) -> Unit = { _, _ -> }
) : TunnelTransport {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        /** Per-channel receive window: 4x JSch's default for better throughput on high-latency links. */
        private const val CHANNEL_WINDOW = 512 * 1024
        private const val CHANNEL_MAX_PACKET = 32 * 1024
        private const val CHANNEL_OPEN_TIMEOUT_MS = 15_000

        /** Errors that will not go away by retrying (wrong credentials or key). */
        fun isPermanentFailure(e: Throwable?): Boolean {
            val msg = e?.message ?: return false
            return msg.contains("Auth fail", ignoreCase = true) ||
                msg.contains("Auth cancel", ignoreCase = true) ||
                msg.contains("invalid privatekey", ignoreCase = true) ||
                msg.contains("USERAUTH fail", ignoreCase = true)
        }

        fun describeError(e: Throwable, profile: SshServerProfile): String {
            val msg = e.message ?: e.javaClass.simpleName
            return when {
                msg.contains("Auth fail", ignoreCase = true) || msg.contains("USERAUTH fail", ignoreCase = true) ->
                    "Authentication failed: invalid username, password, or SSH key"
                msg.contains("invalid privatekey", ignoreCase = true) -> "Invalid SSH private key format or wrong passphrase"
                msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) ->
                    "Connection timed out to ${profile.host}:${profile.port}"
                msg.contains("Connection refused", ignoreCase = true) -> "Connection refused on port ${profile.port}"
                msg.contains("UnknownHost", ignoreCase = true) || e.cause is java.net.UnknownHostException ->
                    "Unknown host: ${profile.host}"
                else -> msg
            }
        }
    }

    /** A direct-tcpip channel with streams; closing it closes the channel. */
    class SshStream(
        private val channel: ChannelDirectTCPIP,
        override val input: InputStream,
        override val output: OutputStream
    ) : TunnelStream {
        override val isClosed: Boolean get() = channel.isClosed || !channel.isConnected
        override fun close() {
            runCatching { input.close() }
            runCatching { channel.disconnect() }
        }
    }

    @Volatile
    private var session: Session? = null
    /** Session/socket of a connect in progress, so [disconnect] can abort it. */
    @Volatile
    private var pendingSession: Session? = null
    @Volatile
    private var pendingSocket: Socket? = null
    @Volatile
    private var isClosed = false

    override val isConnected: Boolean get() = session?.isConnected == true

    /** Blocking connect. Must be called off the main thread. */
    fun connect(profile: SshServerProfile): Result<Unit> {
        return try {
            onLog("Connecting to ${profile.username}@${profile.host}:${profile.port}...", false)
            val newSession = createSession(profile, CONNECT_TIMEOUT_MS)
            if (profile.keepAliveIntervalSeconds > 0) {
                // Three missed keepalives in a row mean the session is dead
                newSession.serverAliveCountMax = 3
            }
            pendingSession = newSession
            if (isClosed) throw IOException("Cancelled")
            newSession.connect(CONNECT_TIMEOUT_MS)
            if (isClosed) {
                newSession.disconnect()
                throw IOException("Cancelled")
            }
            session = newSession
            onLog("SSH session established (${newSession.serverVersion ?: "server"})", false)
            Result.success(Unit)
        } catch (e: Exception) {
            session = null
            if (!isClosed) onLog("SSH connection failed: ${describeError(e, profile)}", true)
            Result.failure(e)
        } finally {
            pendingSession = null
            pendingSocket = null
        }
    }

    /**
     * Opens "direct-tcpip" (the equivalent of a SOCKS CONNECT through "ssh -D").
     * Blocks until the server confirms the channel.
     */
    override fun openStream(host: String, port: Int): SshStream {
        val current = session
        if (current == null || !current.isConnected) throw IOException("SSH session is not connected")
        val channel = try {
            current.openChannel("direct-tcpip") as ChannelDirectTCPIP
        } catch (e: JSchException) {
            throw IOException("Cannot open SSH channel: ${e.message}", e)
        }
        channel.setHost(host)
        channel.setPort(port)
        channel.setOrgIPAddress("127.0.0.1")
        channel.setOrgPort(0)
        ChannelWindowControl.configure(channel, CHANNEL_WINDOW, CHANNEL_MAX_PACKET)

        val buffer = SshChannelBuffer(CHANNEL_WINDOW)
        buffer.channel = channel
        // Streams must be attached before connect(), otherwise early data is lost
        channel.setOutputStream(buffer.sink)
        val output = channel.outputStream
        try {
            channel.connect(CHANNEL_OPEN_TIMEOUT_MS)
        } catch (e: JSchException) {
            runCatching { channel.disconnect() }
            throw IOException("SSH channel to $host:$port rejected: ${e.message}", e)
        }
        if (!channel.isConnected) {
            runCatching { channel.disconnect() }
            throw IOException("SSH channel to $host:$port was not opened")
        }
        return SshStream(channel, buffer.source, output)
    }

    /** TCP connect time to the SSH port, used as a latency estimate. */
    suspend fun measurePing(host: String, port: Int): Long? = withContext(Dispatchers.IO) {
        val startTime = System.nanoTime()
        try {
            Socket().use { socket ->
                protectSocket?.invoke(socket)
                socket.connect(InetSocketAddress(host, port), 5000)
            }
            (System.nanoTime() - startTime) / 1_000_000
        } catch (e: Exception) {
            null
        }
    }

    suspend fun testSshAuthentication(profile: SshServerProfile): Result<Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var testSession: Session? = null
        try {
            testSession = createSession(profile, 10_000)
            testSession.connect(10_000)
            Result.success(System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            Result.failure(Exception(describeError(e, profile), e))
        } finally {
            runCatching { testSession?.disconnect() }
        }
    }

    private fun createSession(profile: SshServerProfile, timeoutMs: Int): Session {
        val jsch = JSch()
        if (profile.authType == AuthType.PRIVATE_KEY && profile.privateKey.isNotBlank()) {
            val keyBytes = profile.privateKey.trim().toByteArray(Charsets.UTF_8)
            val passphraseBytes = profile.passphrase.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity("ssh_tunnel_key", keyBytes, null, passphraseBytes)
        }

        val newSession = jsch.getSession(profile.username, profile.host, profile.port)
        if (profile.authType == AuthType.PASSWORD && profile.password.isNotEmpty()) {
            newSession.setPassword(profile.password)
        }

        val config = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put(
                "PreferredAuthentications",
                if (profile.authType == AuthType.PASSWORD) "password,keyboard-interactive" else "publickey"
            )
            // AES-GCM first: hardware-accelerated by Android's crypto provider and needs no separate MAC
            val ciphers = "aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr"
            put("cipher.s2c", ciphers)
            put("cipher.c2s", ciphers)
            if (profile.compression) {
                put("compression.s2c", "zlib@openssh.com,zlib,none")
                put("compression.c2s", "zlib@openssh.com,zlib,none")
            }
        }
        newSession.setConfig(config)
        newSession.setDaemonThread(true)
        newSession.setSocketFactory(object : SocketFactory {
            override fun createSocket(host: String, port: Int): Socket {
                val socket = Socket()
                pendingSocket = socket
                try {
                    protectSocket?.invoke(socket)
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                } catch (e: Exception) {
                    runCatching { socket.close() }
                    throw e
                }
                return socket
            }

            override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
            override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
        })
        // The keepalive interval doubles as the socket read timeout, so it has to be set last
        newSession.timeout = timeoutMs
        if (profile.keepAliveIntervalSeconds > 0) {
            newSession.setServerAliveInterval(profile.keepAliveIntervalSeconds * 1000)
        }
        return newSession
    }

    /** Closes the session or aborts a connect in progress. The client cannot be reused afterwards. */
    fun disconnect() {
        isClosed = true
        runCatching { pendingSocket?.close() }
        runCatching { pendingSession?.disconnect() }
        val current = session ?: return
        session = null
        try {
            if (current.isConnected) {
                onLog("Disconnecting SSH session...", false)
                current.disconnect()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
