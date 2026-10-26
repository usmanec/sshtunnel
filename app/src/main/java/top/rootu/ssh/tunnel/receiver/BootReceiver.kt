package top.rootu.ssh.tunnel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import top.rootu.ssh.tunnel.R
import top.rootu.ssh.tunnel.data.local.AppDatabase
import top.rootu.ssh.tunnel.data.repository.VpnPrefs
import top.rootu.ssh.tunnel.vpn.SshVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (!VpnPrefs.get(context).getBoolean(VpnPrefs.AUTO_START_ON_BOOT, false)) return
        if (VpnService.prepare(context) != null) {
            SshVpnService.log(context.getString(R.string.log_boot_no_permission), isError = true)
            return
        }

        // Keep the receiver alive while the database is read
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activeServer = AppDatabase.getInstance(context).serverDao().getActiveServerSync() ?: return@launch
                SshVpnService.log(context.getString(R.string.log_boot_start, activeServer.name))
                val serviceIntent = Intent(context, SshVpnService::class.java).apply {
                    this.action = SshVpnService.ACTION_CONNECT
                    putExtra(SshVpnService.EXTRA_SERVER_ID, activeServer.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                SshVpnService.log(context.getString(R.string.log_boot_failed, e.message.orEmpty()), isError = true)
            } finally {
                pending.finish()
            }
        }
    }
}
