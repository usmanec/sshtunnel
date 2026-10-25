package com.sshtunnel.vpn

import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelWindowControl
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Receive buffer of one SSH channel with per-channel flow control.
 *
 * JSch writes incoming data into [sink] on its session thread. The sink never blocks: when more
 * than [highWatermark] bytes are waiting, the channel's SSH window is held closed so the server
 * stops sending on this channel only. Once the reader drains below [lowWatermark], the window is
 * re-opened. Other channels keep flowing, so one paused stream (e.g. a video player whose buffer
 * is full) can no longer freeze the whole tunnel.
 */
class SshChannelBuffer(
    private val windowSize: Int,
    private val highWatermark: Int = windowSize / 2,
    private val lowWatermark: Int = windowSize / 8
) {
    private val lock = Object()
    private val chunks = ArrayDeque<ByteArray>()
    private var headOffset = 0
    private var buffered = 0
    private var eof = false
    private var closed = false
    private var paused = false

    @Volatile
    var channel: Channel? = null

    val sink: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            synchronized(lock) {
                if (closed) throw IOException("Channel reader closed")
                chunks.addLast(b.copyOfRange(off, off + len))
                buffered += len
                if (!paused && buffered >= highWatermark) {
                    paused = true
                    channel?.let { ChannelWindowControl.pause(it) }
                }
                lock.notifyAll()
            }
        }

        override fun close() {
            synchronized(lock) {
                eof = true
                lock.notifyAll()
            }
        }
    }

    val source: InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            var resume: Channel? = null
            val count: Int
            synchronized(lock) {
                while (chunks.isEmpty() && !eof && !closed) lock.wait()
                if (closed) throw IOException("Channel reader closed")
                if (chunks.isEmpty()) return -1
                var copied = 0
                while (copied < len && chunks.isNotEmpty()) {
                    val head = chunks.first()
                    val n = minOf(head.size - headOffset, len - copied)
                    System.arraycopy(head, headOffset, b, off + copied, n)
                    copied += n
                    headOffset += n
                    if (headOffset == head.size) {
                        chunks.removeFirst()
                        headOffset = 0
                    }
                }
                buffered -= copied
                count = copied
                if (paused && buffered <= lowWatermark) {
                    paused = false
                    resume = channel
                }
            }
            // Outside our lock: sending the window adjust takes the channel/session locks
            resume?.let { ChannelWindowControl.resume(it, windowSize) }
            return count
        }

        override fun available(): Int = synchronized(lock) { buffered }

        override fun close() {
            synchronized(lock) {
                closed = true
                chunks.clear()
                buffered = 0
                lock.notifyAll()
            }
        }
    }
}
