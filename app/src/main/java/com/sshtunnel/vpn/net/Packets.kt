package com.sshtunnel.vpn.net

import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal IPv4 / TCP / UDP / ICMP parsing and building used by the userspace TUN router.
 * Pure Kotlin without Android dependencies so it can be covered by plain JVM unit tests.
 *
 * IPv4 addresses are passed around as big-endian [Int] values.
 */
object Packets {
    const val PROTO_ICMP = 1
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    private const val IP_HEADER_LEN = 20
    private const val TCP_HEADER_LEN = 20
    private const val UDP_HEADER_LEN = 8

    private val ipId = AtomicInteger(1)

    fun ipVersion(data: ByteArray): Int = (data[0].toInt() ushr 4) and 0x0F

    /** Returns the IPv4 header length or -1 when the packet is not a well-formed, unfragmented IPv4 packet. */
    fun ipv4HeaderLength(data: ByteArray, length: Int): Int {
        if (length < IP_HEADER_LEN || ipVersion(data) != 4) return -1
        val ihl = (data[0].toInt() and 0x0F) * 4
        if (ihl < IP_HEADER_LEN || ihl > length) return -1
        val totalLength = u16(data, 2)
        if (totalLength < ihl || totalLength > length) return -1
        val flagsAndOffset = u16(data, 6)
        // More Fragments or a non-zero fragment offset: fragments are not reassembled
        if ((flagsAndOffset and 0x2000) != 0 || (flagsAndOffset and 0x1FFF) != 0) return -1
        return ihl
    }

    fun ipv4TotalLength(data: ByteArray): Int = u16(data, 2)
    fun ipv4Protocol(data: ByteArray): Int = data[9].toInt() and 0xFF
    fun ipv4Source(data: ByteArray): Int = i32(data, 12)
    fun ipv4Destination(data: ByteArray): Int = i32(data, 16)

    class TcpSegment(
        val srcIp: Int,
        val dstIp: Int,
        val srcPort: Int,
        val dstPort: Int,
        val seq: Int,
        val ack: Int,
        val flags: Int,
        val window: Int,
        /** MSS option (SYN only), or -1. */
        val mss: Int,
        /** Window scale option (SYN only), or -1. */
        val windowScale: Int,
        val data: ByteArray,
        val payloadOffset: Int,
        val payloadLength: Int
    ) {
        val isSyn get() = (flags and TCP_SYN) != 0
        val isAck get() = (flags and TCP_ACK) != 0
        val isFin get() = (flags and TCP_FIN) != 0
        val isRst get() = (flags and TCP_RST) != 0

        fun copyPayload(): ByteArray = data.copyOfRange(payloadOffset, payloadOffset + payloadLength)
    }

    /** Parses a TCP segment. [data] is not copied, the caller must copy the payload if it keeps it. */
    fun parseTcp(data: ByteArray, length: Int): TcpSegment? {
        val ihl = ipv4HeaderLength(data, length)
        if (ihl < 0 || ipv4Protocol(data) != PROTO_TCP) return null
        val total = ipv4TotalLength(data)
        if (total < ihl + TCP_HEADER_LEN) return null
        val dataOffset = ((data[ihl + 12].toInt() ushr 4) and 0x0F) * 4
        if (dataOffset < TCP_HEADER_LEN || ihl + dataOffset > total) return null

        var mss = -1
        var windowScale = -1
        val flags = data[ihl + 13].toInt() and 0x3F
        if ((flags and TCP_SYN) != 0) {
            var i = ihl + TCP_HEADER_LEN
            val end = ihl + dataOffset
            while (i < end) {
                val kind = data[i].toInt() and 0xFF
                if (kind == 0) break
                if (kind == 1) { i++; continue }
                if (i + 1 >= end) break
                val len = data[i + 1].toInt() and 0xFF
                if (len < 2 || i + len > end) break
                when {
                    kind == 2 && len == 4 -> mss = u16(data, i + 2)
                    kind == 3 && len == 3 -> windowScale = (data[i + 2].toInt() and 0xFF).coerceAtMost(14)
                }
                i += len
            }
        }

        return TcpSegment(
            srcIp = ipv4Source(data),
            dstIp = ipv4Destination(data),
            srcPort = u16(data, ihl),
            dstPort = u16(data, ihl + 2),
            seq = i32(data, ihl + 4),
            ack = i32(data, ihl + 8),
            flags = flags,
            window = u16(data, ihl + 14),
            mss = mss,
            windowScale = windowScale,
            data = data,
            payloadOffset = ihl + dataOffset,
            payloadLength = total - ihl - dataOffset
        )
    }

