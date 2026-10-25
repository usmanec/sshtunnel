package com.sshtunnel.vpn.net

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TcpProxySessionTest {
    private val executor = Executors.newCachedThreadPool()
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())

    @After
    fun tearDown() {
        server.close()
        executor.shutdownNow()
    }

    private fun directConnector(): (Int, Int) -> Socket = { _, _ -> Socket(InetAddress.getLoopbackAddress(), server.localPort) }

    private fun newSession(app: FakeApp, syn: Packets.TcpSegment, connector: (Int, Int) -> Socket = directConnector(),
                           clock: () -> Long = System::currentTimeMillis, closed: CountDownLatch = CountDownLatch(1)) =
        TcpProxySession(app.key, syn, { app.output.add(it) }, connector, executor, { closed.countDown() }, clock)

    @Test
    fun `request and response with half close in both directions`() {
        val serverReceived = AtomicReference<String>()
        executor.execute {
            server.accept().use { s ->
                val buf = ByteArray(5)
                var n = 0
                while (n < 5) n += s.getInputStream().read(buf, n, 5 - n)
                serverReceived.set(String(buf))
                s.getOutputStream().write("world".toByteArray())
                s.shutdownOutput()
                // Wait for the app's FIN (EOF) before closing
                while (s.getInputStream().read() >= 0) Unit
            }
        }
        val app = FakeApp()
        val closed = CountDownLatch(1)
        val session = newSession(app, app.syn(windowScale = 7), closed = closed)
        session.start()
        app.handshake(session, window = 512)

        session.onSegment(app.segment(Packets.TCP_ACK or Packets.TCP_PSH, app.seq, app.serverIsn + 1, 512, "hello".toByteArray()))
        app.seq += 5
        app.receive(session, bytes = 5, window = 512)
        assertEquals("world", app.received.toString())

        // Our data was acknowledged
        assertEquals("hello", serverReceived.get())
        app.receive(session, bytes = 6, window = 512) // waits for FIN
        assertTrue(app.finReceived)

        session.onSegment(app.segment(Packets.TCP_FIN or Packets.TCP_ACK, app.seq, app.serverIsn + 7, 512))
        val ack = app.next()
        assertEquals(app.seq + 1, ack.ack)
        assertTrue("session closes after both FINs", closed.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun `respects the app receive window and delivers everything in order`() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        executor.execute {
            server.accept().use { s ->
                s.getOutputStream().write(data)
                s.shutdownOutput()
                while (s.getInputStream().read() >= 0) Unit
            }
        }
        val app = FakeApp()
        val window = 3000 // no window scaling: raw value
        val session = newSession(app, app.syn(window = window))
        session.start()
        app.handshake(session, window)

        val deadline = System.currentTimeMillis() + 10_000
        while (!app.finReceived) {
            assertTrue("timeout at ${app.received.size()}", System.currentTimeMillis() < deadline)
            // Collect everything the session sends without acknowledging
            val burst = ArrayList<Packets.TcpSegment>()
            var seg = app.nextOrNull(500)
            while (seg != null) {
                burst += seg
                seg = app.nextOrNull(100)
            }
            val inFlight = burst.filter { it.payloadLength > 0 }.sumOf { it.payloadLength }
            assertTrue("in flight $inFlight exceeds window", inFlight <= window)
            for (s in burst.sortedBy { it.seq - app.serverIsn }) {
                val expected = app.serverIsn + 1 + app.received.size()
                if (s.payloadLength > 0 && s.seq == expected) app.received.write(s.data, s.payloadOffset, s.payloadLength)
                if (s.isFin && s.seq == app.serverIsn + 1 + app.received.size()) app.finReceived = true
            }
            val ackNo = app.serverIsn + 1 + app.received.size() + if (app.finReceived) 1 else 0
            session.onSegment(app.segment(Packets.TCP_ACK, app.seq, ackNo, window))
        }
        assertArrayEquals(data, app.received.toByteArray())
    }

    @Test
    fun `retransmits unacknowledged data after the timeout`() {
        var now = 1_000_000L
        // Keep the server side open until the end: an EOF would add a FIN to the packet stream
        val release = CountDownLatch(1)
        executor.execute {
            server.accept().use { s ->
                s.getOutputStream().write("ping".toByteArray())
                release.await(10, TimeUnit.SECONDS)
            }
        }
        try {
            val app = FakeApp()
            val session = newSession(app, app.syn(), clock = { now })
            session.start()
            app.handshake(session, 65535)
            var first = app.next()
            while (first.payloadLength == 0) first = app.next()
            assertEquals(4, first.payloadLength)
            now += 1000
            session.tick(now)
            var again = app.next()
            while (again.payloadLength == 0) again = app.next()
            assertEquals(first.seq, again.seq)
            assertArrayEquals(first.copyPayload(), again.copyPayload())
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `duplicate segments are forwarded once`() {
        val serverReceived = StringBuilder()
        val done = CountDownLatch(1)
        executor.execute {
            server.accept().use { s ->
                val input = s.getInputStream()
                var c = input.read()
                while (c >= 0) {
                    serverReceived.append(c.toChar())
                    c = input.read()
                }
                done.countDown()
            }
        }
        val app = FakeApp()
        val session = newSession(app, app.syn())
        session.start()
        app.handshake(session, 65535)
        val payload = "abc".toByteArray()
        repeat(3) {
            session.onSegment(app.segment(Packets.TCP_ACK, app.seq, app.serverIsn + 1, 65535, payload))
        }
        // Retransmission overlapping already received data plus new bytes
        session.onSegment(app.segment(Packets.TCP_ACK, app.seq + 1, app.serverIsn + 1, 65535, "bcdef".toByteArray()))
        app.seq += 6
        session.onSegment(app.segment(Packets.TCP_FIN or Packets.TCP_ACK, app.seq, app.serverIsn + 1, 65535))
        assertTrue(done.await(3, TimeUnit.SECONDS))
        assertEquals("abcdef", serverReceived.toString())
    }

    @Test
    fun `proxy failure resets the app connection`() {
        val app = FakeApp()
        val session = newSession(app, app.syn(), connector = { _, _ -> throw IOException("tunnel down") })
        session.start()
        val rst = app.next()
        assertTrue(rst.isRst)
        assertEquals(app.seq + 1, rst.ack)
    }

    @Test
    fun `zero window triggers probes`() {
        var now = 1_000_000L
        val accepted = CountDownLatch(1)
        executor.execute {
            server.accept().use {
                accepted.countDown()
                Thread.sleep(3000)
            }
        }
        val app = FakeApp()
        val session = newSession(app, app.syn(), clock = { now })
        session.start()
        app.handshake(session, window = 0)
        assertTrue(accepted.await(3, TimeUnit.SECONDS))
        now += 1500
        session.tick(now)
        val probe = app.next()
        assertNotNull(probe)
        assertEquals(app.serverIsn, probe.seq) // SND.NXT - 1
    }
}
