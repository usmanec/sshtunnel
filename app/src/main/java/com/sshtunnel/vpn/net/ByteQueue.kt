package com.sshtunnel.vpn.net

/**
 * FIFO of byte chunks with random-access reads. Used as the TCP retransmission buffer
 * (data sent to the app but not yet acknowledged). Not thread-safe.
 */
class ByteQueue {
    private val chunks = ArrayDeque<ByteArray>()
    private var headOffset = 0

    var size: Int = 0
        private set

    fun isEmpty() = size == 0

    /** Appends [length] bytes. The array is copied. */
    fun append(data: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        chunks.addLast(data.copyOfRange(offset, offset + length))
        size += length
    }

    /** Drops [count] bytes from the head. */
    fun drop(count: Int) {
        var remaining = count.coerceAtMost(size)
        size -= remaining
        while (remaining > 0) {
            val head = chunks.first()
            val available = head.size - headOffset
            if (remaining < available) {
                headOffset += remaining
                return
            }
            remaining -= available
            chunks.removeFirst()
            headOffset = 0
        }
    }

    /** Copies [length] bytes starting at [position] (relative to the head) into [dest]. */
    fun copy(position: Int, dest: ByteArray, destOffset: Int, length: Int) {
        require(position >= 0 && length >= 0 && position + length <= size)
        var skip = position + headOffset
        var written = 0
        for (chunk in chunks) {
            if (skip >= chunk.size) {
                skip -= chunk.size
                continue
            }
            val n = minOf(chunk.size - skip, length - written)
            System.arraycopy(chunk, skip, dest, destOffset + written, n)
            written += n
            skip = 0
            if (written == length) return
        }
    }

    fun clear() {
        chunks.clear()
        headOffset = 0
        size = 0
    }
}