    class UdpDatagram(
        val srcIp: Int,
        val dstIp: Int,
        val srcPort: Int,
        val dstPort: Int,
        val data: ByteArray,
        val payloadOffset: Int,
        val payloadLength: Int
    ) {
        fun copyPayload(): ByteArray = data.copyOfRange(payloadOffset, payloadOffset + payloadLength)
    }

    fun parseUdp(data: ByteArray, length: Int): UdpDatagram? {
        val ihl = ipv4HeaderLength(data, length)
        if (ihl < 0 || ipv4Protocol(data) != PROTO_UDP) return null
        val total = ipv4TotalLength(data)
        if (total < ihl + UDP_HEADER_LEN) return null
        val udpLength = u16(data, ihl + 4)
        if (udpLength < UDP_HEADER_LEN || ihl + udpLength > total) return null
        return UdpDatagram(
            srcIp = ipv4Source(data),
            dstIp = ipv4Destination(data),
            srcPort = u16(data, ihl),
            dstPort = u16(data, ihl + 2),
            data = data,
            payloadOffset = ihl + UDP_HEADER_LEN,
            payloadLength = udpLength - UDP_HEADER_LEN
        )
    }

    /**
     * Builds an IPv4 TCP segment. [options] must be padded to a multiple of 4 bytes.
     */
    fun buildTcp(
        srcIp: Int,
        dstIp: Int,
        srcPort: Int,
        dstPort: Int,
        seq: Int,
        ack: Int,
        flags: Int,
        window: Int,
        options: ByteArray? = null,
        payload: ByteArray? = null,
        payloadOffset: Int = 0,
        payloadLength: Int = payload?.size ?: 0
    ): ByteArray {
        val optionsLength = options?.size ?: 0
        val tcpHeaderLength = TCP_HEADER_LEN + optionsLength
        val totalLength = IP_HEADER_LEN + tcpHeaderLength + payloadLength
        val packet = ByteArray(totalLength)
        writeIpv4Header(packet, totalLength, PROTO_TCP, srcIp, dstIp)

        val t = IP_HEADER_LEN
        put16(packet, t, srcPort)
        put16(packet, t + 2, dstPort)
        put32(packet, t + 4, seq)
        put32(packet, t + 8, ack)
        packet[t + 12] = ((tcpHeaderLength / 4) shl 4).toByte()
        packet[t + 13] = flags.toByte()
        put16(packet, t + 14, window.coerceIn(0, 0xFFFF))
        if (options != null) System.arraycopy(options, 0, packet, t + TCP_HEADER_LEN, optionsLength)
        if (payload != null && payloadLength > 0) {
            System.arraycopy(payload, payloadOffset, packet, t + tcpHeaderLength, payloadLength)
        }
        put16(packet, t + 16, transportChecksum(packet, PROTO_TCP, srcIp, dstIp, t, tcpHeaderLength + payloadLength))
        return packet
    }

