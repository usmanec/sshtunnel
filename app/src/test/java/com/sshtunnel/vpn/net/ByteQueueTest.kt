package com.sshtunnel.vpn.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteQueueTest {
    @Test
    fun `append drop and copy across chunks`() {
        val q = ByteQueue()
        q.append("hello ".toByteArray(), 0, 6)
        q.append("xxworldxx".toByteArray(), 2, 5)
        assertEquals(11, q.size)
        val out = ByteArray(5)
        q.copy(4, out, 0, 5)
        assertArrayEquals("o wor".toByteArray(), out)
        q.drop(3)
        assertEquals(8, q.size)
        val rest = ByteArray(8)
        q.copy(0, rest, 0, 8)
        assertArrayEquals("lo world".toByteArray(), rest)
        q.drop(100)
        assertEquals(0, q.size)
    }
}
