package top.rootu.ssh.tunnel.vpn

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local SOCKS5 server on 127.0.0.1 ("ssh -D"): every CONNECT becomes an SSH direct-tcpip channel.
 * Used by the TUN router and available to apps with SOCKS5 support.
 *
 * The server outlives SSH reconnects: while there is no SSH session, CONNECT requests fail
 * immediately (and the TUN router answers the app with a TCP reset).
 */
class Socks5ProxyServer(
    val port: Int,
    private val transportProvider: () -> TunnelTransport?,
    private val onBytesTransferred: (upload: Long, download: Long) -> Unit,
    private val onConnectionChange: (activeCount: Int) -> Unit,
    private val onActivity: () -> Unit,
    private val onLog: (String, Boolean) -> Unit
) {
    companion object {
        private const val BUFFER_SIZE = 32 * 1024
        private const val HANDSHAKE_TIMEOUT_MS = 30_000
    }

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private val activeConnections = AtomicInteger(0)
    private val clientSockets = Collections.synchronizedSet(HashSet<Socket>())

    /** Binds the listening socket. Throws if the port is busy so the caller can report it. */
    @Throws(IOException::class)
    fun start() {
        stop()
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 128)
        serverSocket = socket
        val pool = Executors.newCachedThreadPool(daemonThreadFactory())
        executor = pool
        pool.execute { acceptLoop(socket, pool) }
        onLog("Local SOCKS5 proxy listening on 127.0.0.1:$port", false)
    }

    private fun acceptLoop(server: ServerSocket, pool: ExecutorService) {
        while (!server.isClosed) {
            try {
                val client = server.accept()
                clientSockets.add(client)
                onConnectionChange(activeConnections.incrementAndGet())
                onActivity()
                pool.execute { handleClient(client, pool) }
            } catch (e: Exception) {
                if (!server.isClosed) onLog("SOCKS5 accept error: ${e.message}", true)
            }
        }
    }

    private fun handleClient(client: Socket, pool: ExecutorService) {
        var stream: TunnelStream? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()

            // Greeting: VER NMETHODS METHODS...
            if (input.readUnsignedByte() != 5) return
            val methods = ByteArray(input.readUnsignedByte())
            input.readFully(methods)
            if (methods.none { it.toInt() == 0 }) {
                output.write(byteArrayOf(5, 0xFF.toByte()))
                return
            }
            output.write(byteArrayOf(5, 0))

            // Request: VER CMD RSV ATYP DST.ADDR DST.PORT
            if (input.readUnsignedByte() != 5) return
            val command = input.readUnsignedByte()
            input.readUnsignedByte()
            val host = when (input.readUnsignedByte()) {
                1 -> ByteArray(4).also { input.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress }
                3 -> ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.UTF_8)
                4 -> ByteArray(16).also { input.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress }
                else -> {
                    reply(output, 8) // Address type not supported
                    return
                }
            } ?: return
            val targetPort = input.readUnsignedShort()
            if (command != 1) {
                reply(output, 7) // Only CONNECT is supported
                return
            }

            val ssh = transportProvider()
            if (ssh == null || !ssh.isConnected) {
                reply(output, 1) // General failure: tunnel is down
                return
            }
            onActivity()
            val opened = try {
                ssh.openStream(host, targetPort)
            } catch (e: IOException) {
                reply(output, 5) // Connection refused by the SSH server side
                return
            }
            stream = opened
            reply(output, 0)
            client.soTimeout = 0

            // Upload in a separate thread, download in this one
            val upload = pool.submit {
                val ok = pump(client.getInputStream(), opened.output, upload = true)
                // App finished sending: half-close towards the server (SSH EOF)
                if (ok) runCatching { opened.output.close() } else closeQuietly(client)
            }
            val downloadOk = pump(opened.input, output, upload = false)
            if (downloadOk && !opened.isClosed) {
                // Server half-closed: pass EOF on, keep uploading until the app closes too
                runCatching { client.shutdownOutput() }
                upload.get()
            } else {
                if (downloadOk) runCatching { client.shutdownOutput() }
                closeQuietly(client)
                runCatching { upload.get() }
            }
        } catch (e: Exception) {
            // Normal close or reset
        } finally {
            stream?.close()
            closeQuietly(client)
            clientSockets.remove(client)
            onConnectionChange(activeConnections.decrementAndGet().coerceAtLeast(0))
        }
    }

    private fun reply(output: OutputStream, status: Int) {
        output.write(byteArrayOf(5, status.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    /** Copies until EOF. Returns false when the copy ended with an error. */
    private fun pump(input: InputStream, output: OutputStream, upload: Boolean): Boolean {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return true
                if (read == 0) continue
                output.write(buffer, 0, read)
                output.flush()
                onActivity()
                if (upload) onBytesTransferred(read.toLong(), 0L) else onBytesTransferred(0L, read.toLong())
            }
        } catch (e: Exception) {
            return false
        }
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        synchronized(clientSockets) {
            for (socket in clientSockets) closeQuietly(socket)
            clientSockets.clear()
        }
        executor?.shutdownNow()
        executor = null
        activeConnections.set(0)
        onConnectionChange(0)
    }

    private fun daemonThreadFactory(): ThreadFactory {
        val counter = AtomicInteger(1)
        return ThreadFactory { runnable ->
            Thread(null, runnable, "socks-${counter.getAndIncrement()}", 256 * 1024).apply { isDaemon = true }
        }
    }
}
