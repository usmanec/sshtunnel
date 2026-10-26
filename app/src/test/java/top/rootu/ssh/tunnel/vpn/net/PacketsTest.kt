package top.rootu.ssh.tunnel.vpn.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketsTest {
    private val app = Packets.parseIpv4("10.240.0.1")!!
    private val server = Packets.parseIpv4("93.184.216.34")!!

    @Test
    fun `tcp segment round trip with valid checksums`() {
        val payload = "GET / HTTP/1.1\r\n\r\n".toByteArray()
        val packet = Packets.buildTcp(
            srcIp = app, dstIp = server, srcPort = 40000, dstPort = 443,
            seq = -5, ack = 123456, flags = Packets.TCP_ACK or Packets.TCP_PSH, window = 4096, payload = payload
        )
        // IPv4 header and TCP (with pseudo header) checksums verify to zero
        assertEquals(0, Packets.checksum(packet, 0, 20, 0))
        val seg = Packets.parseTcp(packet, packet.size)!!
        assertEquals(app, seg.srcIp)
        assertEquals(server, seg.dstIp)
        assertEquals(40000, seg.srcPort)
        assertEquals(443, seg.dstPort)
        assertEquals(-5, seg.seq)
        assertEquals(123456, seg.ack)
        assertEquals(4096, seg.window)
        assertArrayEquals(payload, seg.copyPayload())
        assertEquals(0, tcpChecksumOf(packet))
    }

    @Test
    fun `syn options are parsed`() {
        val options = byteArrayOf(2, 4, 0x05, 0xB4.toByte(), 1, 3, 3, 7)
        val packet = Packets.buildTcp(app, server, 1, 2, 100, 0, Packets.TCP_SYN, 65535, options = options)
        val seg = Packets.parseTcp(packet, packet.size)!!
        assertEquals(1460, seg.mss)
        assertEquals(7, seg.windowScale)
        assertEquals(0, seg.payloadLength)
    }

    @Test
    fun `fragments and non ipv4 are rejected`() {
        val packet = Packets.buildTcp(app, server, 1, 2, 100, 0, Packets.TCP_SYN, 65535)
        val fragment = packet.copyOf().also { it[6] = 0x20 } // More Fragments
        assertNull(Packets.parseTcp(fragment, fragment.size))
        val v6 = packet.copyOf().also { it[0] = 0x60 }
        assertNull(Packets.parseTcp(v6, v6.size))
        assertNull(Packets.parseTcp(packet, 10))
    }

    @Test
    fun `udp round trip and icmp port unreachable`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val packet = Packets.buildUdp(app, server, 5353, 443, payload)
        val udp = Packets.parseUdp(packet, packet.size)!!
        assertEquals(443, udp.dstPort)
        assertArrayEquals(payload, udp.copyPayload())

        val icmp = Packets.buildIcmpPortUnreachable(packet, packet.size)!!
        assertEquals(Packets.PROTO_ICMP, Packets.ipv4Protocol(icmp))
        assertEquals(server, Packets.ipv4Source(icmp))
        assertEquals(app, Packets.ipv4Destination(icmp))
        assertEquals(3, icmp[20].toInt())
        assertEquals(3, icmp[21].toInt())
        assertEquals(0, Packets.checksum(icmp, 20, icmp.size - 20, 0))
        // Quotes the original IP header + 8 bytes of UDP header
        assertEquals(20 + 8 + 20 + 8, icmp.size)
    }

    @Test
    fun `ipv4 literal parsing`() {
        assertEquals(0x01010101, Packets.parseIpv4("1.1.1.1"))
        assertEquals("192.168.1.254", Packets.ipToString(Packets.parseIpv4("192.168.1.254")!!))
        assertNull(Packets.parseIpv4("1.1.1"))
        assertNull(Packets.parseIpv4("256.1.1.1"))
        assertNull(Packets.parseIpv4("dns.google"))
        assertNotNull(Packets.parseIpv4(" 8.8.8.8 "))
    }

    private fun tcpChecksumOf(packet: ByteArray): Int {
        val src = Packets.ipv4Source(packet)
        val dst = Packets.ipv4Destination(packet)
        val length = packet.size - 20
        var pseudo = 0L
        pseudo += (src ushr 16).toLong() + (src and 0xFFFF).toLong()
        pseudo += (dst ushr 16).toLong() + (dst and 0xFFFF).toLong()
        pseudo += Packets.PROTO_TCP + length
        return Packets.checksum(packet, 20, length, pseudo)
    }
}
