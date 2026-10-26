package top.rootu.ssh.tunnel.vpn.net

import java.nio.ByteBuffer

/**
 * Small helpers for DNS wire-format messages (RFC 1035) needed by the DNS forwarder and cache.
 */
object DnsMessages {
    private const val HEADER_LEN = 12
    private const val TYPE_OPT = 41
    const val MAX_UDP_PAYLOAD_NO_EDNS = 512

    fun id(msg: ByteArray): Int = Packets.u16(msg, 0)
    fun rcode(msg: ByteArray): Int = msg[3].toInt() and 0x0F
    fun isResponse(msg: ByteArray): Boolean = (msg[2].toInt() and 0x80) != 0
    private fun qdCount(msg: ByteArray) = Packets.u16(msg, 4)
    private fun anCount(msg: ByteArray) = Packets.u16(msg, 6)
    private fun nsCount(msg: ByteArray) = Packets.u16(msg, 8)
    private fun arCount(msg: ByteArray) = Packets.u16(msg, 10)

    /** Returns the offset after a (possibly compressed) domain name, or -1 when malformed. */
    private fun skipName(msg: ByteArray, start: Int, length: Int): Int {
        var i = start
        var labels = 0
        while (i < length) {
            val len = msg[i].toInt() and 0xFF
            when {
                len == 0 -> return i + 1
                (len and 0xC0) == 0xC0 -> return if (i + 1 < length) i + 2 else -1
                (len and 0xC0) != 0 -> return -1
                else -> i += len + 1
            }
            if (++labels > 128) return -1
        }
        return -1
    }

    /** Offset right after the question section, or -1 when the message is malformed. */
    fun questionEnd(msg: ByteArray, length: Int = msg.size): Int {
        if (length < HEADER_LEN) return -1
        var offset = HEADER_LEN
        repeat(qdCount(msg)) {
            offset = skipName(msg, offset, length)
            if (offset < 0 || offset + 4 > length) return -1
            offset += 4
        }
        return offset
    }

    /** A query is considered EDNS-capable when it carries an additional (OPT) record. */
    fun hasEdns(query: ByteArray): Boolean = query.size >= HEADER_LEN && arCount(query) > 0

    /**
     * Cache key: everything from the flags to the end of the question (the ID is excluded,
     * the EDNS presence is included since it changes the allowed response size).
     */
    fun cacheKey(query: ByteArray): ByteBuffer? {
        val end = questionEnd(query)
        if (end < 0 || qdCount(query) != 1 || isResponse(query)) return null
        val key = ByteArray(end - 2 + 1)
        System.arraycopy(query, 2, key, 0, end - 2)
        key[key.size - 1] = if (hasEdns(query)) 1 else 0
        // Domain names are case-insensitive
        for (i in 10 until key.size - 5) {
            val c = key[i]
            if (c in 'A'.code.toByte()..'Z'.code.toByte()) key[i] = (c + 32).toByte()
        }
        return ByteBuffer.wrap(key)
    }

    /**
     * Calls [action] with the offset of every resource record TTL in the answer and authority
     * sections (OPT pseudo-records are skipped). Returns false when the message is malformed.
     */
    private fun forEachTtl(msg: ByteArray, length: Int, action: (Int) -> Unit): Boolean {
        var offset = questionEnd(msg, length)
        if (offset < 0) return false
        val records = anCount(msg) + nsCount(msg)
        repeat(records) {
            offset = skipName(msg, offset, length)
            if (offset < 0 || offset + 10 > length) return false
            val type = Packets.u16(msg, offset)
            val rdLength = Packets.u16(msg, offset + 8)
            if (type != TYPE_OPT) action(offset + 4)
            offset += 10 + rdLength
            if (offset > length) return false
        }
        return true
    }

    /** Minimal TTL (seconds) of the answer and authority records, or null when there are none. */
    fun minTtl(msg: ByteArray): Long? {
        var min: Long? = null
        val ok = forEachTtl(msg, msg.size) { offset ->
            val ttl = Packets.i32(msg, offset).toLong() and 0xFFFFFFFFL
            min = if (min == null) ttl else minOf(min!!, ttl)
        }
        return if (ok) min else null
    }

    /** Copy of [msg] with the given ID and all TTLs decreased by [elapsedSeconds]. */
    fun forReply(msg: ByteArray, id: Int, elapsedSeconds: Long): ByteArray {
        val copy = msg.copyOf()
        Packets.put16(copy, 0, id)
        if (elapsedSeconds > 0) {
            forEachTtl(copy, copy.size) { offset ->
                val ttl = Packets.i32(copy, offset).toLong() and 0xFFFFFFFFL
                Packets.put32(copy, offset, (ttl - elapsedSeconds).coerceAtLeast(1).toInt())
            }
        }
        return copy
    }

    /**
     * Header + question with the TC flag set, so a client without EDNS retries over TCP
     * (which also goes through the tunnel).
     */
    fun truncated(response: ByteArray): ByteArray? {
        val end = questionEnd(response)
        if (end < 0) return null
        val out = response.copyOf(end)
        out[2] = (out[2].toInt() or 0x02).toByte()
        Packets.put16(out, 6, 0)
        Packets.put16(out, 8, 0)
        Packets.put16(out, 10, 0)
        return out
    }
}

/**
 * Tiny LRU cache for DNS responses keyed by question. Cuts SSH channel round trips for
 * repeated lookups, which matters on high-latency tunnels.
 */
class DnsCache(
    private val maxEntries: Int = 512,
    private val minTtlSeconds: Long = 10,
    private val maxTtlSeconds: Long = 600,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class Entry(val response: ByteArray, val storedAt: Long, val expiresAt: Long)

    private val entries = object : LinkedHashMap<ByteBuffer, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ByteBuffer, Entry>?): Boolean =
            size > maxEntries
    }

    /** Returns a ready-to-send response for [query] or null. */
    fun lookup(query: ByteArray): ByteArray? {
        val key = DnsMessages.cacheKey(query) ?: return null
        val now = clock()
        val entry = synchronized(entries) {
            val e = entries[key] ?: return null
            if (now >= e.expiresAt) {
                entries.remove(key)
                return null
            }
            e
        }
        return DnsMessages.forReply(entry.response, DnsMessages.id(query), (now - entry.storedAt) / 1000)
    }

    fun store(query: ByteArray, response: ByteArray) {
        // Only cache successful answers
        if (DnsMessages.rcode(response) != 0) return
        val ttl = DnsMessages.minTtl(response) ?: return
        val key = DnsMessages.cacheKey(query) ?: return
        val now = clock()
        val ttlMs = ttl.coerceIn(minTtlSeconds, maxTtlSeconds) * 1000
        synchronized(entries) { entries[key] = Entry(response.copyOf(), now, now + ttlMs) }
    }

    fun clear() = synchronized(entries) { entries.clear() }
}
