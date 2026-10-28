package top.rootu.ssh.tunnel.vpn

import top.rootu.ssh.tunnel.vpn.net.FakeApp
import top.rootu.ssh.tunnel.vpn.net.Packets
import top.rootu.ssh.tunnel.vpn.net.Socks5Client
import top.rootu.ssh.tunnel.vpn.net.TcpProxySession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * App TCP segments -> TcpProxySession -> SOCKS5 client -> Socks5ProxyServer -> transport.
 * The transport stands in for SSH and records which destinations were requested.
 */
class SocksTunnelEndToEndTest {
    private val executor = Executors.newCachedThreadPool()
    private val remote = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val requested = CopyOnWriteArrayList<String>()
    private val downloaded = AtomicLong()

    private var transportUp = true
    private val transport = object : TunnelTransport {
        override val isConnected get() = transportUp
        override fun openStream(host: String, port: Int): TunnelStream {
            requested += "$host:$port"
            val socket = Socket(InetAddress.getLoopbackAddress(), remote.localPort)
            return object : TunnelStream {
                override val input: InputStream = socket.getInputStream()
                override val output: OutputStream = object : OutputStream() {
                    override fun write(b: Int) = socket.getOutputStream().write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = socket.getOutputStream().write(b, off, len)
                    override fun close() = socket.shutdownOutput()
                }
                override val isClosed get() = socket.isClosed
                override fun close() = socket.close()
            }
        }
    }

    private val socks = Socks5ProxyServer(
        // 0 = any free port, as for profiles without a fixed SOCKS port
        port = 0,
        transportProvider = { transport },
        onBytesTransferred = { _, down -> downloaded.addAndGet(down) },
        onConnectionChange = {},
        onActivity = {},
        onLog = { _, _ -> }
    ).apply { start() }

    @After
    fun tearDown() {
        socks.stop()
        remote.close()
        executor.shutdownNow()
    }

    private fun session(app: FakeApp) = TcpProxySession(
        key = app.key,
        syn = app.syn(windowScale = 8),
        output = { app.output.add(it) },
        connector = { ip, port -> Socks5Client.connect(socks.boundPort, ip, port) },
        executor = executor,
        onClosed = {}
    )

    @Test
    fun `app connection is carried through socks to the tunnel`() {
        executor.execute {
            remote.accept().use { s ->
                val request = ByteArray(4)
                var n = 0
                while (n < 4) n += s.getInputStream().read(request, n, 4 - n)
                s.getOutputStream().write(("echo:" + String(request)).toByteArray())
                s.shutdownOutput()
                while (s.getInputStream().read() >= 0) Unit
            }
        }
        val app = FakeApp(serverIp = Packets.parseIpv4("203.0.113.7")!!, serverPort = 443)
        val session = session(app)
        session.start()
        app.handshake(session, window = 1024)
        session.onSegment(app.segment(Packets.TCP_ACK or Packets.TCP_PSH, app.seq, app.serverIsn + 1, 1024, "ping".toByteArray()))
        app.seq += 4
        app.receive(session, bytes = 100, window = 1024)

        assertEquals("echo:ping", app.received.toString())
        assertTrue(app.finReceived)
        // The original destination was requested from the tunnel, nothing went around it
        assertEquals(listOf("203.0.113.7:443"), requested)
        assertEquals(9L, downloaded.get())
    }

    @Test
    fun `tunnel down means immediate reset`() {
        transportUp = false
        val app = FakeApp()
        val session = session(app)
        session.start()
        val rst = app.next()
        assertTrue(rst.isRst)
        assertTrue(requested.isEmpty())
    }
}
