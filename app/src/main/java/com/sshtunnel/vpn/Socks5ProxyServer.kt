package com.sshtunnel.vpn

import android.net.VpnService
import com.jcraft.jsch.ChannelDirectTCPIP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class Socks5ProxyServer(
    val port: Int,
    private val sshClient: SshTunnelClient,
    private val vpnService: VpnService?,
    private val onBytesTransferred: (upload: Long, download: Long) -> Unit,
    private val onConnectionChange: (activeCount: Int) -> Unit,
    private val onActivity: () -> Unit,
    private val onLog: (String, Boolean) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val activeConnections = AtomicInteger(0)
    private val clientSockets = Collections.synchronizedSet(HashSet<Socket>())
    private val totalUpload = AtomicLong(0)
    private val totalDownload = AtomicLong(0)

    fun start(scope: CoroutineScope) {
        stop()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val sSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = sSocket
                onLog("Local SOCKS5 proxy server listening on 127.0.0.1:$port", false)

                while (isActive && !sSocket.isClosed) {
                    try {
                        val client = sSocket.accept()
                        vpnService?.protect(client)
                        clientSockets.add(client)
                        val count = activeConnections.incrementAndGet()
                        onConnectionChange(count)
                        onActivity()

                        scope.launch(Dispatchers.IO) {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (!sSocket.isClosed) {
                            onLog("Error accepting SOCKS5 client: ${e.message}", true)
                        }
                    }
                }
            } catch (e: Exception) {
                onLog("Failed to start local SOCKS5 server on port $port: ${e.message}", true)
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        var channel: ChannelDirectTCPIP? = null
        try {
            clientSocket.soTimeout = 30000
            val input = DataInputStream(clientSocket.getInputStream())
            val output = DataOutputStream(clientSocket.getOutputStream())

            // SOCKS5 Handshake
            val version = input.readByte().toInt() and 0xFF
            if (version != 5) {
                clientSocket.close()
                return
            }

            val numMethods = input.readByte().toInt() and 0xFF
            val methods = ByteArray(numMethods)
            input.readFully(methods)

            // Reply: No authentication required (0x05, 0x00)
            output.writeByte(0x05)
            output.writeByte(0x00)
            output.flush()

            // Read Request
            val reqVer = input.readByte().toInt() and 0xFF
            val cmd = input.readByte().toInt() and 0xFF
            val rsv = input.readByte().toInt() and 0xFF
            val atyp = input.readByte().toInt() and 0xFF

            if (cmd != 1) { // 1 = CONNECT
                // Command not supported
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
                clientSocket.close()
                return
            }

            val targetHost = when (atyp) {
                1 -> { // IPv4
                    val ip = ByteArray(4)
                    input.readFully(ip)
                    InetAddress.getByAddress(ip).hostAddress ?: "0.0.0.0"
                }
                3 -> { // Domain name
                    val domainLen = input.readByte().toInt() and 0xFF
                    val domainBytes = ByteArray(domainLen)
                    input.readFully(domainBytes)
                    String(domainBytes, Charsets.UTF_8)
                }
                4 -> { // IPv6
                    val ip6 = ByteArray(16)
                    input.readFully(ip6)
                    InetAddress.getByAddress(ip6).hostAddress ?: "::"
                }
                else -> {
                    clientSocket.close()
                    return
                }
            }

            val targetPort = input.readUnsignedShort()

            onActivity()
            channel = sshClient.openDirectTcpIpChannel(targetHost, targetPort)
            if (channel == null) {
                // Host unreachable
                output.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
                clientSocket.close()
                return
            }

            channel.connect(15000)

            // Success response
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()

            // Stream forwarding
            clientSocket.soTimeout = 0
            val channelIn = channel.inputStream
            val channelOut = channel.outputStream

            val clientToChannel = Thread {
                pipe(clientSocket.getInputStream(), channelOut, isUpload = true)
            }
            val channelToClient = Thread {
                pipe(channelIn, clientSocket.getOutputStream(), isUpload = false)
            }

            clientToChannel.start()
            channelToClient.start()

            clientToChannel.join()
            channelToClient.join()

        } catch (e: Exception) {
            // Normal close or socket reset
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { clientSocket.close() }
            clientSockets.remove(clientSocket)
            val count = activeConnections.decrementAndGet()
            onConnectionChange(count)
        }
    }

    private fun pipe(input: InputStream, output: OutputStream, isUpload: Boolean) {
        val buffer = ByteArray(16384)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
                onActivity()
                if (isUpload) {
                    totalUpload.addAndGet(read.toLong())
                    onBytesTransferred(read.toLong(), 0L)
                } else {
                    totalDownload.addAndGet(read.toLong())
                    onBytesTransferred(0L, read.toLong())
                }
            }
        } catch (e: Exception) {
            // Socket or channel closed
        }
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        synchronized(clientSockets) {
            for (socket in clientSockets) {
                runCatching { socket.close() }
            }
            clientSockets.clear()
        }
        activeConnections.set(0)
        onConnectionChange(0)
    }
}
