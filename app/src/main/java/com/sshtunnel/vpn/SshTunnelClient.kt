package com.sshtunnel.vpn

import android.net.VpnService
import com.sshtunnel.data.model.AuthType
import com.sshtunnel.data.model.SshServerProfile
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Properties

class SshTunnelClient(
    private val vpnService: VpnService?,
    private val onLog: (String, Boolean) -> Unit = { _, _ -> }
) {
    private var session: Session? = null
    private var isConnected = false

    suspend fun connect(profile: SshServerProfile): Result<Session> = withContext(Dispatchers.IO) {
        try {
            disconnect()
            onLog("Initializing SSH connection to ${profile.host}:${profile.port}...", false)

            val jsch = JSch()

            if (profile.authType == AuthType.PRIVATE_KEY && profile.privateKey.isNotBlank()) {
                onLog("Configuring SSH private key authentication...", false)
                val keyBytes = profile.privateKey.trim().toByteArray(Charsets.UTF_8)
                val passphraseBytes = if (profile.passphrase.isNotBlank()) {
                    profile.passphrase.toByteArray(Charsets.UTF_8)
                } else null
                jsch.addIdentity("vpn_key", keyBytes, null, passphraseBytes)
            }

            val newSession = jsch.getSession(profile.username, profile.host, profile.port)
            if (profile.authType == AuthType.PASSWORD && profile.password.isNotBlank()) {
                newSession.setPassword(profile.password)
            }

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", if (profile.authType == AuthType.PASSWORD) "password,keyboard-interactive" else "publickey,password")
                if (profile.compression) {
                    put("compression.s2c", "zlib@openssh.com,zlib,none")
                    put("compression.c2s", "zlib@openssh.com,zlib,none")
                }
            }
            newSession.setConfig(config)
            newSession.setServerAliveInterval(profile.keepAliveIntervalSeconds * 1000)
            newSession.timeout = 20000

            if (vpnService != null) {
                newSession.setSocketFactory(object : com.jcraft.jsch.SocketFactory {
                    override fun createSocket(host: String, port: Int): Socket {
                        val s = Socket()
                        vpnService.protect(s)
                        s.connect(InetSocketAddress(host, port), 20000)
                        return s
                    }
                    override fun getInputStream(socket: Socket) = socket.getInputStream()
                    override fun getOutputStream(socket: Socket) = socket.getOutputStream()
                })
            }

            onLog("Establishing SSH session with ${profile.username}@${profile.host}...", false)
            newSession.connect(20000)

            session = newSession
            isConnected = true
            onLog("SSH connection established successfully!", false)
            Result.success(newSession)
        } catch (e: Exception) {
            isConnected = false
            session = null
            onLog("SSH connection failed: ${e.message}", true)
            Result.failure(e)
        }
    }

    fun openDirectTcpIpChannel(targetHost: String, targetPort: Int): ChannelDirectTCPIP? {
        val currentSession = session
        if (currentSession == null || !currentSession.isConnected) return null
        return try {
            val channel = currentSession.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)
            channel.setOrgIPAddress("127.0.0.1")
            channel.setOrgPort(0)
            channel
        } catch (e: Exception) {
            onLog("Failed to open direct-tcpip channel to $targetHost:$targetPort - ${e.message}", true)
            null
        }
    }

    suspend fun measurePing(host: String, port: Int): Long? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var testSocket: Socket? = null
        try {
            testSocket = Socket()
            vpnService?.protect(testSocket)
            testSocket.connect(InetSocketAddress(host, port), 5000)
            val duration = System.currentTimeMillis() - startTime
            duration
        } catch (e: Exception) {
            null
        } finally {
            runCatching { testSocket?.close() }
        }
    }

    suspend fun testSshAuthentication(profile: SshServerProfile): Result<Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var testSession: Session? = null
        try {
            val jsch = JSch()
            if (profile.authType == AuthType.PRIVATE_KEY && profile.privateKey.isNotBlank()) {
                val keyBytes = profile.privateKey.trim().toByteArray(Charsets.UTF_8)
                val passphraseBytes = if (profile.passphrase.isNotBlank()) {
                    profile.passphrase.toByteArray(Charsets.UTF_8)
                } else null
                jsch.addIdentity("test_key", keyBytes, null, passphraseBytes)
            }

            testSession = jsch.getSession(profile.username, profile.host, profile.port)
            if (profile.authType == AuthType.PASSWORD && profile.password.isNotBlank()) {
                testSession.setPassword(profile.password)
            }

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", if (profile.authType == AuthType.PASSWORD) "password,keyboard-interactive" else "publickey,password")
            }
            testSession.setConfig(config)
            testSession.timeout = 10000

            testSession.connect(10000)
            val latency = System.currentTimeMillis() - startTime
            Result.success(latency)
        } catch (e: Exception) {
            val msg = when {
                e.message?.contains("Auth fail", ignoreCase = true) == true -> "Authentication failed: invalid username, password, or SSH key"
                e.message?.contains("invalid privatekey", ignoreCase = true) == true -> "Invalid SSH private key format or wrong passphrase"
                e.message?.contains("timeout", ignoreCase = true) == true -> "Connection timed out to ${profile.host}:${profile.port}"
                e.message?.contains("Connection refused", ignoreCase = true) == true -> "Connection refused on port ${profile.port}"
                e.message?.contains("UnknownHost", ignoreCase = true) == true -> "Unknown host: ${profile.host}"
                else -> e.message ?: "Connection test failed"
            }
            Result.failure(Exception(msg))
        } finally {
            runCatching { testSession?.disconnect() }
        }
    }

    fun isSessionActive(): Boolean {
        return session?.isConnected == true
    }

    fun disconnect() {
        try {
            if (session?.isConnected == true) {
                onLog("Disconnecting SSH session...", false)
                session?.disconnect()
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            session = null
            isConnected = false
        }
    }
}
