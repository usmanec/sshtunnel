package top.rootu.ssh.tunnel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import top.rootu.ssh.tunnel.MainActivity
import top.rootu.ssh.tunnel.R
import top.rootu.ssh.tunnel.data.local.AppDatabase
import top.rootu.ssh.tunnel.data.model.ConnectionLogEntry
import top.rootu.ssh.tunnel.data.model.LogLevel
import top.rootu.ssh.tunnel.data.model.RoutingPolicy
import top.rootu.ssh.tunnel.data.model.SshServerProfile
import top.rootu.ssh.tunnel.data.model.VpnStats
import top.rootu.ssh.tunnel.data.model.VpnStatus
import top.rootu.ssh.tunnel.data.repository.VpnPrefs
import top.rootu.ssh.tunnel.vpn.net.Cidr
import top.rootu.ssh.tunnel.vpn.net.Packets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * VPN service: TUN interface -> [VpnTunPacketHandler] -> local [Socks5ProxyServer] -> SSH.
 *
 * The TUN interface stays up for the whole service lifetime, also while SSH reconnects. During a
 * reconnect the routed apps get immediate TCP resets instead of silently falling back to the
 * direct connection, so traffic never leaks around the tunnel (kill switch).
 *
 * Switching servers (or re-applying settings) while connected is make-before-break: the new SSH
 * session is established first while the current tunnel keeps working; only then the pipeline is
 * replaced and the old session closed. If the new server fails, the current tunnel stays as is.
 *
 * Lifecycle state is only touched on the main thread; blocking work runs on IO dispatchers.
 */
class SshVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var tunnelJob: Job? = null
    private var switchJob: Job? = null
    private var statsJob: Job? = null
    private var pingJob: Job? = null

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetHandler: VpnTunPacketHandler? = null
    private var socksServer: Socks5ProxyServer? = null

    @Volatile
    private var sshClient: SshTunnelClient? = null
    @Volatile
    private var connectingClient: SshTunnelClient? = null
    /** New session being prepared by [switchTunnel] while the current one still carries traffic. */
    @Volatile
    private var switchingClient: SshTunnelClient? = null

    @Volatile
    private var isScreenOn = true
    private var screenReceiver: BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var defaultNetwork: Network? = null
    private var sshNetwork: Network? = null
    private val networkEvents = Channel<Unit>(Channel.CONFLATED)

    private val sessionBytesIn = AtomicLong(0)
    private val sessionBytesOut = AtomicLong(0)
    private val lastSocksActivity = AtomicLong(0)
    private var connectStartTime = 0L
    private var lastNotificationText: String? = null

    companion object {
        const val ACTION_CONNECT = "com.rootu.ssh.tunnel.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.rootu.ssh.tunnel.vpn.ACTION_DISCONNECT"
        /** Abandons a server switch in progress; the current tunnel is not touched. */
        const val ACTION_CANCEL_SWITCH = "com.rootu.ssh.tunnel.vpn.ACTION_CANCEL_SWITCH"
        const val EXTRA_SERVER_ID = "extra_server_id"

        private const val NOTIFICATION_ID = 2024
        private const val ERROR_NOTIFICATION_ID = 2025
        private const val CHANNEL_ID = "ssh_vpn_service_channel"
        private const val TUN_ADDRESS = "10.240.0.1"
        private const val TUN_MTU = 1500
        private const val MAX_LOG_ENTRIES = 300

        private val _vpnStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
        val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()

        private val _vpnStats = MutableStateFlow(VpnStats())
        val vpnStats: StateFlow<VpnStats> = _vpnStats.asStateFlow()

        private val _activeServer = MutableStateFlow<SshServerProfile?>(null)
        val activeServer: StateFlow<SshServerProfile?> = _activeServer.asStateFlow()

        /** Port the local SOCKS5 proxy actually listens on (random when the profile leaves it empty). */
        private val _socksPort = MutableStateFlow<Int?>(null)
        val socksPort: StateFlow<Int?> = _socksPort.asStateFlow()

        /** Server a running tunnel is switching to; the current tunnel stays up meanwhile. */
        private val _switchingTo = MutableStateFlow<SshServerProfile?>(null)
        val switchingTo: StateFlow<SshServerProfile?> = _switchingTo.asStateFlow()

        /** Localized reason the last switch failed (the previous tunnel kept running). */
        private val _switchError = MutableStateFlow<String?>(null)
        val switchError: StateFlow<String?> = _switchError.asStateFlow()

        fun clearSwitchError() {
            _switchError.value = null
        }

        private val _logs = MutableStateFlow<List<ConnectionLogEntry>>(emptyList())
        val logs: StateFlow<List<ConnectionLogEntry>> = _logs.asStateFlow()

        /** Set by the activity: latency checks only run while somebody is looking at them. */
        @Volatile
        var isUiVisible: Boolean = false

        fun log(message: String, isError: Boolean = false, level: LogLevel? = null) {
            val entry = ConnectionLogEntry(
                timestamp = System.currentTimeMillis(),
                level = level ?: if (isError) LogLevel.ERROR else LogLevel.INFO,
                message = message
            )
            _logs.update { current -> (current + entry).takeLast(MAX_LOG_ENTRIES) }
        }

        fun clearLogs() {
            _logs.value = emptyList()
        }

        val isRunning: Boolean
            get() = _vpnStatus.value != VpnStatus.DISCONNECTED && _vpnStatus.value != VpnStatus.ERROR
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenStateReceiver()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                log(getString(R.string.log_disconnect_requested))
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_CANCEL_SWITCH -> {
                if (switchJob?.isActive == true) {
                    log(getString(R.string.log_switch_cancelled))
                    cancelSwitch()
                }
                // Started only to deliver this: nothing to keep alive
                if (tunnelJob == null) stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                startForegroundCompat(buildNotification(getString(R.string.notif_starting)))
                startTunnel(intent.getLongExtra(EXTRA_SERVER_ID, -1L))
            }
            else -> {
                // Always-on VPN (action android.net.VpnService) or a restart after the process was killed
                startForegroundCompat(buildNotification(getString(R.string.notif_starting)))
                if (tunnelJob?.isActive != true) {
                    log(getString(R.string.log_started_by_system))
                    startTunnel(-1L)
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        lastNotificationText = null
        startForeground(NOTIFICATION_ID, notification)
    }

    // -------------------------------------------------------------------------------------------
    // Tunnel lifecycle
    // -------------------------------------------------------------------------------------------

    private fun startTunnel(serverId: Long) {
        // A working session is kept until the new one is ready
        if (tunnelJob?.isActive == true && sshClient?.isConnected == true) {
            switchTunnel(serverId)
            return
        }
        cancelSwitch()
        val previous = tunnelJob
        tunnelJob = serviceScope.launch {
            previous?.cancelAndJoin()
            val profile = loadProfile(serverId)
            if (profile == null) {
                log(getString(R.string.log_no_server), isError = true)
                failAndStop(getString(R.string.reason_no_server))
                return@launch
            }
            runTunnel(profile)
        }
    }

    private suspend fun loadProfile(serverId: Long): SshServerProfile? = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(applicationContext).serverDao()
        (if (serverId > 0) dao.getServerById(serverId) else null) ?: dao.getActiveServerSync()
    }

    /** Make-before-break: connects to the new server first; on failure the current tunnel stays. */
    private fun switchTunnel(serverId: Long) {
        cancelSwitch()
        switchJob = serviceScope.launch {
            val profile = loadProfile(serverId) ?: return@launch
            val currentName = _activeServer.value?.name.orEmpty()
            _switchError.value = null
            _switchingTo.value = profile
            log(getString(R.string.log_switch_start, profile.name, currentName))

            val client = newSshClient()
            switchingClient = client
            var handedOver = false
            try {
                // No wake lock here: the user is switching in the app, and the lock belongs to reconnects
                val result = runInterruptible(Dispatchers.IO) { client.connect(tunedProfile(profile)) }
                if (result.isFailure) {
                    val reason = result.exceptionOrNull()
                        ?.let { SshTunnelClient.describeError(this@SshVpnService, it, profile) }
                        .orEmpty()
                    _switchError.value = getString(R.string.switch_failed, profile.name, reason, currentName)
                    log(getString(R.string.log_switch_failed, profile.name, currentName), level = LogLevel.WARN)
                    return@launch
                }

                // The new session is up: now replace the pipeline, which closes the old session
                val previous = tunnelJob
                tunnelJob = serviceScope.launch {
                    try {
                        previous?.cancelAndJoin()
                    } catch (e: CancellationException) {
                        // Disconnected before the new pipeline took the session over
                        Thread { client.disconnect() }.start()
                        throw e
                    }
                    runTunnel(profile, preconnected = client)
                }
                handedOver = true
            } finally {
                // A newer switch may already be running: only clear what belongs to this one
                if (switchingClient === client) switchingClient = null
                if (_switchingTo.value === profile) _switchingTo.value = null
                if (!handedOver) Thread { client.disconnect() }.start()
            }
        }
    }

    private fun cancelSwitch() {
        switchJob?.cancel()
        switchJob = null
        // Aborts a connect in progress (JSch does not react to thread interrupts)
        switchingClient?.let { client -> Thread { client.disconnect() }.start() }
        switchingClient = null
        _switchingTo.value = null
    }

    private fun newSshClient() =
        SshTunnelClient(this, { socket -> protect(socket) }) { msg, isErr -> log(msg, isErr) }

    /** The battery saver stretches the keepalive interval. */
    private fun tunedProfile(profile: SshServerProfile): SshServerProfile {
        val prefs = VpnPrefs.get(this)
        val keepAlive = if (prefs.getBoolean(VpnPrefs.AGGRESSIVE_BATTERY_SAVER, false)) {
            profile.keepAliveIntervalSeconds.coerceAtLeast(60)
        } else {
            profile.keepAliveIntervalSeconds
        }
        return profile.copy(keepAliveIntervalSeconds = keepAlive)
    }

    /** [preconnected] is a session already established by [switchTunnel]. */
    private suspend fun runTunnel(profile: SshServerProfile, preconnected: SshTunnelClient? = null) {
        // stopPipeline() and shutdown() close it if the pipeline fails before using it
        connectingClient = preconnected
        _activeServer.value = profile
        if (preconnected == null) _vpnStatus.value = VpnStatus.CONNECTING
        _vpnStats.value = VpnStats()
        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).serverDao().setActiveServer(profile.id)
        }
        sessionBytesIn.set(0)
        sessionBytesOut.set(0)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ERROR_NOTIFICATION_ID)
        log(getString(R.string.log_starting, profile.name, profile.host, profile.port))
        updateNotification(getString(R.string.notif_connecting, profile.name))

        try {
            // 1. Local SOCKS5 proxy: survives SSH reconnects
            val socks = Socks5ProxyServer(
                port = profile.socksPort,
                transportProvider = { sshClient },
                onBytesTransferred = { up, down ->
                    if (up > 0) sessionBytesOut.addAndGet(up)
                    if (down > 0) sessionBytesIn.addAndGet(down)
                },
                onConnectionChange = { count -> _vpnStats.update { it.copy(activeConnections = count) } },
                onActivity = { lastSocksActivity.set(System.currentTimeMillis()) },
                onLog = { msg, isErr -> log(msg, isErr) }
            )
            try {
                withContext(Dispatchers.IO) { socks.start() }
            } catch (e: IOException) {
                log(getString(R.string.log_socks_busy, profile.socksPort, e.message.orEmpty()), isError = true)
                failAndStop(getString(R.string.reason_socks_busy, profile.socksPort))
                return
            }
            socksServer = socks
            // Port 0 in the profile = a free port picked by the system at every connection
            val socksPort = socks.boundPort
            _socksPort.value = socksPort
            log(getString(R.string.log_socks_listening, socksPort))

            // 2. TUN interface (replaces a previous one without a gap when switching servers)
            val tun = establishTun(profile) ?: return
            val oldTun = vpnInterface
            vpnInterface = tun
            if (oldTun != null) runCatching { oldTun.close() }
            packetHandler = VpnTunPacketHandler(tun, socksPort) {
                log(getString(R.string.log_tun_closed), level = LogLevel.WARN)
            }.also { it.start() }
            log(getString(R.string.log_tun_started, socksPort))

            startStatsLoop(profile)
            startPingLoop(profile)

            // 3. SSH session with automatic reconnects
            runSshLoop(profile, preconnected)
        } finally {
            withContext(NonCancellable) { stopPipeline(closeTun = false) }
        }
    }

    private suspend fun runSshLoop(profile: SshServerProfile, preconnected: SshTunnelClient?) {
        val tunedProfile = tunedProfile(profile)
        var attempt = 0
        var pending = preconnected

        while (currentCoroutineContext().isActive) {
            val client: SshTunnelClient
            val result: Result<Unit>
            if (pending != null) {
                // Already connected by switchTunnel()
                client = pending
                pending = null
                result = Result.success(Unit)
            } else {
                _vpnStatus.value = if (attempt == 0) VpnStatus.CONNECTING else VpnStatus.RECONNECTING
                client = newSshClient()
                connectingClient = client
                acquireWakeLock()
                result = try {
                    runInterruptible(Dispatchers.IO) { client.connect(tunedProfile) }
                } finally {
                    releaseWakeLock()
                    connectingClient = null
                }
            }

            if (result.isSuccess) {
                sshClient = client
                connectingClient = null
                sshNetwork = defaultNetwork
                attempt = 0
                connectStartTime = System.currentTimeMillis()
                _vpnStatus.value = VpnStatus.CONNECTED
                log(getString(R.string.log_active, profile.host), level = LogLevel.SUCCESS)
                updateNotification(getString(R.string.notif_connected, profile.name))

                val reason = waitWhileConnected(client)
                sshClient = null
                withContext(Dispatchers.IO) { client.disconnect() }
                log(reason, level = LogLevel.WARN)
            } else if (SshTunnelClient.isPermanentFailure(result.exceptionOrNull())) {
                log(getString(R.string.log_not_retrying, profile.name), isError = true)
                failAndStop(getString(R.string.reason_auth_failed, profile.name))
                return
            }

            if (!VpnPrefs.get(this).getBoolean(VpnPrefs.AUTO_RECONNECT, true)) {
                log(getString(R.string.log_autoreconnect_off), level = LogLevel.WARN)
                failAndStop(getString(R.string.reason_connection_lost))
                return
            }

            attempt++
            val delayMs = (2000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(60_000L)
            _vpnStatus.value = VpnStatus.RECONNECTING
            log(getString(R.string.log_reconnect_in, attempt, (delayMs / 1000).toInt()), level = LogLevel.WARN)
            updateNotification(getString(R.string.notif_reconnecting, profile.name), getString(R.string.notif_reconnecting_text, attempt))
            // Drop a stale event, then wait for the back-off or a network change
            networkEvents.tryReceive()
            withTimeoutOrNull(delayMs) { networkEvents.receive() }
        }
    }

    /** Suspends until the SSH session drops or the device switches networks. Returns the reason. */
    private suspend fun waitWhileConnected(client: SshTunnelClient): String {
        while (true) {
            withTimeoutOrNull(2000) { networkEvents.receive() }
            if (!client.isConnected) return getString(R.string.log_session_lost)
            val current = defaultNetwork
            if (current != null && sshNetwork != null && current != sshNetwork) {
                return getString(R.string.log_network_changed)
            }
            if (current != null && sshNetwork == null) sshNetwork = current
        }
    }

    private suspend fun establishTun(profile: SshServerProfile): ParcelFileDescriptor? {
        val prefs = VpnPrefs.get(this)
        val policy = VpnPrefs.routingPolicy(prefs)
        val dnsServer = VpnPrefs.dnsServer(prefs)
        val bypassLan = prefs.getBoolean(VpnPrefs.BYPASS_LAN, true)
        val lanSuffix = if (bypassLan) getString(R.string.log_lan_bypassed) else ""
        val selectedApps = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).appRuleDao().getSelectedPackageNames()
        }.filter { it != packageName && isInstalled(it) }

        val builder = Builder()
            .setSession(getString(R.string.session_name, profile.name))
            .setMtu(TUN_MTU)
            .addAddress(TUN_ADDRESS, 24)
            .addDnsServer(dnsServer)
            .setBlocking(false)
        // IPv6 is deliberately not configured: Android then blocks IPv6 for the routed apps,
        // so they use IPv4 through the tunnel instead of leaking over IPv6

        if (bypassLan) {
            for (route in Cidr.complement(Cidr.PRIVATE_NETWORKS)) {
                builder.addRoute(Packets.ipToString(route.address), route.prefixLength)
            }
            // The resolver must always go through the tunnel, even if it is a private address
            val dnsIp = Packets.parseIpv4(dnsServer)
            if (dnsIp != null && Cidr.PRIVATE_NETWORKS.any { (dnsIp.toLong() and 0xFFFFFFFFL) in it.start..it.endInclusive }) {
                builder.addRoute(dnsServer, 32)
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Otherwise Android treats every VPN as metered (e.g. streaming apps lower quality)
            builder.setMetered(false)
        }

        builder.setConfigureIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        if (policy == RoutingPolicy.ROUTE_ONLY_SELECTED) {
            if (selectedApps.isEmpty()) {
                log(getString(R.string.log_no_apps), isError = true)
                failAndStop(getString(R.string.reason_no_apps))
                return null
            }
            for (pkg in selectedApps) {
                runCatching { builder.addAllowedApplication(pkg) }
                    .onFailure { log(getString(R.string.log_cannot_route, pkg, it.message.orEmpty()), level = LogLevel.WARN) }
            }
            log(getString(R.string.log_routing_selected, selectedApps.size) + lanSuffix)
        } else {
            runCatching { builder.addDisallowedApplication(packageName) }
            for (pkg in selectedApps) {
                runCatching { builder.addDisallowedApplication(pkg) }
            }
            log(getString(R.string.log_routing_all, selectedApps.size) + lanSuffix)
        }

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            log(getString(R.string.log_tun_failed, e.message.orEmpty()), isError = true)
            null
        }
        if (pfd == null) {
            log(getString(R.string.log_tun_refused), isError = true)
            failAndStop(getString(R.string.reason_permission))
        }
        return pfd
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Stops SSH, SOCKS and the packet router. The TUN is kept unless [closeTun]. Main thread only. */
    private fun stopPipeline(closeTun: Boolean) {
        statsJob?.cancel()
        statsJob = null
        pingJob?.cancel()
        pingJob = null
        packetHandler?.stop()
        packetHandler = null
        socksServer?.stop()
        socksServer = null
        _socksPort.value = null
        val clients = listOfNotNull(sshClient, connectingClient)
        sshClient = null
        connectingClient = null
        if (clients.isNotEmpty()) {
            // JSch disconnect may block on socket I/O
            Thread { clients.forEach { it.disconnect() } }.start()
        }
        if (closeTun) {
            runCatching { vpnInterface?.close() }
            vpnInterface = null
        }
    }

    private fun failAndStop(reason: String) {
        shutdown()
        _vpnStatus.value = VpnStatus.ERROR
        // Keep a visible hint until the user opens the app again
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ERROR_NOTIFICATION_ID, buildNotification(getString(R.string.notif_stopped), reason, ongoing = false))
    }

    private fun shutdown() {
        cancelSwitch()
        tunnelJob?.cancel()
        tunnelJob = null
        stopPipeline(closeTun = true)
        _vpnStatus.value = VpnStatus.DISCONNECTED
        _vpnStats.value = VpnStats()
        _activeServer.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // -------------------------------------------------------------------------------------------
    // Statistics, idle detection and latency
    // -------------------------------------------------------------------------------------------

    private fun startStatsLoop(profile: SshServerProfile) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var lastTime = System.currentTimeMillis()
            var lastIn = sessionBytesIn.get()
            var lastOut = sessionBytesOut.get()
            while (isActive) {
                val prefs = VpnPrefs.get(this@SshVpnService)
                val status = _vpnStatus.value
                val idle = status == VpnStatus.IDLE_SUSPENDED
                delay(
                    when {
                        !isScreenOn && prefs.getBoolean(VpnPrefs.SCREEN_OFF_THROTTLING, true) -> 15_000L
                        idle -> 5_000L
                        prefs.getBoolean(VpnPrefs.AGGRESSIVE_BATTERY_SAVER, false) -> 3_000L
                        !isUiVisible -> 2_000L
                        else -> 1_000L
                    }
                )

                val now = System.currentTimeMillis()
                val seconds = ((now - lastTime).coerceAtLeast(1)) / 1000.0
                val totalIn = sessionBytesIn.get()
                val totalOut = sessionBytesOut.get()
                val speedIn = ((totalIn - lastIn) / seconds).toLong().coerceAtLeast(0)
                val speedOut = ((totalOut - lastOut) / seconds).toLong().coerceAtLeast(0)
                lastTime = now
                lastIn = totalIn
                lastOut = totalOut

                updateIdleState(prefs.getBoolean(VpnPrefs.SMART_BATTERY_SAVER, true), prefs.getInt(VpnPrefs.IDLE_TIMEOUT_SECONDS, VpnPrefs.DEFAULT_IDLE_TIMEOUT))

                val connected = _vpnStatus.value == VpnStatus.CONNECTED || _vpnStatus.value == VpnStatus.IDLE_SUSPENDED
                _vpnStats.update {
                    it.copy(
                        bytesIn = totalIn,
                        bytesOut = totalOut,
                        speedInBps = speedIn,
                        speedOutBps = speedOut,
                        connectedDurationSeconds = if (connected) (now - connectStartTime) / 1000 else 0,
                        isIdleSuspended = _vpnStatus.value == VpnStatus.IDLE_SUSPENDED
                    )
                }

                if (connected) {
                    val stats = _vpnStats.value
                    val title = if (_vpnStatus.value == VpnStatus.IDLE_SUSPENDED) getString(R.string.notif_connected_idle, profile.name) else getString(R.string.notif_connected_short, profile.name)
                    updateNotification(
                        title,
                        getString(R.string.notif_stats, stats.formatSpeed(speedIn), stats.formatSpeed(speedOut), stats.formatBytes(totalIn + totalOut))
                    )
                }
            }
        }
    }

    /** CONNECTED <-> IDLE_SUSPENDED: while idle, statistics refresh slower and latency checks pause. */
    private fun updateIdleState(smartIdle: Boolean, idleTimeoutSeconds: Int) {
        val lastActivity = maxOf(packetHandler?.lastActivityTime ?: 0L, lastSocksActivity.get())
        val idleFor = System.currentTimeMillis() - lastActivity
        val status = _vpnStatus.value
        if (status == VpnStatus.CONNECTED && smartIdle && idleFor > idleTimeoutSeconds.coerceAtLeast(10) * 1000L) {
            _vpnStatus.value = VpnStatus.IDLE_SUSPENDED
            log(getString(R.string.log_idle, (idleFor / 1000).toInt()))
        } else if (status == VpnStatus.IDLE_SUSPENDED && (!smartIdle || idleFor < 5_000)) {
            _vpnStatus.value = VpnStatus.CONNECTED
            log(getString(R.string.log_traffic_resumed))
        }
    }

    private fun startPingLoop(profile: SshServerProfile) {
        pingJob?.cancel()
        pingJob = serviceScope.launch {
            while (isActive) {
                // Each check is a TCP connect to the SSH port: only while the user looks at the numbers
                if (_vpnStatus.value == VpnStatus.CONNECTED && isUiVisible && isScreenOn) {
                    val ping = sshClient?.measurePing(profile.host, profile.port)
                    if (ping != null) {
                        _vpnStats.update { it.copy(pingMs = ping) }
                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(applicationContext).serverDao().updatePing(profile.id, ping)
                        }
                    }
                }
                delay(if (VpnPrefs.get(this@SshVpnService).getBoolean(VpnPrefs.AGGRESSIVE_BATTERY_SAVER, false)) 30_000L else 10_000L)
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // System callbacks
    // -------------------------------------------------------------------------------------------

    private fun registerScreenStateReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> isScreenOn = false
                    Intent.ACTION_SCREEN_ON -> isScreenOn = true
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (network != defaultNetwork) {
                    defaultNetwork = network
                    networkEvents.trySend(Unit)
                }
            }

            override fun onLost(network: Network) {
                if (network == defaultNetwork) defaultNetwork = null
            }
        }
        try {
            // Our own app is excluded from the VPN, so this tracks the underlying network
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            log(getString(R.string.log_network_monitor_unavailable, e.message.orEmpty()), level = LogLevel.WARN)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SshVpn:Connect")?.apply { acquire(30_000L) }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            wakeLock = null
        }
    }

    override fun onRevoke() {
        log(getString(R.string.log_revoked), level = LogLevel.WARN)
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignore
        }
        screenReceiver = null
        networkCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        releaseWakeLock()
        cancelSwitch()
        tunnelJob?.cancel()
        stopPipeline(closeTun = true)
        if (_vpnStatus.value != VpnStatus.ERROR) _vpnStatus.value = VpnStatus.DISCONNECTED
        _vpnStats.value = VpnStats()
        serviceScope.cancel()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String = getString(R.string.notif_tap), ongoing: Boolean = true): Notification {
        val openPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openPendingIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (ongoing) {
            val disconnectPendingIntent = PendingIntent.getService(
                this, 1,
                Intent(this, SshVpnService::class.java).apply { action = ACTION_DISCONNECT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_stat_vpn, getString(R.string.notif_action_disconnect), disconnectPendingIntent)
        } else {
            builder.setAutoCancel(true)
        }
        return builder.build()
    }

    private fun updateNotification(title: String, content: String = getString(R.string.notif_tap)) {
        if (tunnelJob == null) return
        val text = "$title|$content"
        if (text == lastNotificationText) return
        lastNotificationText = text
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }
}
