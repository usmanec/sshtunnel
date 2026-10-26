package top.rootu.ssh.tunnel.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.withTransaction
import top.rootu.ssh.tunnel.data.local.AppDatabase
import top.rootu.ssh.tunnel.data.model.AppRoutingRule
import top.rootu.ssh.tunnel.data.model.RoutingPolicy
import top.rootu.ssh.tunnel.data.model.SshServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VpnRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val serverDao = db.serverDao()
    private val appRuleDao = db.appRuleDao()
    private val prefs = VpnPrefs.get(context)

    val allServers: Flow<List<SshServerProfile>> = serverDao.getAllServers()
    val activeServer: Flow<SshServerProfile?> = serverDao.getActiveServer()
    val allAppRules: Flow<List<AppRoutingRule>> = appRuleDao.getAllRules()
    val selectedCount: Flow<Int> = appRuleDao.getSelectedCountFlow()

    private val _routingPolicy = MutableStateFlow(VpnPrefs.routingPolicy(prefs))
    val routingPolicy: Flow<RoutingPolicy> = _routingPolicy.asStateFlow()

    private val _autoReconnect = MutableStateFlow(prefs.getBoolean(VpnPrefs.AUTO_RECONNECT, true))
    val autoReconnect: Flow<Boolean> = _autoReconnect.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(prefs.getBoolean(VpnPrefs.AUTO_START_ON_BOOT, false))
    val autoStartOnBoot: Flow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _smartBatterySaver = MutableStateFlow(prefs.getBoolean(VpnPrefs.SMART_BATTERY_SAVER, true))
    val smartBatterySaver: Flow<Boolean> = _smartBatterySaver.asStateFlow()

    private val _screenOffThrottling = MutableStateFlow(prefs.getBoolean(VpnPrefs.SCREEN_OFF_THROTTLING, true))
    val screenOffThrottling: Flow<Boolean> = _screenOffThrottling.asStateFlow()

    private val _aggressiveBatterySaver = MutableStateFlow(prefs.getBoolean(VpnPrefs.AGGRESSIVE_BATTERY_SAVER, false))
    val aggressiveBatterySaver: Flow<Boolean> = _aggressiveBatterySaver.asStateFlow()

    private val _idleTimeoutSeconds = MutableStateFlow(prefs.getInt(VpnPrefs.IDLE_TIMEOUT_SECONDS, VpnPrefs.DEFAULT_IDLE_TIMEOUT))
    val idleTimeoutSeconds: Flow<Int> = _idleTimeoutSeconds.asStateFlow()

    private val _dnsServer = MutableStateFlow(VpnPrefs.dnsServer(prefs))
    val dnsServer: Flow<String> = _dnsServer.asStateFlow()

    private val _bypassLan = MutableStateFlow(prefs.getBoolean(VpnPrefs.BYPASS_LAN, true))
    val bypassLan: Flow<Boolean> = _bypassLan.asStateFlow()

    suspend fun setRoutingPolicy(policy: RoutingPolicy) = withContext(Dispatchers.IO) {
        prefs.edit().putString(VpnPrefs.ROUTING_POLICY, policy.name).apply()
        _routingPolicy.value = policy
    }

    suspend fun setAutoReconnect(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(VpnPrefs.AUTO_RECONNECT, enabled).apply()
        _autoReconnect.value = enabled
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(VpnPrefs.AUTO_START_ON_BOOT, enabled).apply()
        _autoStartOnBoot.value = enabled
    }

    suspend fun setSmartBatterySaver(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(VpnPrefs.SMART_BATTERY_SAVER, enabled).apply()
        _smartBatterySaver.value = enabled
    }

    suspend fun setScreenOffThrottling(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(VpnPrefs.SCREEN_OFF_THROTTLING, enabled).apply()
        _screenOffThrottling.value = enabled
    }

    suspend fun setAggressiveBatterySaver(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(VpnPrefs.AGGRESSIVE_BATTERY_SAVER, enabled).apply()
        _aggressiveBatterySaver.value = enabled
    }

    suspend fun setIdleTimeoutSeconds(seconds: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(VpnPrefs.IDLE_TIMEOUT_SECONDS, seconds).apply()
        _idleTimeoutSeconds.value = seconds
    }

    /** Stores the DNS server if it is a valid IPv4 address. Returns false otherwise. */
    suspend fun setDnsServer(dns: String): Boolean = withContext(Dispatchers.IO) {
        val value = dns.trim()
        if (!VpnPrefs.isValidDns(value)) return@withContext false
        prefs.edit().putString(VpnPrefs.DNS_SERVER, value).apply()
        _dnsServer.value = value
        true
    }

    suspend fun setBypassLan(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(VpnPrefs.BYPASS_LAN, enabled).apply()
        _bypassLan.value = enabled
    }

    /**
     * Synchronizes the app list with installed packages: adds new apps, refreshes names and the
     * "system" flag, removes uninstalled ones. Selections are kept.
     *
     * An app counts as a user app when it has a launcher entry (phone or TV launcher) or was
     * installed/updated by the user. Pre-installed TV apps like YouTube or Netflix are system
     * packages on Android TV, but they must be easy to find in the default "User Apps" filter.
     */
    suspend fun syncInstalledApps() = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val myPackage = context.packageName
            val installed = pm.getInstalledApplications(0).filter { it.packageName != myPackage }
            val rules = installed.map { appInfo ->
                val flags = appInfo.flags
                val isPlainSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                val hasLauncher = pm.getLaunchIntentForPackage(appInfo.packageName) != null ||
                    pm.getLeanbackLaunchIntentForPackage(appInfo.packageName) != null
                AppRoutingRule(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    isSelected = false,
                    isSystemApp = isPlainSystem && !hasLauncher
                )
            }

            val existing = appRuleDao.getAllRulesSync().associateBy { it.packageName }
            val added = rules.filter { it.packageName !in existing }
            val changed = rules.filter { rule ->
                val old = existing[rule.packageName]
                old != null && (old.appName != rule.appName || old.isSystemApp != rule.isSystemApp)
            }
            val installedNames = rules.mapTo(HashSet()) { it.packageName }
            val removed = existing.keys.filter { it !in installedNames }
            if (added.isEmpty() && changed.isEmpty() && removed.isEmpty()) return@withContext

            // One transaction: observers of the app list are notified once
            db.withTransaction {
                if (added.isNotEmpty()) appRuleDao.insertAll(added)
                for (rule in changed) appRuleDao.updateAppInfo(rule.packageName, rule.appName, rule.isSystemApp)
                for (chunk in removed.chunked(500)) appRuleDao.deleteByPackageNames(chunk)
            }
        } catch (e: Exception) {
            // Package manager failures leave the previous list in place
        }
    }

    suspend fun setAppSelected(packageName: String, isSelected: Boolean) = withContext(Dispatchers.IO) {
        appRuleDao.setAppSelected(packageName, isSelected)
    }

    suspend fun setAppsSelected(packageNames: List<String>, isSelected: Boolean) = withContext(Dispatchers.IO) {
        // SQLite limits the number of bound parameters
        for (chunk in packageNames.chunked(500)) appRuleDao.setSelected(chunk, isSelected)
    }

    suspend fun saveServer(server: SshServerProfile): Long = withContext(Dispatchers.IO) {
        if (server.id == 0L) {
            val id = serverDao.insert(server)
            // The first server becomes active automatically
            if (serverDao.getActiveServerSync() == null) {
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
        // Keep one server active so "Connect" keeps working
        if (serverDao.getActiveServerSync() == null) {
            serverDao.getNewestServerId()?.let { serverDao.setActiveServer(it) }
        }
    }

    suspend fun setActiveServer(serverId: Long) = withContext(Dispatchers.IO) {
        serverDao.setActiveServer(serverId)
    }

    suspend fun getServer(serverId: Long): SshServerProfile? = withContext(Dispatchers.IO) {
        serverDao.getServerById(serverId)
    }
}
