package com.sshtunnel.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class VpnTunPacketHandler(
    private val vpnInterface: ParcelFileDescriptor,
    private val socksPort: Int,
    private val dnsServerIp: String,
    private val vpnService: VpnService?,
    private val onPacketActivity: () -> Unit,
    private val onBytesTransferred: (upload: Long, download: Long) -> Unit,
    private val onIdleStateChanged: (Boolean) -> Unit,
    private val onLog: (String, Boolean) -> Unit
) {
    private var handlerJob: Job? = null
    private var idleMonitorJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    val isSuspended = AtomicBoolean(false)
    private val lastActivityTime = AtomicLong(System.currentTimeMillis())
    private var idleTimeoutMs = 60_000L

    // Active TCP Sessions table
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    private inner class TcpSession(
        val key: String,
        val clientIp: ByteArray,
        val clientPort: Int,
        val serverIp: ByteArray,
        val serverPort: Int,
        var clientSeq: Long,
        var mySeq: Long,
        var lastAckSent: Long,
        var socksSocket: Socket? = null,
        var isEstablished: Boolean = false,
        var isClosed: Boolean = false,
        var lastPacketTime: Long = System.currentTimeMillis()
    ) {
        fun close() {
            if (isClosed) return
            isClosed = true
            runCatching { socksSocket?.close() }
            tcpSessions.remove(key)
        }
    }

    fun start(scope: CoroutineScope, idleTimeoutSeconds: Int = 60, smartIdleEnabled: Boolean = true) {
        stop()
        isRunning.set(true)
        isSuspended.set(false)
        idleTimeoutMs = (idleTimeoutSeconds.coerceAtLeast(10)) * 1000L
        lastActivityTime.set(System.currentTimeMillis())

        val inFd = FileInputStream(vpnInterface.fileDescriptor)
        val outFd = FileOutputStream(vpnInterface.fileDescriptor)

        handlerJob = scope.launch(Dispatchers.IO) {
            val packet = ByteBuffer.allocate(32768)
            onLog("TUN interface TCP/IP packet router started (SOCKS: 127.0.0.1:$socksPort, DNS: $dnsServerIp)", false)

            while (isActive && isRunning.get()) {
                try {
                    packet.clear()
                    val length = inFd.read(packet.array())
                    if (length > 0) {
                        packet.limit(length)
                        lastActivityTime.set(System.currentTimeMillis())
                        if (isSuspended.compareAndSet(true, false)) {
                            onIdleStateChanged(false)
                            onLog("Network activity detected. Resuming SSH tunnel!", false)
                        }
                        onPacketActivity()
                        onBytesTransferred(length.toLong(), 0L)

                        handlePacket(packet, length, outFd, scope)
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        break
                    }
                }
            }
        }

        idleMonitorJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get()) {
                kotlinx.coroutines.delay(5000)
                val now = System.currentTimeMillis()
                val idleTime = now - lastActivityTime.get()

                // Smart Idle suspension check
                if (smartIdleEnabled && idleTime > idleTimeoutMs && !isSuspended.get()) {
                    isSuspended.set(true)
                    onIdleStateChanged(true)
                    onLog("Selected apps idle for ${idleTime / 1000}s. Suspending tunnel keepalive.", false)
                }

                // Periodic stale TCP sessions reaper (idle > 60s) to free file descriptors and memory
                val it = tcpSessions.entries.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    val sess = entry.value
                    if (sess.isClosed || (now - sess.lastPacketTime > 60_000L)) {
                        sess.close()
                    }
                }
            }
        }
    }

    private fun handlePacket(packet: ByteBuffer, length: Int, outFd: FileOutputStream, scope: CoroutineScope) {
        if (length < 20) return
        val versionAndHeaderLen = packet.get(0).toInt() and 0xFF
        val version = versionAndHeaderLen ushr 4

        // Drop IPv6 to prevent leaks
        if (version == 6) return
        if (version != 4) return

        val ipHeaderLen = (versionAndHeaderLen and 0x0F) * 4
        val protocol = packet.get(9).toInt() and 0xFF

        val srcIp = ByteArray(4)
        val dstIp = ByteArray(4)
        packet.position(12)
        packet.get(srcIp)
        packet.get(dstIp)

        // Protocol 17: UDP (DNS query on port 53)
        if (protocol == 17 && length >= ipHeaderLen + 8) {
            val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF
            val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
            if (dstPort == 53) {
                val udpPayloadLen = length - (ipHeaderLen + 8)
                if (udpPayloadLen > 0) {
                    val queryBytes = ByteArray(udpPayloadLen)
                    System.arraycopy(packet.array(), ipHeaderLen + 8, queryBytes, 0, udpPayloadLen)
                    scope.launch(Dispatchers.IO) {
                        forwardDnsOverSocks(queryBytes, srcIp, srcPort, outFd)
                    }
                }
            }
            return
        }

        // Protocol 6: TCP
        if (protocol == 6 && length >= ipHeaderLen + 20) {
            val srcPort = packet.getShort(ipHeaderLen).toInt() and 0xFFFF
            val dstPort = packet.getShort(ipHeaderLen + 2).toInt() and 0xFFFF

            val seqNum = packet.getInt(ipHeaderLen + 4).toLong() and 0xFFFFFFFFL
            val ackNum = packet.getInt(ipHeaderLen + 8).toLong() and 0xFFFFFFFFL
            val flags = packet.getShort(ipHeaderLen + 12).toInt() and 0xFFF
            val tcpHeaderLen = ((flags ushr 12) and 0x0F) * 4
            val tcpFlags = flags and 0x1FF

            val syn = (tcpFlags and 0x02) != 0
            val ack = (tcpFlags and 0x10) != 0
            val fin = (tcpFlags and 0x01) != 0
            val rst = (tcpFlags and 0x04) != 0

            val payloadOffset = ipHeaderLen + tcpHeaderLen
            val payloadLen = (length - payloadOffset).coerceAtLeast(0)

            val sessionKey = "${formatIp(srcIp)}:$srcPort->${formatIp(dstIp)}:$dstPort"

            if (rst) {
                tcpSessions[sessionKey]?.close()
                return
            }

            if (syn && !ack) {
                // Incoming TCP SYN: Start new TCP session through SOCKS5
                if (tcpSessions.size > 200) {
                    // Evict oldest closed sessions
                    tcpSessions.entries.removeIf { it.value.isClosed }
                }

                val session = TcpSession(
                    key = sessionKey,
                    clientIp = srcIp,
                    clientPort = srcPort,
                    serverIp = dstIp,
                    serverPort = dstPort,
                    clientSeq = seqNum,
                    mySeq = 1000L,
                    lastAckSent = (seqNum + 1) and 0xFFFFFFFFL
                )
                tcpSessions[sessionKey] = session

                scope.launch(Dispatchers.IO) {
                    connectTcpThroughSocks(session, outFd)
                }
                return
            }

            val session = tcpSessions[sessionKey] ?: return

            if (payloadLen > 0) {
                // Application data (e.g. TLS ClientHello or HTTP request)
                try {
                    val payload = ByteArray(payloadLen)
                    System.arraycopy(packet.array(), payloadOffset, payload, 0, payloadLen)
                    session.socksSocket?.getOutputStream()?.apply {
                        write(payload)
                        flush()
                    }
                    session.lastPacketTime = System.currentTimeMillis()
                    session.lastAckSent = (seqNum + payloadLen) and 0xFFFFFFFFL

                    // Send TCP ACK back to TUN
                    val ackPacket = buildTcpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = session.mySeq,
                        ack = session.lastAckSent,
                        flags = 0x10, // ACK
                        payload = ByteArray(0)
                    )
                    synchronized(outFd) {
                        outFd.write(ackPacket)
                        outFd.flush()
                    }
                } catch (e: Exception) {
                    session.close()
                }
            }

            if (fin) {
                session.lastAckSent = (seqNum + 1) and 0xFFFFFFFFL
                val finAckPacket = buildTcpPacket(
                    srcIp = dstIp,
                    dstIp = srcIp,
                    srcPort = dstPort,
                    dstPort = srcPort,
                    seq = session.mySeq,
                    ack = session.lastAckSent,
                    flags = 0x11, // FIN | ACK
                    payload = ByteArray(0)
                )
                synchronized(outFd) {
                    outFd.write(finAckPacket)
                    outFd.flush()
                }
                session.close()
            }
        }
    }

    private fun connectTcpThroughSocks(session: TcpSession, outFd: FileOutputStream) {
        var socket: Socket? = null
        try {
            socket = Socket()
            vpnService?.protect(socket)
            socket.tcpNoDelay = true
            socket.soTimeout = 15000
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), 8000)

            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            // SOCKS5 Handshake
            output.writeByte(0x05)
            output.writeByte(0x01)
            output.writeByte(0x00) // No auth
            output.flush()

            val ver = input.readByte().toInt() and 0xFF
            val method = input.readByte().toInt() and 0xFF
            if (ver != 5 || method != 0) {
                session.close()
                return
            }

            // SOCKS5 CONNECT
            output.writeByte(0x05)
            output.writeByte(0x01) // CONNECT
            output.writeByte(0x00) // RSV
            output.writeByte(0x01) // IPv4
            output.write(session.serverIp)
            output.writeShort(session.serverPort)
            output.flush()

            val repVer = input.readByte().toInt() and 0xFF
            val repStatus = input.readByte().toInt() and 0xFF
            val repRsv = input.readByte().toInt() and 0xFF
            val repAtyp = input.readByte().toInt() and 0xFF
            when (repAtyp) {
                1 -> input.skipBytes(4 + 2)
                3 -> {
                    val len = input.readByte().toInt() and 0xFF
                    input.skipBytes(len + 2)
                }
                4 -> input.skipBytes(16 + 2)
            }

            if (repStatus != 0) {
                session.close()
                return
            }

            session.socksSocket = socket
            session.isEstablished = true
            socket.soTimeout = 0

            // Send SYN-ACK to TUN
            val synAck = buildTcpPacket(
                srcIp = session.serverIp,
                dstIp = session.clientIp,
                srcPort = session.serverPort,
                dstPort = session.clientPort,
                seq = session.mySeq,
                ack = session.lastAckSent,
                flags = 0x12, // SYN | ACK
                payload = ByteArray(0)
            )
            session.mySeq++

            synchronized(outFd) {
                outFd.write(synAck)
                outFd.flush()
            }

            // Read from remote socket and forward to TUN
            val buffer = ByteArray(1400)
            val inStream = socket.getInputStream()
            var read = 0
            while (!session.isClosed && inStream.read(buffer).also { read = it } != -1) {
                if (read > 0) {
                    session.lastPacketTime = System.currentTimeMillis()
                    val chunk = ByteArray(read)
                    System.arraycopy(buffer, 0, chunk, 0, read)
                    val dataPacket = buildTcpPacket(
                        srcIp = session.serverIp,
                        dstIp = session.clientIp,
                        srcPort = session.serverPort,
                        dstPort = session.clientPort,
                        seq = session.mySeq,
                        ack = session.lastAckSent,
                        flags = 0x18, // ACK | PSH
                        payload = chunk
                    )
                    session.mySeq = (session.mySeq + read) and 0xFFFFFFFFL

                    synchronized(outFd) {
                        outFd.write(dataPacket)
                        outFd.flush()
                    }
                    onBytesTransferred(0L, dataPacket.size.toLong())
                }
            }

            // Remote closed connection: send FIN-ACK to TUN
            if (!session.isClosed) {
                val finPacket = buildTcpPacket(
                    srcIp = session.serverIp,
                    dstIp = session.clientIp,
                    srcPort = session.serverPort,
                    dstPort = session.clientPort,
                    seq = session.mySeq,
                    ack = session.lastAckSent,
                    flags = 0x11, // FIN | ACK
                    payload = ByteArray(0)
                )
                synchronized(outFd) {
                    outFd.write(finPacket)
                    outFd.flush()
                }
            }

        } catch (e: Exception) {
            // Closed or timeout
        } finally {
            session.close()
        }
    }

    /**
     * Resolves DNS queries via SOCKS5 over the SSH tunnel to prevent DNS leaks.
     */
    private fun forwardDnsOverSocks(
        queryData: ByteArray,
        clientIp: ByteArray,
        clientPort: Int,
        outFd: FileOutputStream
    ) {
        var socket: Socket? = null
        try {
            socket = Socket()
            vpnService?.protect(socket)
            socket.soTimeout = 6000
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)

            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            // SOCKS5 Handshake
            output.write(byteArrayOf(0x05, 0x01, 0x00))
            output.flush()
            val ver = input.readByte().toInt() and 0xFF
            val method = input.readByte().toInt() and 0xFF
            if (ver != 5 || method != 0) return

            // Connect to DNS server port 53 via SOCKS5
            val dnsInet = InetAddress.getByName(dnsServerIp)
            output.writeByte(0x05)
            output.writeByte(0x01)
            output.writeByte(0x00)
            output.writeByte(0x01)
            output.write(dnsInet.address)
            output.writeShort(53)
            output.flush()

            val repVer = input.readByte().toInt() and 0xFF
            val repStatus = input.readByte().toInt() and 0xFF
            val repRsv = input.readByte().toInt() and 0xFF
            val repAtyp = input.readByte().toInt() and 0xFF
            when (repAtyp) {
                1 -> input.skipBytes(4 + 2)
                3 -> {
                    val len = input.readByte().toInt() and 0xFF
                    input.skipBytes(len + 2)
                }
                4 -> input.skipBytes(16 + 2)
            }
            if (repStatus != 0) return

            // RFC 7766 DNS-over-TCP: send 2-byte query length + query payload
            output.writeShort(queryData.size)
            output.write(queryData)
            output.flush()

            // Read 2-byte response length
            val respLen = input.readUnsignedShort()
            if (respLen in 1..2048) {
                val respPayload = ByteArray(respLen)
                input.readFully(respPayload)

                // Encapsulate into UDP packet and send back to TUN
                val udpResponse = buildUdpPacket(
                    srcIp = dnsInet.address,
                    dstIp = clientIp,
                    srcPort = 53,
                    dstPort = clientPort,
                    payload = respPayload,
                    payloadLen = respLen
                )
                synchronized(outFd) {
                    outFd.write(udpResponse)
                    outFd.flush()
                }
                onBytesTransferred(0L, udpResponse.size.toLong())
            }
        } catch (e: Exception) {
            // DNS resolution timeout or error
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun buildTcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray
    ): ByteArray {
        val ipLen = 20
        val tcpLen = 20
        val totalLen = ipLen + tcpLen + payload.size
        val packet = ByteArray(totalLen)

        // IPv4 Header
        packet[0] = 0x45.toByte()
        packet[1] = 0x00
        packet[2] = (totalLen ushr 8).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[4] = 0x54.toByte()
        packet[5] = 0x32.toByte()
        packet[6] = 0x40.toByte()
        packet[7] = 0x00
        packet[8] = 64.toByte() // TTL
        packet[9] = 6.toByte()  // Protocol: TCP
        packet[10] = 0
        packet[11] = 0
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipChecksum = computeChecksum(packet, 0, ipLen)
        packet[10] = (ipChecksum ushr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // TCP Header
        packet[ipLen] = (srcPort ushr 8).toByte()
        packet[ipLen + 1] = (srcPort and 0xFF).toByte()
        packet[ipLen + 2] = (dstPort ushr 8).toByte()
        packet[ipLen + 3] = (dstPort and 0xFF).toByte()

        // Seq & Ack
        packet[ipLen + 4] = ((seq ushr 24) and 0xFF).toByte()
        packet[ipLen + 5] = ((seq ushr 16) and 0xFF).toByte()
        packet[ipLen + 6] = ((seq ushr 8) and 0xFF).toByte()
        packet[ipLen + 7] = (seq and 0xFF).toByte()

        packet[ipLen + 8] = ((ack ushr 24) and 0xFF).toByte()
        packet[ipLen + 9] = ((ack ushr 16) and 0xFF).toByte()
        packet[ipLen + 10] = ((ack ushr 8) and 0xFF).toByte()
        packet[ipLen + 11] = (ack and 0xFF).toByte()

        // Data Offset & Flags
        packet[ipLen + 12] = 0x50.toByte() // Header len 20 bytes (5 * 4)
        packet[ipLen + 13] = (flags and 0xFF).toByte()

        // Window size (64 KB)
        packet[ipLen + 14] = 0xFF.toByte()
        packet[ipLen + 15] = 0xFF.toByte()

        packet[ipLen + 16] = 0 // Checksum placeholder
        packet[ipLen + 17] = 0
        packet[ipLen + 18] = 0 // Urgent pointer
        packet[ipLen + 19] = 0

        // Payload
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, ipLen + tcpLen, payload.size)
        }

        // Compute TCP Checksum with pseudo-header
        val tcpChecksum = computeTcpChecksum(packet, srcIp, dstIp, tcpLen + payload.size)
        packet[ipLen + 16] = (tcpChecksum ushr 8).toByte()
        packet[ipLen + 17] = (tcpChecksum and 0xFF).toByte()

        return packet
    }

    private fun computeTcpChecksum(packet: ByteArray, srcIp: ByteArray, dstIp: ByteArray, tcpAndPayloadLen: Int): Int {
        var sum = 0
        // Pseudo-header: Source IP (4), Dest IP (4), Zero (1), Protocol (1 = 6), TCP length (2)
        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)
        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)
        sum += 6 // Protocol TCP
        sum += tcpAndPayloadLen

        val offset = 20 // start of TCP header
        var i = 0
        while (i < tcpAndPayloadLen - 1) {
            val high = packet[offset + i].toInt() and 0xFF
            val low = packet[offset + i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i < tcpAndPayloadLen) {
            sum += (packet[offset + i].toInt() and 0xFF) shl 8
        }
        while (sum ushr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun buildUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        payloadLen: Int
    ): ByteArray {
        val ipLen = 20
        val udpLen = 8
        val totalLen = ipLen + udpLen + payloadLen
        val packet = ByteArray(totalLen)

        // IPv4 Header
        packet[0] = 0x45.toByte()
        packet[1] = 0x00
        packet[2] = (totalLen ushr 8).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[4] = 0x12.toByte()
        packet[5] = 0x34.toByte()
        packet[6] = 0x40.toByte()
        packet[7] = 0x00
        packet[8] = 64.toByte()
        packet[9] = 17.toByte() // UDP
        packet[10] = 0
        packet[11] = 0
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipChecksum = computeChecksum(packet, 0, ipLen)
        packet[10] = (ipChecksum ushr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header
        packet[ipLen] = (srcPort ushr 8).toByte()
        packet[ipLen + 1] = (srcPort and 0xFF).toByte()
        packet[ipLen + 2] = (dstPort ushr 8).toByte()
        packet[ipLen + 3] = (dstPort and 0xFF).toByte()
        val udpTotalLen = udpLen + payloadLen
        packet[ipLen + 4] = (udpTotalLen ushr 8).toByte()
        packet[ipLen + 5] = (udpTotalLen and 0xFF).toByte()
        packet[ipLen + 6] = 0
        packet[ipLen + 7] = 0

        System.arraycopy(payload, 0, packet, ipLen + udpLen, payloadLen)
        return packet
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val high = data[i].toInt() and 0xFF
            val low = data[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum ushr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun formatIp(ip: ByteArray): String {
        return "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"
    }

    fun stop() {
        isRunning.set(false)
        handlerJob?.cancel()
        handlerJob = null
        idleMonitorJob?.cancel()
        idleMonitorJob = null
        for (session in tcpSessions.values) {
            session.close()
        }
        tcpSessions.clear()
    }
}
