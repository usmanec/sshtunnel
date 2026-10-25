package com.sshtunnel.vpn.net

/** IPv4 prefix. [address] is big-endian, host bits are zero. */
data class Cidr(val address: Int, val prefixLength: Int) {
    init {
        require(prefixLength in 0..32)
    }

    val start: Long get() = address.toLong() and 0xFFFFFFFFL
    val size: Long get() = 1L shl (32 - prefixLength)
    val endInclusive: Long get() = start + size - 1

    override fun toString() = "${Packets.ipToString(address)}/$prefixLength"

    companion object {
        fun parse(text: String): Cidr {
            val (ip, prefix) = text.split('/')
            val address = requireNotNull(Packets.parseIpv4(ip)) { "Bad IPv4 address: $ip" }
            val length = prefix.toInt()
            val mask = if (length == 0) 0 else (-1 shl (32 - length))
            return Cidr(address and mask, length)
        }

        /** Local networks that should stay reachable directly when "Bypass LAN" is on. */
        val PRIVATE_NETWORKS: List<Cidr> = listOf(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "169.254.0.0/16",
            "224.0.0.0/4", // multicast (SSDP/DLNA, mDNS)
            "255.255.255.255/32"
        ).map(::parse)

        /**
         * Minimal set of prefixes covering 0.0.0.0/0 minus [excluded]
         * (VpnService.Builder.excludeRoute only exists on API 33+).
         */
        fun complement(excluded: List<Cidr>): List<Cidr> {
            val result = ArrayList<Cidr>()
            fun split(block: Cidr) {
                if (excluded.none { overlaps(block, it) }) {
                    result += block
                    return
                }
                if (excluded.any { contains(it, block) }) return
                val childLength = block.prefixLength + 1
                val half = 1L shl (32 - childLength)
                split(Cidr(block.address, childLength))
                split(Cidr((block.start + half).toInt(), childLength))
            }
            split(Cidr(0, 0))
            return result
        }

        private fun overlaps(a: Cidr, b: Cidr) = a.start <= b.endInclusive && b.start <= a.endInclusive

        private fun contains(outer: Cidr, inner: Cidr) =
            outer.start <= inner.start && inner.endInclusive <= outer.endInclusive
    }
}
