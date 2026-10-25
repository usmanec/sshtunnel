package com.sshtunnel.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sshtunnel.data.model.RoutingPolicy
import com.sshtunnel.vpn.net.Packets

/** SharedPreferences keys and defaults shared by the UI and the VPN service. */
object VpnPrefs {
    const val FILE = "vpn_settings"

    const val ROUTING_POLICY = "routing_policy"
    const val AUTO_RECONNECT = "auto_reconnect"
    const val AUTO_START_ON_BOOT = "auto_start_on_boot"
    const val SMART_BATTERY_SAVER = "smart_battery_saver"
    const val SCREEN_OFF_THROTTLING = "screen_off_throttling"
    const val AGGRESSIVE_BATTERY_SAVER = "aggressive_battery_saver"
    const val IDLE_TIMEOUT_SECONDS = "idle_timeout_seconds"
    const val DNS_SERVER = "dns_server"
    const val BYPASS_LAN = "bypass_lan"

    const val DEFAULT_DNS = "1.1.1.1"
    const val DEFAULT_IDLE_TIMEOUT = 60

    /** Public resolvers offered as one-click choices (easy to pick with a TV remote). */
    val DNS_PRESETS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "77.88.8.8")

    fun get(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun routingPolicy(prefs: SharedPreferences): RoutingPolicy {
        val name = prefs.getString(ROUTING_POLICY, null) ?: return RoutingPolicy.ROUTE_ONLY_SELECTED
        return runCatching { RoutingPolicy.valueOf(name) }.getOrDefault(RoutingPolicy.ROUTE_ONLY_SELECTED)
    }

    /** Configured DNS server, falling back to the default when the stored value is not an IPv4 literal. */
    fun dnsServer(prefs: SharedPreferences): String {
        val value = prefs.getString(DNS_SERVER, DEFAULT_DNS)?.trim().orEmpty()
        return if (isValidDns(value)) value else DEFAULT_DNS
    }

    fun isValidDns(value: String): Boolean {
        val ip = Packets.parseIpv4(value) ?: return false
        val first = ip ushr 24
        // 0.x, loopback and multicast/reserved cannot be used as a resolver
        return first != 0 && first != 127 && first < 224
    }
}
