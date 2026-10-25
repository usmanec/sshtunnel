package com.sshtunnel.vpn.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsMessagesTest {

    private fun name(domain: String): ByteArray {
        val out = ByteArrayOutputStream()
        for (label in domain.split('.')) {
            out.write(label.length)
            out.write(label.toByteArray())
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun query(id: Int, domain: String, edns: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((id ushr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, if (edns) 1 else 0))
        out.write(name(domain))
        out.write(byteArrayOf(0, 1, 0, 1))
        if (edns) out.write(byteArrayOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0))
        return out.toByteArray()
    }

    private fun response(id: Int, domain: String, ttls: List<Int>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((id ushr 8).toByte(), id.toByte(), 0x81.toByte(), 0x80.toByte(), 0, 1, 0, ttls.size.toByte(), 0, 0, 0, 0))
        out.write(name(domain))
        out.write(byteArrayOf(0, 1, 0, 1))
        for ((i, ttl) in ttls.withIndex()) {
            out.write(byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1))
            out.write(byteArrayOf((ttl ushr 24).toByte(), (ttl ushr 16).toByte(), (ttl ushr 8).toByte(), ttl.toByte()))
            out.write(byteArrayOf(0, 4, 93.toByte(), 184.toByte(), 216.toByte(), (34 + i).toByte()))
        }
        return out.toByteArray()
    }

    @Test
    fun `cache key ignores id and case`() {
        val a = DnsMessages.cacheKey(query(1, "Example.COM"))
        val b = DnsMessages.cacheKey(query(2, "example.com"))
        assertEquals(a, b)
        assertTrue(a != DnsMessages.cacheKey(query(1, "example.com", edns = true)))
    }

    @Test
    fun `min ttl and reply rewriting`() {
        val resp = response(7, "example.com", listOf(300, 60))
        assertEquals(60L, DnsMessages.minTtl(resp))
        val reply = DnsMessages.forReply(resp, 0x1234, 10)
        assertEquals(0x1234, DnsMessages.id(reply))
        assertEquals(50L, DnsMessages.minTtl(reply))
    }

    @Test
    fun `truncated keeps only the question`() {
        val resp = response(7, "example.com", listOf(300))
        val tc = DnsMessages.truncated(resp)!!
        assertEquals(DnsMessages.questionEnd(resp), tc.size)
        assertTrue((tc[2].toInt() and 0x02) != 0)
        assertEquals(0, Packets.u16(tc, 6))
    }

    @Test
    fun `cache returns fresh copy with new id and expires`() {
        var now = 1_000_000L
        val cache = DnsCache(clock = { now })
        val q = query(1, "example.com")
        cache.store(q, response(1, "example.com", listOf(30)))
        now += 5_000
        val hit = cache.lookup(query(99, "EXAMPLE.com"))
        assertNotNull(hit)
        assertEquals(99, DnsMessages.id(hit!!))
        assertEquals(25L, DnsMessages.minTtl(hit))
        now += 30_000
        assertNull(cache.lookup(q))
    }

    @Test
    fun `failed answers are not cached`() {
        val cache = DnsCache()
        val q = query(1, "nx.example")
        val resp = response(1, "nx.example", emptyList()).also { it[3] = 0x83.toByte() } // NXDOMAIN
        cache.store(q, resp)
        assertNull(cache.lookup(q))
    }
}