    fun buildUdp(srcIp: Int, dstIp: Int, srcPort: Int, dstPort: Int, payload: ByteArray, payloadLength: Int = payload.size): ByteArray {
        val totalLength = IP_HEADER_LEN + UDP_HEADER_LEN + payloadLength
        val packet = ByteArray(totalLength)
        writeIpv4Header(packet, totalLength, PROTO_UDP, srcIp, dstIp)
        val u = IP_HEADER_LEN
        put16(packet, u, srcPort)
        put16(packet, u + 2, dstPort)
        put16(packet, u + 4, UDP_HEADER_LEN + payloadLength)
        System.arraycopy(payload, 0, packet, u + UDP_HEADER_LEN, payloadLength)
        var checksum = transportChecksum(packet, PROTO_UDP, srcIp, dstIp, u, UDP_HEADER_LEN + payloadLength)
        if (checksum == 0) checksum = 0xFFFF
        put16(packet, u + 6, checksum)
        return packet
    }

    /**
     * ICMP "Destination unreachable / port unreachable" for an outgoing packet, so the app fails
     * fast (e.g. QUIC falls back to TCP) instead of waiting for a timeout.
     */
    fun buildIcmpPortUnreachable(original: ByteArray, originalLength: Int): ByteArray? {
        val ihl = ipv4HeaderLength(original, originalLength)
        if (ihl < 0) return null
        val quoted = minOf(originalLength, ihl + 8)
        val icmpLength = 8 + quoted
        val totalLength = IP_HEADER_LEN + icmpLength
        val packet = ByteArray(totalLength)
        val src = ipv4Destination(original)
        val dst = ipv4Source(original)
        writeIpv4Header(packet, totalLength, PROTO_ICMP, src, dst)
        val i = IP_HEADER_LEN
        packet[i] = 3 // Destination unreachable
        packet[i + 1] = 3 // Port unreachable
        System.arraycopy(original, 0, packet, i + 8, quoted)
        put16(packet, i + 2, checksum(packet, i, icmpLength, 0))
        return packet
    }

    private fun writeIpv4Header(packet: ByteArray, totalLength: Int, protocol: Int, srcIp: Int, dstIp: Int) {
        packet[0] = 0x45
        put16(packet, 2, totalLength)
        put16(packet, 4, ipId.getAndIncrement() and 0xFFFF)
        packet[6] = 0x40 // Don't Fragment
        packet[8] = 64 // TTL
        packet[9] = protocol.toByte()
        put32(packet, 12, srcIp)
        put32(packet, 16, dstIp)
        put16(packet, 10, checksum(packet, 0, IP_HEADER_LEN, 0))
    }

    private fun transportChecksum(packet: ByteArray, protocol: Int, srcIp: Int, dstIp: Int, offset: Int, length: Int): Int {
        var pseudo = 0L
        pseudo += (srcIp ushr 16).toLong() + (srcIp and 0xFFFF).toLong()
        pseudo += (dstIp ushr 16).toLong() + (dstIp and 0xFFFF).toLong()
        pseudo += protocol.toLong()
        pseudo += length.toLong()
        return checksum(packet, offset, length, pseudo)
    }

    fun checksum(data: ByteArray, offset: Int, length: Int, initial: Long): Int {
        var sum = initial
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8 or (data[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (i < end) sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    fun i32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    fun put32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 24).toByte()
        data[offset + 1] = (value ushr 16).toByte()
        data[offset + 2] = (value ushr 8).toByte()
        data[offset + 3] = value.toByte()
    }

    fun ipToBytes(ip: Int): ByteArray = byteArrayOf((ip ushr 24).toByte(), (ip ushr 16).toByte(), (ip ushr 8).toByte(), ip.toByte())

    fun ipToString(ip: Int): String = "${ip ushr 24 and 0xFF}.${ip ushr 16 and 0xFF}.${ip ushr 8 and 0xFF}.${ip and 0xFF}"

    /** Parses a dotted IPv4 literal, returns null for anything else (no DNS lookups). */
    fun parseIpv4(text: String): Int? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var ip = 0
        for (part in parts) {
            if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
            val value = part.toInt()
            if (value > 255) return null
            ip = (ip shl 8) or value
        }
        return ip
    }
}
