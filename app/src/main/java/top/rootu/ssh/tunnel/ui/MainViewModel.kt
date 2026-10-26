package top.rootu.ssh.tunnel.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import top.rootu.ssh.tunnel.R
import top.rootu.ssh.tunnel.data.model.AppRoutingRule
import top.rootu.ssh.tunnel.data.model.ConnectionLogEntry
import top.rootu.ssh.tunnel.data.model.RoutingPolicy
import top.rootu.ssh.tunnel.data.model.SshServerProfile
import top.rootu.ssh.tunnel.data.model.VpnStats
import top.rootu.ssh.tunnel.data.model.VpnStatus
import top.rootu.ssh.tunnel.data.repository.VpnRepository
import top.rootu.ssh.tunnel.vpn.SshTunnelClient
import top.rootu.ssh.tunnel.vpn.SshVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppFilterMode {
    ALL,
    USER_ONLY,
    SYSTEM_ONLY,
    SELECTED_ONLY
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application
    private val repository = VpnRepository(application)

    val vpnStatus: StateFlow<VpnStatus> = SshVpnService.vpnStatus
    val vpnStats: StateFlow<VpnStats> = SshVpnService.vpnStats
    val logs: StateFlow<List<ConnectionLogEntry>> = SshVpnService.logs

    val allServers: StateFlow<List<SshServerProfile>> = repository.allServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeServer: StateFlow<SshServerProfile?> = repository.activeServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val routingPolicy: StateFlow<RoutingPolicy> = repository.routingPolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutingPolicy.ROUTE_ONLY_SELECTED)

    val autoReconnect: StateFlow<Boolean> = repository.autoReconnect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoStartOnBoot: StateFlow<Boolean> = repository.autoStartOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val smartBatterySaver: StateFlow<Boolean> = repository.smartBatterySaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val screenOffThrottling: StateFlow<Boolean> = repository.screenOffThrottling
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val aggressiveBatterySaver: StateFlow<Boolean> = repository.aggressiveBatterySaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val idleTimeoutSeconds: StateFlow<Int> = repository.idleTimeoutSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    val dnsServer: StateFlow<String> = repository.dnsServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1.1.1.1")

    val bypassLan: StateFlow<Boolean> = repository.bypassLan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * Routing settings (apps, policy, DNS, LAN bypass) are applied when the TUN interface is
     * created. While the VPN runs, a change raises this flag so the UI can offer a reconnect.
     */
    private val _needsReconnect = MutableStateFlow(false)
    val needsReconnect: StateFlow<Boolean> = _needsReconnect.asStateFlow()

    private fun markRoutingChanged() {
        if (SshVpnService.isRunning) _needsReconnect.value = true
    }

    val selectedAppCount: StateFlow<Int> = repository.selectedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // App screen filtering
    val appSearchQuery = MutableStateFlow("")
    val appFilterMode = MutableStateFlow(AppFilterMode.USER_ONLY)

    val filteredApps: StateFlow<List<AppRoutingRule>> = combine(
        repository.allAppRules,
        appSearchQuery,
        appFilterMode
    ) { allRules, query, filter ->
        allRules.filter { rule ->
            val matchesQuery = query.isBlank() ||
                    rule.appName.contains(query, ignoreCase = true) ||
                    rule.packageName.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                AppFilterMode.ALL -> true
                AppFilterMode.USER_ONLY -> !rule.isSystemApp
                AppFilterMode.SYSTEM_ONLY -> rule.isSystemApp
                AppFilterMode.SELECTED_ONLY -> rule.isSelected
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Server Dialog & Testing
    private val _editingServer = MutableStateFlow<SshServerProfile?>(null)
    val editingServer: StateFlow<SshServerProfile?> = _editingServer.asStateFlow()

    private val _isServerDialogOpen = MutableStateFlow(false)
    val isServerDialogOpen: StateFlow<Boolean> = _isServerDialogOpen.asStateFlow()

    private val _testConnectionResult = MutableStateFlow<String?>(null)
    val testConnectionResult: StateFlow<String?> = _testConnectionResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    init {
        viewModelScope.launch {
            repository.syncInstalledApps()
        }
        viewModelScope.launch {
            vpnStatus.collect { status ->
                if (status == VpnStatus.DISCONNECTED || status == VpnStatus.ERROR) _needsReconnect.value = false
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch { repository.syncInstalledApps() }
    }

    /** Starts the VPN, or restarts it with fresh settings when it is already running. */
    fun startVpn(context: Context, serverId: Long? = null) {
        _needsReconnect.value = false
        val intent = Intent(context, SshVpnService::class.java).apply {
            action = SshVpnService.ACTION_CONNECT
            if (serverId != null) {
                putExtra(SshVpnService.EXTRA_SERVER_ID, serverId)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopVpn(context: Context) {
        val intent = Intent(context, SshVpnService::class.java).apply {
            action = SshVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    /**
     * Makes [id] the active server. If the VPN is running on another server it switches over
     * right away (no VPN permission prompt is needed while the VPN is already up).
     */
    fun setActiveServer(context: Context, id: Long) {
        viewModelScope.launch {
            repository.setActiveServer(id)
            val running = SshVpnService.isRunning
            if (running && SshVpnService.activeServer.value?.id != id) {
                startVpn(context.applicationContext, id)
            }
        }
    }

    fun openAddServerDialog() {
        _editingServer.value = SshServerProfile(
            name = app.getString(R.string.new_server_name),
            host = "",
            port = 22,
            username = "root",
            socksPort = 10808,
            keepAliveIntervalSeconds = 15
        )
        _testConnectionResult.value = null
        _isTestFailed.value = false
        _isServerDialogOpen.value = true
    }

    fun openEditServerDialog(server: SshServerProfile) {
        _editingServer.value = server
        _testConnectionResult.value = null
        _isTestFailed.value = false
        _isServerDialogOpen.value = true
    }

    fun closeServerDialog() {
        _isServerDialogOpen.value = false
        _editingServer.value = null
        _testConnectionResult.value = null
        _isTestFailed.value = false
    }

    /** Saves without a successful test (e.g. the server is temporarily unreachable). */
    fun saveServerUnverified(context: Context, server: SshServerProfile) {
        viewModelScope.launch {
            repository.saveServer(server)
            closeServerDialog()
            restartIfRunningOn(context, server.id)
        }
    }

    /** Re-applies an edited profile when the VPN currently uses it. */
    private fun restartIfRunningOn(context: Context, serverId: Long) {
        if (serverId != 0L && SshVpnService.isRunning && SshVpnService.activeServer.value?.id == serverId) {
            startVpn(context.applicationContext, serverId)
        }
    }

    fun deleteServer(server: SshServerProfile) {
        viewModelScope.launch {
            repository.deleteServer(server)
            closeServerDialog()
        }
    }

    private val _isTestSuccessful = MutableStateFlow(false)
    val isTestSuccessful: StateFlow<Boolean> = _isTestSuccessful.asStateFlow()

    /** The last test finished with an error (the message itself is localized, so not parsed). */
    private val _isTestFailed = MutableStateFlow(false)
    val isTestFailed: StateFlow<Boolean> = _isTestFailed.asStateFlow()

    private var verifiedProfileHash: Int? = null

    fun testServer(profile: SshServerProfile, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isTesting.value = true
            _isTestSuccessful.value = false
            _isTestFailed.value = false
            _testConnectionResult.value = app.getString(R.string.test_in_progress, profile.host, profile.port)
            val result = withContext(Dispatchers.IO) {
                SshTunnelClient(app, null).testSshAuthentication(profile)
            }
            _isTesting.value = false
            if (result.isSuccess) {
                val ping = result.getOrNull() ?: 0L
                val msg = app.getString(R.string.test_ok, ping)
                _testConnectionResult.value = msg
                _isTestSuccessful.value = true
                verifiedProfileHash = computeProfileHash(profile)
                onComplete?.invoke(true, msg)
            } else {
                val err = result.exceptionOrNull()?.message ?: app.getString(R.string.test_failed_generic)
                _testConnectionResult.value = app.getString(R.string.test_error, err)
                _isTestSuccessful.value = false
                _isTestFailed.value = true
                verifiedProfileHash = null
                onComplete?.invoke(false, err)
            }
        }
    }

    fun isProfileVerified(profile: SshServerProfile): Boolean {
        return _isTestSuccessful.value && verifiedProfileHash == computeProfileHash(profile)
    }

    private fun computeProfileHash(profile: SshServerProfile): Int {
        return (profile.host + ":" + profile.port + ":" + profile.username + ":" + profile.authType.name + ":" + profile.password + ":" + profile.privateKey.trim() + ":" + profile.passphrase).hashCode()
    }

    fun saveServerVerified(
        context: Context,
        profile: SshServerProfile,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val save = {
            viewModelScope.launch {
                repository.saveServer(profile)
                closeServerDialog()
                onSuccess()
                restartIfRunningOn(context, profile.id)
            }
        }
        // If already tested and verified, save directly, otherwise run the test first
        if (isProfileVerified(profile)) {
            save()
        } else {
            testServer(profile) { success, msg ->
                if (success) save() else onError(msg)
            }
        }
    }

    fun readKeyFileContent(context: Context, uri: android.net.Uri): Pair<String, String>? {
        return try {
            var fileName = "imported_key"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: "imported_key"
                }
            }
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
            if (content != null && content.isNotBlank()) {
                Pair(fileName, content.trim())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun toggleAppSelection(packageName: String, isSelected: Boolean) {
        viewModelScope.launch {
            repository.setAppSelected(packageName, isSelected)
            markRoutingChanged()
        }
    }

    /** Selects or clears the apps currently shown by the search and filter (not the hidden ones). */
    fun selectShownApps(isSelected: Boolean) {
        val shown = filteredApps.value.map { it.packageName }
        if (shown.isEmpty()) return
        viewModelScope.launch {
            repository.setAppsSelected(shown, isSelected)
            markRoutingChanged()
        }
    }

    fun setRoutingPolicy(policy: RoutingPolicy) {
        if (policy == routingPolicy.value) return
        viewModelScope.launch {
            repository.setRoutingPolicy(policy)
            markRoutingChanged()
        }
    }

    fun setSmartBatterySaver(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSmartBatterySaver(enabled)
        }
    }

    fun setScreenOffThrottling(enabled: Boolean) {
        viewModelScope.launch {
            repository.setScreenOffThrottling(enabled)
        }
    }

    fun setAggressiveBatterySaver(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAggressiveBatterySaver(enabled)
        }
    }

    fun setBypassLan(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBypassLan(enabled)
            markRoutingChanged()
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoReconnect(enabled)
        }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoStartOnBoot(enabled)
        }
    }

    fun setIdleTimeoutSeconds(seconds: Int) {
        viewModelScope.launch {
            repository.setIdleTimeoutSeconds(seconds)
        }
    }

    /** Saves a valid IPv4 DNS server; [onResult] gets false for invalid input. */
    fun setDnsServer(dns: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val changed = dns.trim() != dnsServer.value
            val ok = repository.setDnsServer(dns)
            if (ok && changed) markRoutingChanged()
            onResult(ok)
        }
    }

    fun clearLogs() {
        SshVpnService.clearLogs()
    }
}
