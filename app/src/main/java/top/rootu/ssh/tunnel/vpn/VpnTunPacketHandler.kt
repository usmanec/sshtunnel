package top.rootu.ssh.tunnel.vpn

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import top.rootu.ssh.tunnel.vpn.net.DnsCache
import top.rootu.ssh.tunnel.vpn.net.DnsMessages
import top.rootu.ssh.tunnel.vpn.net.FlowKey
import top.rootu.ssh.tunnel.vpn.net.Packets
import top.rootu.ssh.tunnel.vpn.net.Socks5Client
import top.rootu.ssh.tunnel.vpn.net.TcpProxySession
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Routes IPv4 packets read from the TUN interface into the local SOCKS5 proxy (which forwards
 * everything over SSH):
 *  - TCP: terminated by [TcpProxySession] and relayed via SOCKS5 CONNECT;
 *  - DNS (UDP port 53 to any server): sent as DNS-over-TCP through SOCKS5, answered from the
 *    original server address, with a small cache;
 *  - any other UDP (QUIC, NTP, ...): answered with ICMP port unreachable so apps fall back to
 *    TCP right away. Nothing is sent around the tunnel, so nothing leaks.
 * IPv6 is not routed into the TUN at all (Android then blocks it for the VPN apps).
 */
class VpnTunPacketHandler(
    private val vpnInterface: ParcelFileDescriptor,
    private val socksPort: Int,
    private val onLog: (String, Boolean) -> Unit
) {
    companion object {
        private const val MAX_PACKET = 65535
        private const val MAX_TCP_SESSIONS = 2048
        private const val MAX_PARALLEL_DNS = 32
        private const val DNS_TIMEOUT_MS = 10_000
        private const val TICK_MS = 200L
    }

    private val running = AtomicBoolean(false)
    private val sessions = ConcurrentHashMap<FlowKey, TcpProxySession>()
    private val dnsCache = DnsCache()
    private val dnsPermits = Semaphore(MAX_PARALLEL_DNS)
    private val lastActivity = AtomicLong(System.currentTimeMillis())

    private var executor: ExecutorService? = null
    private var timer: ScheduledExecutorService? = null
    private var readerThread: Thread? = null
    private val tunOut = FileOutputStream(vpnInterface.fileDescriptor)

    val activeSessions: Int get() = sessions.size

    /** Time of the last packet from or to an app, for idle detection. */
    val lastActivityTime: Long get() = lastActivity.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        executor = Executors.newCachedThreadPool(daemonThreadFactory("tun-io"))
        timer = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("tun-timer")).apply {
            scheduleWithFixedDelay({ tick() }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
        }
        readerThread = Thread(null, { readLoop() }, "tun-reader", 256 * 1024).apply {
            isDaemon = true
            start()
        }
        onLog("TUN router started: TCP and DNS -> SOCKS5 127.0.0.1:$socksPort -> SSH", false)
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        for (session in sessions.values) session.abort()
        sessions.clear()
        dnsCache.clear()
        timer?.shutdownNow()
        timer = null
        executor?.shutdownNow()
        executor = null
        readerThread?.interrupt()
        readerThread = null
    }

    private fun readLoop() {
        val fd = vpnInterface.fileDescriptor
        val input = FileInputStream(fd)
        val buffer = ByteArray(MAX_PACKET)
        // The TUN descriptor is non-blocking: wait in poll() instead of spinning on empty reads
        val pollFds = arrayOf(StructPollfd().apply {
            this.fd = fd
            events = OsConstants.POLLIN.toShort()
        })
        while (running.get()) {
            try {
                val length = input.read(buffer)
                when {
                    length > 0 -> {
                        lastActivity.set(System.currentTimeMillis())
                        try {
                            handlePacket(buffer, length)
                        } catch (e: Exception) {
                            // A malformed packet must not stop the router
                        }
                    }
                    length < 0 -> break
                    else -> {
                        pollFds[0].revents = 0
                        Os.poll(pollFds, 1000)
                    }
                }
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.EINTR) break
            } catch (e: IOException) {
                break
            }
        }
        if (running.get()) onLog("TUN interface closed, packet router stopped", false)
    }

    private fun writePacket(packet: ByteArray) {
        try {
            synchronized(tunOut) { tunOut.write(packet) }
            lastActivity.set(System.currentTimeMillis())
        } catch (e: IOException) {
            // Interface is gone or the queue is full; TCP retransmits, DNS clients retry
        }
    }

    private fun handlePacket(data: ByteArray, length: Int) {
        if (Packets.ipv4HeaderLength(data, length) < 0) return
        when (Packets.ipv4Protocol(data)) {
            Packets.PROTO_TCP -> Packets.parseTcp(data, length)?.let { handleTcp(it) }
            Packets.PROTO_UDP -> Packets.parseUdp(data, length)?.let { handleUdp(it, data, length) }
            // ICMP and other protocols cannot be carried by SOCKS5 CONNECT: dropped
        }
    }

    private fun handleTcp(seg: Packets.TcpSegment) {
        val key = FlowKey(seg.srcIp, seg.srcPort, seg.dstIp, seg.dstPort)
        val existing = sessions[key]

        if (seg.isSyn && !seg.isAck) {
            if (existing != null) {
                if (existing.isSameSyn(seg)) {
                    existing.onSynRetransmit()
                    return
                }
                existing.abort()
            }
            val pool = executor ?: return
            if (sessions.size >= MAX_TCP_SESSIONS) {
                sendReset(seg)
                return
            }
            val session = TcpProxySession(
                key = key,
                syn = seg,
                output = ::writePacket,
                connector = { ip, port -> Socks5Client.connect(socksPort, ip, port) },
                executor = pool,
                onClosed = { sessions.remove(it.key, it) }
            )
            sessions[key] = session
            session.start()
            return
        }

        if (existing != null) {
            existing.onSegment(seg)
        } else if (!seg.isRst) {
            // Unknown connection (e.g. opened before a tunnel restart): reset it so the app reconnects
            sendReset(seg)
        }
    }

    private fun TcpProxySession.isSameSyn(seg: Packets.TcpSegment) = synSeq == seg.seq

    private fun sendReset(seg: Packets.TcpSegment) {
        val packet = if (seg.isAck) {
            Packets.buildTcp(
                srcIp = seg.dstIp, dstIp = seg.srcIp, srcPort = seg.dstPort, dstPort = seg.srcPort,
                seq = seg.ack, ack = 0, flags = Packets.TCP_RST, window = 0
            )
        } else {
            val segLength = seg.payloadLength + (if (seg.isSyn) 1 else 0) + (if (seg.isFin) 1 else 0)
            Packets.buildTcp(
                srcIp = seg.dstIp, dstIp = seg.srcIp, srcPort = seg.dstPort, dstPort = seg.srcPort,
                seq = 0, ack = seg.seq + segLength, flags = Packets.TCP_RST or Packets.TCP_ACK, window = 0
            )
        }
        writePacket(packet)
    }

    private fun handleUdp(udp: Packets.UdpDatagram, raw: ByteArray, length: Int) {
        if (udp.dstPort != 53) {
            Packets.buildIcmpPortUnreachable(raw, length)?.let { writePacket(it) }
            return
        }
        if (udp.payloadLength < 12) return
        val query = udp.copyPayload()

        dnsCache.lookup(query)?.let { cached ->
            sendDnsResponse(udp, query, cached)
            return
        }
        val pool = executor ?: return
        // Too many lookups in flight: drop, the resolver retries
        if (!dnsPermits.tryAcquire()) return
        try {
            pool.execute {
                try {
                    resolveOverSocks(udp, query)
                } finally {
                    dnsPermits.release()
                }
            }
        } catch (e: Exception) {
            dnsPermits.release()
        }
    }

    private fun resolveOverSocks(udp: Packets.UdpDatagram, query: ByteArray) {
        try {
            Socks5Client.connect(socksPort, udp.dstIp, 53, DNS_TIMEOUT_MS).use { socket ->
                socket.soTimeout = DNS_TIMEOUT_MS
                // RFC 7766 DNS over TCP: 2-byte length prefix
                val request = ByteArray(2 + query.size)
                Packets.put16(request, 0, query.size)
                System.arraycopy(query, 0, request, 2, query.size)
                socket.getOutputStream().write(request)
                val input = DataInputStream(socket.getInputStream())
                val responseLength = input.readUnsignedShort()
                if (responseLength < 12) return
                val response = ByteArray(responseLength)
                input.readFully(response)
                if (DnsMessages.id(response) != DnsMessages.id(query)) return
                dnsCache.store(query, response)
                sendDnsResponse(udp, query, response)
            }
        } catch (e: Exception) {
            // Timeout or tunnel down: the resolver retries or fails over
        }
    }

    private fun sendDnsResponse(udp: Packets.UdpDatagram, query: ByteArray, response: ByteArray) {
        val payload = if (response.size > DnsMessages.MAX_UDP_PAYLOAD_NO_EDNS && !DnsMessages.hasEdns(query)) {
            DnsMessages.truncated(response) ?: return
        } else {
            response
        }
        writePacket(Packets.buildUdp(udp.dstIp, udp.srcIp, 53, udp.srcPort, payload))
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        for (session in sessions.values) {
            try {
                session.tick(now)
            } catch (e: Exception) {
                session.abort()
            }
        }
    }

    private fun daemonThreadFactory(prefix: String): ThreadFactory {
        val counter = AtomicInteger(1)
        return ThreadFactory { runnable ->
            Thread(null, runnable, "$prefix-${counter.getAndIncrement()}", 256 * 1024).apply { isDaemon = true }
        }
    }
}
