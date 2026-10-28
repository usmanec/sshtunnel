package top.rootu.ssh.tunnel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import top.rootu.ssh.tunnel.data.model.AuthType
import top.rootu.ssh.tunnel.data.model.SshServerProfile
import top.rootu.ssh.tunnel.data.model.VpnStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import top.rootu.ssh.tunnel.ui.util.OemBatteryOptimizationHelper
import top.rootu.ssh.tunnel.ui.util.OemBatteryOptimizationHelper.OemBrand
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
    val context = ApplicationProvider.getApplicationContext<Context>()
    val instructionsXiaomi = OemBatteryOptimizationHelper.getOemInstructions(context, OemBrand.XIAOMI)
    assertEquals(3, instructionsXiaomi.size)
    assert(instructionsXiaomi[0].contains("Autostart"))

    val brand = OemBatteryOptimizationHelper.getDetectedBrand()
    assertNotNull(brand)
  }

  @Test
  @Config(qualifiers = "ru")
  fun `russian translation is used for russian locale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val instructionsXiaomi = OemBatteryOptimizationHelper.getOemInstructions(context, OemBrand.XIAOMI)
    assert(instructionsXiaomi[0].contains("Автозапуск"))
    assertEquals("Стандартный Android", OemBatteryOptimizationHelper.brandName(context, OemBrand.GENERIC))
    assertEquals("Главная", context.getString(R.string.nav_home))
    assertEquals("Подключено к srv", context.getString(R.string.notif_connected, "srv"))
    assertEquals("Сервер 3", context.getString(R.string.new_server_name, 3))
  }

  @Test
  fun `english is the default language`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Home", context.getString(R.string.nav_home))
    assertEquals("Connected to srv", context.getString(R.string.notif_connected, "srv"))
    assertEquals("Server 3", context.getString(R.string.new_server_name, 3))
  }

  @Test
  fun `verify vpn settings defaults and dns validation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = top.rootu.ssh.tunnel.data.repository.VpnPrefs.get(context)

    assertEquals("1.1.1.1", top.rootu.ssh.tunnel.data.repository.VpnPrefs.dnsServer(prefs))
    assertEquals(true, prefs.getBoolean(top.rootu.ssh.tunnel.data.repository.VpnPrefs.BYPASS_LAN, true))
    assertEquals(
      top.rootu.ssh.tunnel.data.model.RoutingPolicy.ROUTE_ONLY_SELECTED,
      top.rootu.ssh.tunnel.data.repository.VpnPrefs.routingPolicy(prefs)
    )

    // An invalid stored value falls back to the default instead of breaking the VPN builder
    prefs.edit().putString(top.rootu.ssh.tunnel.data.repository.VpnPrefs.DNS_SERVER, "1.1.").commit()
    assertEquals("1.1.1.1", top.rootu.ssh.tunnel.data.repository.VpnPrefs.dnsServer(prefs))
    assertEquals(false, top.rootu.ssh.tunnel.data.repository.VpnPrefs.isValidDns("127.0.0.1"))
    assertEquals(true, top.rootu.ssh.tunnel.data.repository.VpnPrefs.isValidDns("9.9.9.9"))
  }

  @Test
  fun `verify initial server list is empty without pre-installed server`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = top.rootu.ssh.tunnel.data.local.AppDatabase.getInstance(context)
    val active = db.serverDao().getActiveServerSync()
    assertEquals(null, active)
  }
}
