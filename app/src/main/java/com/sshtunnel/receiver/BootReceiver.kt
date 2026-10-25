package com.sshtunnel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.sshtunnel.data.local.AppDatabase
import com.sshtunnel.vpn.SshVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start_on_boot", false)

            if (autoStart) {
                // Check if VPN is prepared
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent == null) {
                    // VPN permission is already granted! We can auto-start
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getInstance(context)
                        val activeServer = db.serverDao().getActiveServerSync()
                        if (activeServer != null) {
                            SshVpnService.log("Device boot completed: Auto-starting VPN tunnel for ${activeServer.name}...")
                            val serviceIntent = Intent(context, SshVpnService::class.java).apply {
                                this.action = SshVpnService.ACTION_CONNECT
                                putExtra(SshVpnService.EXTRA_SERVER_ID, activeServer.id)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                    }
                } else {
                    SshVpnService.log("Auto-start on boot skipped: VPN permission must be granted once in UI first.", isError = true)
                }
            }
        }
    }
}
