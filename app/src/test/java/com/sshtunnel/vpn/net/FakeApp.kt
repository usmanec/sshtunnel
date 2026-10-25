package com.sshtunnel.vpn.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Plays the app's TCP stack on the TUN side of a [TcpProxySession]. */
class FakeApp(
    val appIp: Int = Packets.parseIpv4("10.240.0.1")!!,
    val appPort: Int = 41000,
    val serverIp: Int = Packets.parseIpv4("93.184.216.34")!!,
    val serverPort: Int = 80
) {
    val output = LinkedBlockingQueue<ByteArray>()
    val key = FlowKey(appIp, appPort, serverIp, serverPort)
    var seq = 1000
    var serverIsn = 0
    val received = ByteArrayOutputStream()
    var finReceived = false

    fun syn(window: Int = 65535, windowScale: Int = -1): Packets.TcpSegment {
        val options = if (windowScale >= 0) byteArrayOf(2, 4, 0x05, 0xB4.toByte(), 1, 3, 3, windowScale.toByte())
        else byteArrayOf(2, 4, 0x05, 0xB4.toByte())
        return segment(Packets.TCP_SYN, seq, 0, window, options = options)
    }

    fun segment(flags: Int, seq: Int, ack: Int, window: Int, payload: ByteArray? = null, options: ByteArray? = null): Packets.TcpSegment {
        val packet = Packets.buildTcp(appIp, serverIp, appPort, serverPort, seq, ack, flags, window, options, payload)
        return Packets.parseTcp(packet, packet.size)!!
    }

    /** Next packet sent to the app, parsed. */
    fun next(timeoutMs: Long = 3000): Packets.TcpSegment {
        val packet = output.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: throw AssertionError("No packet from session")
        val seg = Packets.parseTcp(packet, packet.size) ?: throw AssertionError("Not a TCP packet")
        assertEquals(serverIp, seg.srcIp)
        assertEquals(appIp, seg.dstIp)
        assertEquals(serverPort, seg.srcPort)
        assertEquals(appPort, seg.dstPort)
        return seg
    }

    fun nextOrNull(timeoutMs: Long): Packets.TcpSegment? {
        val packet = output.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
        return Packets.parseTcp(packet, packet.size)
    }

    /** Completes the handshake: expects SYN-ACK and answers with ACK. */
    fun handshake(session: TcpProxySession, window: Int) {
        val synAck = next()
        assertEquals(Packets.TCP_SYN or Packets.TCP_ACK, synAck.flags and (Packets.TCP_SYN or Packets.TCP_ACK))
        assertEquals(seq + 1, synAck.ack)
        seq += 1
        serverIsn = synAck.seq
        session.onSegment(segment(Packets.TCP_ACK, seq, serverIsn + 1, window))
    }

    /** Collects in-order data until [bytes] arrived or FIN; ACKs with [window]. */
    fun receive(session: TcpProxySession, bytes: Int, window: Int, timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (received.size() < bytes && !finReceived) {
            val left = deadline - System.currentTimeMillis()
            assertTrue("Timed out after ${received.size()} bytes", left > 0)
            val seg = next(left)
            val expected = serverIsn + 1 + received.size()
            if (seg.payloadLength > 0 && seg.seq == expected) {
                received.write(seg.data, seg.payloadOffset, seg.payloadLength)
            }
            var ackNo = serverIsn + 1 + received.size()
            if (seg.isFin && seg.seq + seg.payloadLength == ackNo) {
                finReceived = true
                ackNo += 1
            }
            session.onSegment(segment(Packets.TCP_ACK, seq, ackNo, window))
        }
    }
}
