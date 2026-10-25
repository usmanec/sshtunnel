package com.sshtunnel.vpn.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CidrTest {
    @Test
    fun `complement of private networks covers everything else exactly`() {
        val excluded = Cidr.PRIVATE_NETWORKS
        val routes = Cidr.complement(excluded)
        val excludedSize = excluded.sumOf { it.size }
        assertEquals((1L shl 32) - excludedSize, routes.sumOf { it.size })
        for (route in routes) {
            for (ex in excluded) {
                assertFalse("$route overlaps $ex", route.start <= ex.endInclusive && ex.start <= route.endInclusive)
            }
        }
        // Public resolvers and typical hosts are routed into the tunnel
        for (ip in listOf("1.1.1.1", "8.8.8.8", "100.64.1.1", "172.32.0.1", "11.0.0.1")) {
            val addr = Packets.parseIpv4(ip)!!.toLong() and 0xFFFFFFFFL
            assertTrue(ip, routes.any { addr in it.start..it.endInclusive })
        }
        assertTrue("Route count stays reasonable: ${routes.size}", routes.size < 100)
    }

    @Test
    fun `parse normalizes host bits`() {
        assertEquals("192.168.0.0/16", Cidr.parse("192.168.10.1/16").toString())
        assertEquals(listOf(Cidr(0, 0)), Cidr.complement(emptyList()))
    }
}
