package top.rootu.ssh.tunnel.vpn.net

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.Executor
import kotlin.random.Random

data class FlowKey(val srcIp: Int, val srcPort: Int, val dstIp: Int, val dstPort: Int)

/**
 * Userspace TCP endpoint that terminates one app connection coming from the TUN interface and
 * relays its byte stream through a proxied [Socket] (SOCKS5 -> SSH).
 *
 * Implemented parts of TCP that matter on a local virtual link:
 *  - in-order delivery with duplicate/out-of-order filtering in both directions;
 *  - flow control: the app's receive window (incl. window scaling) is respected and our own
 *    receive window reflects how much upload data is still queued for the proxy socket;
 *  - retransmission with exponential back-off and zero-window probes;
 *  - half-close in both directions and RST on proxy failures, so apps fail fast instead of
 *    hanging until their connect timeout.
 *
 * All state is guarded by `this`; blocking socket I/O happens outside the lock on executor threads.
 */
class TcpProxySession(
    val key: FlowKey,
    syn: Packets.TcpSegment,
    private val output: (ByteArray) -> Unit,
    private val connector: (dstIp: Int, dstPort: Int) -> Socket,
    private val executor: Executor,
    private val onClosed: (TcpProxySession) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        /** Our receive window. We advertise window scale 0, so it cannot exceed 65535. */
        const val RECEIVE_WINDOW = 65535
        /** Upper bound for unacknowledged data sent to the app. */
        const val MAX_IN_FLIGHT = 256 * 1024
        const val OUR_MSS = 1460
        private const val READ_CHUNK = 32 * 1024
        private const val RTO_MIN_MS = 400L
        private const val RTO_MAX_MS = 6_000L
        private const val MAX_RETRANSMITS = 10
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val CLOSING_TIMEOUT_MS = 60_000L
    }

    private enum class State { CONNECTING, ESTABLISHED, CLOSED }

    private val clientIsn = syn.seq
    private val sendMss = (if (syn.mss > 0) syn.mss else 536).coerceIn(256, OUR_MSS)
    private val windowScaleEnabled = syn.windowScale >= 0
    private val clientWindowShift = if (windowScaleEnabled) syn.windowScale else 0

    private var state = State.CONNECTING
    private val createdAt = clock()
    private var closingSince = 0L

    // Receive side (app -> proxy)
    private var rcvNext = clientIsn + 1
    private val uploadQueue = ArrayDeque<ByteArray>()
    private var uploadQueued = 0
    private var uploadWriterActive = false
    private var clientFinReceived = false
    private var uploadShutdown = false

    // Send side (proxy -> app)
    private val ourIsn = Random.nextInt()
    private var sndUna = ourIsn
    private var sndNext = ourIsn
    private var synAcked = false
    private val unacked = ByteQueue()
    // The window in a SYN is never scaled
    private var clientWindow = syn.window.toLong()
    private var remoteEof = false
    private var finSent = false
    private var finAcked = false

    // Retransmission
    private var rto = RTO_MIN_MS
    private var retransmitAt = 0L
    private var retransmits = 0
    private var lastProbeAt = 0L

    private var socket: Socket? = null
    private var socketOut: OutputStream? = null

    /** Initial sequence number of the app's SYN, to recognize SYN retransmissions. */
    val synSeq: Int get() = clientIsn

    @Volatile
    var lastActivity: Long = createdAt
        private set

    fun start() {
        executor.execute { connectAndPump() }
    }

    private fun inFlight(): Int = sndNext - sndUna

    private fun advertisedWindow(): Int = (RECEIVE_WINDOW - uploadQueued).coerceIn(0, RECEIVE_WINDOW)

    private fun sendLimit(): Long = minOf(clientWindow, MAX_IN_FLIGHT.toLong())

    // ---------------------------------------------------------------------------------------
    // Proxy side: connect, then pump proxy -> app until EOF
    // ---------------------------------------------------------------------------------------

    private fun connectAndPump() {
        val sock = try {
            connector(key.dstIp, key.dstPort)
        } catch (e: Exception) {
            synchronized(this) { abortLocked() }
            return
        }
        val input: InputStream = synchronized(this) {
            if (state == State.CLOSED) {
                runCatching { sock.close() }
                return
            }
            socket = sock
            socketOut = sock.getOutputStream()
            state = State.ESTABLISHED
            sndNext = ourIsn + 1
            // Arm the timer before the packet leaves, so the deadline never lags behind it
            armRetransmitLocked()
            sendSynAckLocked()
            sock.getInputStream()
        }

        val buffer = ByteArray(READ_CHUNK)
        try {
            while (true) {
                val allowance = synchronized(this) {
                    while (state != State.CLOSED && inFlight() >= sendLimit()) {
                        (this as Object).wait(500)
                    }
                    if (state == State.CLOSED) return
                    (sendLimit() - inFlight()).toInt().coerceIn(1, READ_CHUNK)
                }
                val n = input.read(buffer, 0, allowance)
                synchronized(this) {
                    if (state == State.CLOSED) return
                    if (n < 0) {
                        remoteEof = true
                        sendFinLocked()
                        return
                    }
                    if (n > 0) {
                        lastActivity = clock()
                        val startSeq = sndNext
                        unacked.append(buffer, 0, n)
                        sndNext += n
                        if (retransmitAt == 0L) armRetransmitLocked()
                        sendRangeLocked(startSeq, buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            synchronized(this) { abortLocked() }
        }
    }

    // ---------------------------------------------------------------------------------------
    // App side: segments from the TUN reader thread
    // ---------------------------------------------------------------------------------------

    /** A repeated SYN for this flow (our SYN-ACK was lost or is not sent yet). */
    @Synchronized
    fun onSynRetransmit() {
        if (state == State.ESTABLISHED && !synAcked) sendSynAckLocked()
    }

    @Synchronized
    fun onSegment(seg: Packets.TcpSegment) {
        if (state == State.CLOSED) return
        lastActivity = clock()
        if (seg.isRst) {
            closeLocked()
            return
        }
        if (state == State.CONNECTING) return // the app must wait for our SYN-ACK

        if (seg.isAck) processAckLocked(seg.ack, seg.window)
        if (state == State.CLOSED) return

        val len = seg.payloadLength
        var needAck = false
        if (len > 0) {
            needAck = true
            val diff = seg.seq - rcvNext
            if (diff <= 0 && !clientFinReceived) {
                val skip = -diff
                if (skip < len) {
                    val accept = minOf(len - skip, advertisedWindow())
                    if (accept > 0) {
                        val start = seg.payloadOffset + skip
                        uploadQueue.addLast(seg.data.copyOfRange(start, start + accept))
                        uploadQueued += accept
                        rcvNext += accept
                        scheduleUploadLocked()
                    }
                }
            }
            // Out-of-order, duplicate or partially accepted data: the ACK tells the app what we have
        }

        if (seg.isFin) {
            needAck = true
            if (!clientFinReceived && seg.seq + len == rcvNext) {
                clientFinReceived = true
                rcvNext += 1
                if (closingSince == 0L) closingSince = clock()
                scheduleUploadLocked()
            }
        }

        if (needAck) sendAckLocked()
        checkFullyClosedLocked()
    }

    private fun processAckLocked(ack: Int, rawWindow: Int) {
        val acked = ack - sndUna
        if (acked > 0 && ack - sndNext <= 0) {
            var remaining = acked
            if (!synAcked) {
                synAcked = true
                remaining -= 1
            }
            val data = minOf(remaining, unacked.size)
            unacked.drop(data)
            remaining -= data
            if (remaining > 0 && finSent) finAcked = true
            sndUna = ack
            retransmits = 0
            rto = RTO_MIN_MS
            retransmitAt = 0L
            if (inFlight() > 0) armRetransmitLocked()
        }
        if (synAcked) {
            val newWindow = rawWindow.toLong() shl clientWindowShift
            clientWindow = newWindow
        }
        (this as Object).notifyAll()
    }

    // ---------------------------------------------------------------------------------------
    // Upload: app -> proxy socket, drained by an executor task only while data is pending
    // ---------------------------------------------------------------------------------------

    private fun scheduleUploadLocked() {
        if (uploadWriterActive || socketOut == null) return
        if (uploadQueue.isEmpty() && !(clientFinReceived && !uploadShutdown)) return
        uploadWriterActive = true
        executor.execute { drainUpload() }
    }

    private fun drainUpload() {
        try {
            while (true) {
                val step = synchronized(this) { nextUploadStepLocked() } ?: return
                val chunk = step.chunk
                if (chunk != null) {
                    step.out.write(chunk)
                    // Window update so an app stalled on our full window continues sending
                    if (step.windowWasClosed) synchronized(this) { if (state != State.CLOSED) sendAckLocked() }
                } else {
                    socket?.shutdownOutput()
                    synchronized(this) { checkFullyClosedLocked() }
                }
            }
        } catch (e: Exception) {
            synchronized(this) {
                uploadWriterActive = false
                abortLocked()
            }
        }
    }

    /** A chunk to write, or a half-close when [chunk] is null. */
    private class UploadStep(val out: OutputStream, val chunk: ByteArray?, val windowWasClosed: Boolean)

    private fun nextUploadStepLocked(): UploadStep? {
        val out = socketOut
        if (state == State.CLOSED || out == null) {
            uploadWriterActive = false
            return null
        }
        if (uploadQueue.isNotEmpty()) {
            val windowWasClosed = advertisedWindow() < sendMss
            val chunk = uploadQueue.removeFirst()
            uploadQueued -= chunk.size
            return UploadStep(out, chunk, windowWasClosed)
        }
        if (clientFinReceived && !uploadShutdown) {
            uploadShutdown = true
            return UploadStep(out, null, false)
        }
        uploadWriterActive = false
        return null
    }

    // ---------------------------------------------------------------------------------------
    // Timers
    // ---------------------------------------------------------------------------------------

    @Synchronized
    fun tick(now: Long) {
        when (state) {
            State.CLOSED -> return
            State.CONNECTING -> {
                if (now - createdAt > CONNECT_TIMEOUT_MS) abortLocked()
                return
            }
            State.ESTABLISHED -> Unit
        }

        // Half-closed and silent for too long: the app or the server forgot to close its side
        if (closingSince != 0L && now - lastActivity > CLOSING_TIMEOUT_MS) {
            abortLocked()
            return
        }

        if (inFlight() > 0 && retransmitAt != 0L && now >= retransmitAt) {
            if (++retransmits > MAX_RETRANSMITS) {
                abortLocked()
                return
            }
            retransmitLocked()
            rto = (rto * 2).coerceAtMost(RTO_MAX_MS)
            retransmitAt = now + rto
        } else if (inFlight() == 0 && clientWindow == 0L && !remoteEof && now - lastProbeAt >= 1000) {
            // Zero window probe: an ACK with an old sequence number makes the app re-announce its window
            lastProbeAt = now
            send(sndNext - 1, rcvNext, Packets.TCP_ACK)
        }
    }

    private fun retransmitLocked() {
        if (!synAcked) {
            sendSynAckLocked()
            return
        }
        val window = maxOf(clientWindow, sendMss.toLong()).coerceAtMost(MAX_IN_FLIGHT.toLong()).toInt()
        val length = minOf(unacked.size, window)
        if (length > 0) {
            val data = ByteArray(length)
            unacked.copy(0, data, 0, length)
            sendRangeLocked(sndUna, data, 0, length)
        }
        if (finSent && !finAcked && length == unacked.size) {
            send(sndNext - 1, rcvNext, Packets.TCP_FIN or Packets.TCP_ACK)
        }
    }

    private fun armRetransmitLocked() {
        retransmitAt = clock() + rto
    }

    // ---------------------------------------------------------------------------------------
    // Packet output
    // ---------------------------------------------------------------------------------------

    private fun sendSynAckLocked() {
        val options = if (windowScaleEnabled) {
            byteArrayOf(2, 4, (OUR_MSS ushr 8).toByte(), OUR_MSS.toByte(), 1, 3, 3, 0)
        } else {
            byteArrayOf(2, 4, (OUR_MSS ushr 8).toByte(), OUR_MSS.toByte())
        }
        output(
            Packets.buildTcp(
                srcIp = key.dstIp, dstIp = key.srcIp, srcPort = key.dstPort, dstPort = key.srcPort,
                seq = ourIsn, ack = rcvNext, flags = Packets.TCP_SYN or Packets.TCP_ACK,
                window = advertisedWindow(), options = options
            )
        )
    }

    private fun sendRangeLocked(startSeq: Int, data: ByteArray, offset: Int, length: Int) {
        var pos = 0
        while (pos < length) {
            val n = minOf(sendMss, length - pos)
            val last = pos + n == length
            val flags = Packets.TCP_ACK or (if (last) Packets.TCP_PSH else 0)
            output(
                Packets.buildTcp(
                    srcIp = key.dstIp, dstIp = key.srcIp, srcPort = key.dstPort, dstPort = key.srcPort,
                    seq = startSeq + pos, ack = rcvNext, flags = flags, window = advertisedWindow(),
                    payload = data, payloadOffset = offset + pos, payloadLength = n
                )
            )
            pos += n
        }
    }

    private fun sendFinLocked() {
        if (finSent) return
        finSent = true
        if (closingSince == 0L) closingSince = clock()
        sndNext += 1
        if (retransmitAt == 0L) armRetransmitLocked()
        send(sndNext - 1, rcvNext, Packets.TCP_FIN or Packets.TCP_ACK)
    }

    private fun sendAckLocked() = send(sndNext, rcvNext, Packets.TCP_ACK)

    private fun send(seq: Int, ack: Int, flags: Int) {
        output(
            Packets.buildTcp(
                srcIp = key.dstIp, dstIp = key.srcIp, srcPort = key.dstPort, dstPort = key.srcPort,
                seq = seq, ack = ack, flags = flags, window = advertisedWindow()
            )
        )
    }

    // ---------------------------------------------------------------------------------------
    // Teardown
    // ---------------------------------------------------------------------------------------

    private fun checkFullyClosedLocked() {
        if (clientFinReceived && uploadShutdown && finSent && finAcked) closeLocked()
    }

    @Synchronized
    fun abort() = abortLocked()

    private fun abortLocked() {
        if (state == State.CLOSED) return
        send(sndNext, rcvNext, Packets.TCP_RST or Packets.TCP_ACK)
        closeLocked()
    }

    @Synchronized
    fun close() = closeLocked()

    private fun closeLocked() {
        if (state == State.CLOSED) return
        state = State.CLOSED
        unacked.clear()
        uploadQueue.clear()
        uploadQueued = 0
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        (this as Object).notifyAll()
        onClosed(this)
    }
}
