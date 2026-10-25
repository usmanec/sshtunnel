package com.sshtunnel.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import com.sshtunnel.data.local.AppDatabase
import com.sshtunnel.data.model.AppRoutingRule
import com.sshtunnel.data.model.RoutingPolicy
import com.sshtunnel.data.model.SshServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VpnRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val serverDao = db.serverDao()
    private val appRuleDao = db.appRuleDao()
    private val prefs = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)

    val allServers: Flow<List<SshServerProfile>> = serverDao.getAllServers()
    val activeServer: Flow<SshServerProfile?> = serverDao.getActiveServer()
    val allAppRules: Flow<List<AppRoutingRule>> = appRuleDao.getAllRules()
    val selectedCount: Flow<Int> = appRuleDao.getSelectedCountFlow()

    private val _routingPolicy = MutableStateFlow(loadRoutingPolicy())
    val routingPolicy: Flow<RoutingPolicy> = _routingPolicy.asStateFlow()

    private val _autoReconnect = MutableStateFlow(prefs.getBoolean("auto_reconnect", true))
    val autoReconnect: Flow<Boolean> = _autoReconnect.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(prefs.getBoolean("auto_start_on_boot", false))
    val autoStartOnBoot: Flow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _smartBatterySaver = MutableStateFlow(prefs.getBoolean("smart_battery_saver", true))
    val smartBatterySaver: Flow<Boolean> = _smartBatterySaver.asStateFlow()

    private val _screenOffThrottling = MutableStateFlow(prefs.getBoolean("screen_off_throttling", true))
    val screenOffThrottling: Flow<Boolean> = _screenOffThrottling.asStateFlow()

    private val _aggressiveBatterySaver = MutableStateFlow(prefs.getBoolean("aggressive_battery_saver", false))
    val aggressiveBatterySaver: Flow<Boolean> = _aggressiveBatterySaver.asStateFlow()

    private val _idleTimeoutSeconds = MutableStateFlow(prefs.getInt("idle_timeout_seconds", 60))
    val idleTimeoutSeconds: Flow<Int> = _idleTimeoutSeconds.asStateFlow()

    private val _dnsServer = MutableStateFlow(prefs.getString("dns_server", "1.1.1.1") ?: "1.1.1.1")
    val dnsServer: Flow<String> = _dnsServer.asStateFlow()

    private val _isolateDnsToSelectedApps = MutableStateFlow(prefs.getBoolean("isolate_dns_to_selected", true))
    val isolateDnsToSelectedApps: Flow<Boolean> = _isolateDnsToSelectedApps.asStateFlow()

    private fun loadRoutingPolicy(): RoutingPolicy {
        val name = prefs.getString("routing_policy", RoutingPolicy.ROUTE_ONLY_SELECTED.name)
        return runCatching { RoutingPolicy.valueOf(name!!) }.getOrDefault(RoutingPolicy.ROUTE_ONLY_SELECTED)
    }

    suspend fun setRoutingPolicy(policy: RoutingPolicy) = withContext(Dispatchers.IO) {
        prefs.edit().putString("routing_policy", policy.name).apply()
        _routingPolicy.value = policy
    }

    suspend fun setAutoReconnect(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("auto_reconnect", enabled).apply()
        _autoReconnect.value = enabled
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("auto_start_on_boot", enabled).apply()
        _autoStartOnBoot.value = enabled
    }

    suspend fun setSmartBatterySaver(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("smart_battery_saver", enabled).apply()
        _smartBatterySaver.value = enabled
    }

    suspend fun setScreenOffThrottling(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("screen_off_throttling", enabled).apply()
        _screenOffThrottling.value = enabled
    }

    suspend fun setAggressiveBatterySaver(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("aggressive_battery_saver", enabled).apply()
        _aggressiveBatterySaver.value = enabled
    }

    suspend fun setIdleTimeoutSeconds(seconds: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt("idle_timeout_seconds", seconds).apply()
        _idleTimeoutSeconds.value = seconds
    }

    suspend fun setDnsServer(dns: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString("dns_server", dns).apply()
        _dnsServer.value = dns
    }

    suspend fun setIsolateDnsToSelectedApps(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("isolate_dns_to_selected", enabled).apply()
        _isolateDnsToSelectedApps.value = enabled
    }

    fun isDnsIsolatedSync(): Boolean = prefs.getBoolean("isolate_dns_to_selected", true)
    fun isScreenOffThrottlingSync(): Boolean = prefs.getBoolean("screen_off_throttling", true)
    fun isAggressiveBatterySaverSync(): Boolean = prefs.getBoolean("aggressive_battery_saver", false)

    suspend fun syncInstalledApps() = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(0)
            val myPackage = context.packageName
            val rules = installed
                .filter { it.packageName != myPackage }
                .map { appInfo ->
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    AppRoutingRule(
                        packageName = appInfo.packageName,
                        appName = appName,
                        isSelected = false,
                        isSystemApp = isSystem
                    )
                }.sortedBy { it.appName.lowercase() }

            appRuleDao.insertAll(rules)
        } catch (e: Exception) {
            // Log or ignore
        }
    }

    suspend fun setAppSelected(packageName: String, isSelected: Boolean) = withContext(Dispatchers.IO) {
        appRuleDao.setAppSelected(packageName, isSelected)
    }

    suspend fun setAllAppsSelected(isSelected: Boolean) = withContext(Dispatchers.IO) {
        appRuleDao.setAllSelected(isSelected)
    }

    suspend fun saveServer(server: SshServerProfile): Long = withContext(Dispatchers.IO) {
        if (server.id == 0L) {
            val id = serverDao.insert(server)
            // If it's the first server, set it as active
            val all = serverDao.getActiveServerSync()
            if (all == null) {
                serverDao.setActiveServer(id)
            }
            id
        } else {
            serverDao.update(server)
            server.id
        }
    }

    suspend fun deleteServer(server: SshServerProfile) = withContext(Dispatchers.IO) {
        serverDao.delete(server)
    }

    suspend fun setActiveServer(serverId: Long) = withContext(Dispatchers.IO) {
        serverDao.setActiveServer(serverId)
    }

    suspend fun initDefaultServerIfEmpty() = withContext(Dispatchers.IO) {
        // No pre-installed server; user starts with an empty server list
    }
}
