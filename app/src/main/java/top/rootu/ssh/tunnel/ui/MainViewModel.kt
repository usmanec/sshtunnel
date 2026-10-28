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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import top.rootu.ssh.tunnel.update.UpdateInfo
import top.rootu.ssh.tunnel.update.UpdateManager
import java.io.File
import top.rootu.ssh.tunnel.vpn.net.GeoIpInfo
import top.rootu.ssh.tunnel.vpn.net.GeoIpLookup
import top.rootu.ssh.tunnel.vpn.net.Socks5Client
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppFilterMode {
    ALL,
    USER_ONLY,
    SYSTEM_ONLY,
    SELECTED_ONLY
}

private const val EXTERNAL_IP_TTL_MS = 5 * 60_000L

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application
    private val repository = VpnRepository(application)

    val vpnStatus: StateFlow<VpnStatus> = SshVpnService.vpnStatus
    val vpnStats: StateFlow<VpnStats> = SshVpnService.vpnStats
    val logs: StateFlow<List<ConnectionLogEntry>> = SshVpnService.logs

    /** Server the running tunnel uses and the port its SOCKS5 proxy actually listens on. */
    val tunnelServer: StateFlow<SshServerProfile?> = SshVpnService.activeServer
    val socksPort: StateFlow<Int?> = SshVpnService.socksPort

    /** Make-before-break server switch: target in progress, or why the last one failed. */
    val switchingTo: StateFlow<SshServerProfile?> = SshVpnService.switchingTo
    val switchError: StateFlow<String?> = SshVpnService.switchError

    fun dismissSwitchError() {
        SshVpnService.clearSwitchError()
    }

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
     * Makes [id] the active server. While the tunnel is running it switches over right away
     * (no permission prompt is needed then); the service marks the server active only once the
     * new session is up, so a failed switch leaves the current server selected.
     */
    fun setActiveServer(context: Context, id: Long) {
        viewModelScope.launch {
            if (SshVpnService.isRunning) {
                if (SshVpnService.activeServer.value?.id != id) {
                    startVpn(context.applicationContext, id)
                } else if (SshVpnService.switchingTo.value != null) {
                    // Back to the server in use: drop the pending switch instead of reconnecting
                    context.applicationContext.startService(
                        Intent(context.applicationContext, SshVpnService::class.java).setAction(SshVpnService.ACTION_CANCEL_SWITCH)
                    )
                }
            } else {
                repository.setActiveServer(id)
            }
        }
    }

    fun openAddServerDialog() {
        viewModelScope.launch {
            // From the database: allServers is empty while no screen collects it
            val servers = repository.allServers.first()
            _editingServer.value = SshServerProfile(
                name = defaultServerName(servers),
                host = "",
                port = 22,
                username = "root",
                // Empty in the form: a free port is picked at every connection
                socksPort = 0,
                keepAliveIntervalSeconds = 15
            )
            _testConnectionResult.value = null
            _isTestFailed.value = false
            _isServerDialogOpen.value = true
        }
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

    /** "Server N", where N is the number the new server gets in the list (skipping names in use). */
    private fun defaultServerName(servers: List<SshServerProfile>): String {
        val names = servers.mapTo(HashSet()) { it.name }
        var number = servers.size + 1
        while (app.getString(R.string.new_server_name, number) in names) number++
        return app.getString(R.string.new_server_name, number)
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

    // External IP and location shown on Home

    data class ExternalIpState(
        val loading: Boolean = false,
        val info: GeoIpInfo? = null,
        val viaTunnel: Boolean = false,
        val failed: Boolean = false
    )

    private val _externalIp = MutableStateFlow(ExternalIpState())
    val externalIp: StateFlow<ExternalIpState> = _externalIp.asStateFlow()
    private var externalIpJob: Job? = null
    private var externalIpKey: String? = null
    private var externalIpCheckedAt = 0L

    /**
     * Looks up the external IP. While the tunnel is up the request goes through the local SOCKS5
     * proxy (our own app is excluded from the tunnel), so it shows the SSH server's exit address;
     * otherwise the direct one. Results are reused for a few minutes unless [force] is set.
     */
    fun refreshExternalIp(force: Boolean = false) {
        val status = vpnStatus.value
        val connected = status == VpnStatus.CONNECTED || status == VpnStatus.IDLE_SUSPENDED
        // While connecting or reconnecting the path is about to change: keep what is shown
        if (!connected && status != VpnStatus.DISCONNECTED && status != VpnStatus.ERROR) return
        val server = if (connected) SshVpnService.activeServer.value ?: return else null
        val socksPort = if (connected) SshVpnService.socksPort.value ?: return else 0
        val key = server?.let { "tunnel:${it.id}:$socksPort" } ?: "direct"
        val fresh = System.currentTimeMillis() - externalIpCheckedAt < EXTERNAL_IP_TTL_MS
        if (!force && key == externalIpKey && fresh && _externalIp.value.info != null) return

        val sameKey = key == externalIpKey
        externalIpKey = key
        externalIpJob?.cancel()
        _externalIp.value = ExternalIpState(
            loading = true,
            info = if (sameKey) _externalIp.value.info else null,
            viaTunnel = server != null
        )
        val connector: (String, Int) -> Socket = if (server != null) {
            { host, port -> Socks5Client.connectHost(socksPort, host, port, 15_000) }
        } else {
            { host, port -> Socket().apply { connect(InetSocketAddress(host, port), 10_000) } }
        }
        val language = app.resources.configuration.locales[0].language
        externalIpJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GeoIpLookup(connector, language).lookup() }
            }
            externalIpCheckedAt = if (result.isSuccess) System.currentTimeMillis() else 0L
            _externalIp.value = ExternalIpState(
                loading = false,
                info = result.getOrNull(),
                viaTunnel = server != null,
                failed = result.isFailure
            )
        }
    }

    // In-app updates from GitHub releases (Settings → Updates)

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        object UpToDate : UpdateState()
        data class Available(val info: UpdateInfo) : UpdateState()
        /** [progress] is 0..100, or -1 when the size is unknown. */
        data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState()
        data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState()
        /** Android 8+: the user has to allow installing apps from this app first. */
        data class NeedsInstallPermission(val info: UpdateInfo, val file: File) : UpdateState()
        data class Failed(val error: UpdateError, val detail: String? = null) : UpdateState()
    }

    enum class UpdateError { CHECK_FAILED, DOWNLOAD_FAILED, NOT_THIS_APP, SIGNATURE_MISMATCH, NO_INSTALLER, NO_PERMISSION_SCREEN }

    private val updateManager = UpdateManager(application)
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    val currentVersion: String get() = updateManager.currentVersion
    private var updateJob: Job? = null
    private var autoCheckedForUpdate = false

    /** Checks once per app session when Settings opens; the button checks again on demand. */
    fun checkForUpdateOnce() {
        if (autoCheckedForUpdate) return
        autoCheckedForUpdate = true
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        updateJob?.cancel()
        _updateState.value = UpdateState.Checking
        updateJob = viewModelScope.launch {
            _updateState.value = try {
                updateManager.checkForUpdate()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(UpdateError.CHECK_FAILED, e.message)
            }
        }
    }

    fun downloadAndInstallUpdate(info: UpdateInfo) {
        updateJob?.cancel()
        _updateState.value = UpdateState.Downloading(info, 0)
        updateJob = viewModelScope.launch {
            try {
                val file = updateManager.download(info) { progress ->
                    _updateState.value = UpdateState.Downloading(info, progress)
                }
                _updateState.value = UpdateState.ReadyToInstall(info, file)
                installUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateManager.InvalidApkException) {
                _updateState.value = UpdateState.Failed(
                    if (e.problem == UpdateManager.Problem.SIGNATURE_MISMATCH) UpdateError.SIGNATURE_MISMATCH else UpdateError.NOT_THIS_APP
                )
            } catch (e: Exception) {
                _updateState.value = UpdateState.Failed(UpdateError.DOWNLOAD_FAILED, e.message)
            }
        }
    }

    /** Installs the downloaded APK, asking for the "install unknown apps" permission first if needed. */
    fun installUpdate() {
        val (info, file) = when (val state = _updateState.value) {
            is UpdateState.ReadyToInstall -> state.info to state.file
            is UpdateState.NeedsInstallPermission -> state.info to state.file
            else -> return
        }
        if (!updateManager.canInstall()) {
            _updateState.value = UpdateState.NeedsInstallPermission(info, file)
            if (!updateManager.openInstallPermissionSettings()) {
                _updateState.value = UpdateState.Failed(UpdateError.NO_PERMISSION_SCREEN)
            }
            return
        }
        _updateState.value = UpdateState.ReadyToInstall(info, file)
        if (!updateManager.install(file)) {
            _updateState.value = UpdateState.Failed(UpdateError.NO_INSTALLER)
        }
    }

    fun clearLogs() {
        SshVpnService.clearLogs()
    }
}
