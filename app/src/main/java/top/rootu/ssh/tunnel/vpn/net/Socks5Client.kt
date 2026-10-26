package top.rootu.ssh.tunnel.vpn.net

import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Minimal SOCKS5 CONNECT client (no authentication) for the local proxy. */
object Socks5Client {

    class SocksException(message: String) : IOException(message)

    /**
     * Opens a TCP connection to [dstIp]:[dstPort] through the SOCKS5 proxy on
     * [proxyHost]:[proxyPort]. Returns a connected socket ready for data.
     */
    fun connect(
        proxyPort: Int,
        dstIp: Int,
        dstPort: Int,
        handshakeTimeoutMs: Int = 20_000,
        proxyHost: InetAddress = InetAddress.getLoopbackAddress()
    ): Socket {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(proxyHost, proxyPort), 5_000)
            socket.soTimeout = handshakeTimeoutMs
            val out = socket.getOutputStream()
            val input = DataInputStream(socket.getInputStream())

            // Greeting + CONNECT in one write: the proxy reads them sequentially
            val request = ByteArray(3 + 10)
            request[0] = 5; request[1] = 1; request[2] = 0
            request[3] = 5; request[4] = 1; request[5] = 0; request[6] = 1
            Packets.put32(request, 7, dstIp)
            Packets.put16(request, 11, dstPort)
            out.write(request)
            out.flush()

            if (input.readUnsignedByte() != 5 || input.readUnsignedByte() != 0) {
                throw SocksException("SOCKS5 authentication method rejected")
            }
            if (input.readUnsignedByte() != 5) throw SocksException("Bad SOCKS5 reply")
            val status = input.readUnsignedByte()
            input.readUnsignedByte() // RSV
            when (input.readUnsignedByte()) {
                1 -> input.skipFully(4 + 2)
                3 -> input.skipFully(input.readUnsignedByte() + 2)
                4 -> input.skipFully(16 + 2)
                else -> throw SocksException("Bad SOCKS5 address type")
            }
            if (status != 0) throw SocksException("SOCKS5 CONNECT failed with status $status")
            socket.soTimeout = 0
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    private fun DataInputStream.skipFully(count: Int) {
        var remaining = count
        while (remaining > 0) {
            readUnsignedByte()
            remaining--
        }
    }
}
