package com.sshtunnel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sshtunnel.data.model.AuthType
import com.sshtunnel.data.model.SshServerProfile
import com.sshtunnel.data.model.VpnStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SSH Tunnel", appName)
  }

  @Test
  fun `verify vpn stats byte and speed formatting`() {
    val stats = VpnStats(
      bytesIn = 1024 * 1024 * 5,
      bytesOut = 1024 * 500,
      speedInBps = 1024 * 1024 * 2,
      speedOutBps = 1024 * 128
    )

    assertEquals("5.00 MB", stats.formatBytes(stats.bytesIn))
    assertEquals("500.0 KB", stats.formatBytes(stats.bytesOut))
    assertEquals("2.00 MB/s", stats.formatSpeed(stats.speedInBps))
    assertEquals("128.0 KB/s", stats.formatSpeed(stats.speedOutBps))
  }

  @Test
  fun `verify server profile creation`() {
    val profile = SshServerProfile(
      name = "Test VPS",
      host = "vpn.rootu.ssh.tunnel.com",
      port = 2222,
      username = "testuser",
      authType = AuthType.PASSWORD,
      password = "secret",
      socksPort = 3128
    )

    assertEquals("Test VPS", profile.name)
    assertEquals(2222, profile.port)
    assertEquals(3128, profile.socksPort)
    assertNotNull(profile)
  }

  @Test
  fun `verify oem battery helper instructions`() {
    val instructionsXiaomi = com.sshtunnel.ui.util.OemBatteryOptimizationHelper.getOemInstructions(
      com.sshtunnel.ui.util.OemBatteryOptimizationHelper.OemBrand.XIAOMI
    )
    assertEquals(3, instructionsXiaomi.size)
    assert(instructionsXiaomi[0].contains("Автозапуск"))
    assert(instructionsXiaomi[1].contains("Контроль активности"))

    val brand = com.sshtunnel.ui.util.OemBatteryOptimizationHelper.getDetectedBrand()
    assertNotNull(brand)
  }

  @Test
  fun `verify vpn settings defaults and dns validation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.sshtunnel.data.repository.VpnPrefs.get(context)

    assertEquals("1.1.1.1", com.sshtunnel.data.repository.VpnPrefs.dnsServer(prefs))
    assertEquals(true, prefs.getBoolean(com.sshtunnel.data.repository.VpnPrefs.BYPASS_LAN, true))
    assertEquals(
      com.sshtunnel.data.model.RoutingPolicy.ROUTE_ONLY_SELECTED,
      com.sshtunnel.data.repository.VpnPrefs.routingPolicy(prefs)
    )

    // An invalid stored value falls back to the default instead of breaking the VPN builder
    prefs.edit().putString(com.sshtunnel.data.repository.VpnPrefs.DNS_SERVER, "1.1.").commit()
    assertEquals("1.1.1.1", com.sshtunnel.data.repository.VpnPrefs.dnsServer(prefs))
    assertEquals(false, com.sshtunnel.data.repository.VpnPrefs.isValidDns("127.0.0.1"))
    assertEquals(true, com.sshtunnel.data.repository.VpnPrefs.isValidDns("9.9.9.9"))
  }

  @Test
  fun `verify initial server list is empty without pre-installed server`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.sshtunnel.data.local.AppDatabase.getInstance(context)
    val active = db.serverDao().getActiveServerSync()
    assertEquals(null, active)
  }
}
