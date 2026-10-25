package com.sshtunnel.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshtunnel.data.model.AppRoutingRule
import com.sshtunnel.data.model.ConnectionLogEntry
import com.sshtunnel.data.model.RoutingPolicy
import com.sshtunnel.data.model.SshServerProfile
import com.sshtunnel.data.model.VpnStats
import com.sshtunnel.data.model.VpnStatus
import com.sshtunnel.data.repository.VpnRepository
import com.sshtunnel.vpn.SshTunnelClient
import com.sshtunnel.vpn.SshVpnService
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

    val isolateDnsToSelectedApps: StateFlow<Boolean> = repository.isolateDnsToSelectedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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
            repository.initDefaultServerIfEmpty()
            repository.syncInstalledApps()
        }
    }

    fun startVpn(context: Context, serverId: Long? = null) {
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

    fun setActiveServer(id: Long) {
        viewModelScope.launch {
            repository.setActiveServer(id)
        }
    }

    fun openAddServerDialog() {
        _editingServer.value = SshServerProfile(
            name = "New SSH Server",
            host = "",
            port = 22,
            username = "root",
            socksPort = 10808,
            keepAliveIntervalSeconds = 15
        )
        _testConnectionResult.value = null
        _isServerDialogOpen.value = true
    }

    fun openEditServerDialog(server: SshServerProfile) {
        _editingServer.value = server
        _testConnectionResult.value = null
        _isServerDialogOpen.value = true
    }

    fun closeServerDialog() {
        _isServerDialogOpen.value = false
        _editingServer.value = null
        _testConnectionResult.value = null
    }

    fun saveServer(server: SshServerProfile) {
        viewModelScope.launch {
            repository.saveServer(server)
            closeServerDialog()
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

    private var verifiedProfileHash: Int? = null

    fun testServer(profile: SshServerProfile, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isTesting.value = true
            _isTestSuccessful.value = false
            _testConnectionResult.value = "Testing SSH connection & authentication to ${profile.host}:${profile.port}..."
            val result = withContext(Dispatchers.IO) {
                SshTunnelClient(null).testSshAuthentication(profile)
            }
            _isTesting.value = false
            if (result.isSuccess) {
                val ping = result.getOrNull() ?: 0L
                val msg = "Connection & Auth verified! Latency: ${ping}ms"
                _testConnectionResult.value = msg
                _isTestSuccessful.value = true
                verifiedProfileHash = computeProfileHash(profile)
                onComplete?.invoke(true, msg)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Connection test failed."
                _testConnectionResult.value = "Error: $err"
                _isTestSuccessful.value = false
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
        profile: SshServerProfile,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // If already tested and verified, save directly
        if (isProfileVerified(profile)) {
            viewModelScope.launch {
                repository.saveServer(profile)
                closeServerDialog()
                onSuccess()
            }
        } else {
            // Otherwise automatically run the test first before allowing save
            testServer(profile) { success, msg ->
                if (success) {
                    viewModelScope.launch {
                        repository.saveServer(profile)
                        closeServerDialog()
                        onSuccess()
                    }
                } else {
                    onError(msg)
                }
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
        }
    }

    fun selectAllApps(isSelected: Boolean) {
        viewModelScope.launch {
            repository.setAllAppsSelected(isSelected)
        }
    }

    fun setRoutingPolicy(policy: RoutingPolicy) {
        viewModelScope.launch {
            repository.setRoutingPolicy(policy)
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

    fun setIsolateDnsToSelectedApps(enabled: Boolean) {
        viewModelScope.launch {
            repository.setIsolateDnsToSelectedApps(enabled)
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

    fun setDnsServer(dns: String) {
        viewModelScope.launch {
            repository.setDnsServer(dns)
        }
    }

    fun clearLogs() {
        SshVpnService.clearLogs()
    }
}
