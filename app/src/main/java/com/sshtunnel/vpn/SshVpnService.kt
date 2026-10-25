package com.sshtunnel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.sshtunnel.MainActivity
import com.sshtunnel.R
import com.sshtunnel.data.local.AppDatabase
import com.sshtunnel.data.model.ConnectionLogEntry
import com.sshtunnel.data.model.LogLevel
import com.sshtunnel.data.model.RoutingPolicy
import com.sshtunnel.data.model.SshServerProfile
import com.sshtunnel.data.model.VpnStats
import com.sshtunnel.data.model.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class SshVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var sshClient: SshTunnelClient? = null
    private var socksServer: Socks5ProxyServer? = null
    private var packetHandler: VpnTunPacketHandler? = null

    private var statsJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var isUserInitiatedStop = false

    private var isScreenOn = true
    private var screenReceiver: BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val sessionBytesIn = AtomicLong(0)
    private val sessionBytesOut = AtomicLong(0)
    private var lastSpeedCheckTime = 0L
    private var lastBytesInSnapshot = 0L
    private var lastBytesOutSnapshot = 0L
    private var connectStartTime = 0L

    companion object {
        const val ACTION_CONNECT = "com.rootu.ssh.tunnel.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.rootu.ssh.tunnel.vpn.ACTION_DISCONNECT"
        const val EXTRA_SERVER_ID = "extra_server_id"

        private const val NOTIFICATION_ID = 2024
        private const val CHANNEL_ID = "ssh_vpn_service_channel"

        private val _vpnStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
        val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()

        private val _vpnStats = MutableStateFlow(VpnStats())
        val vpnStats: StateFlow<VpnStats> = _vpnStats.asStateFlow()

        private val _activeServer = MutableStateFlow<SshServerProfile?>(null)
        val activeServer: StateFlow<SshServerProfile?> = _activeServer.asStateFlow()

        private val _logs = MutableStateFlow<List<ConnectionLogEntry>>(emptyList())
        val logs: StateFlow<List<ConnectionLogEntry>> = _logs.asStateFlow()

        fun log(message: String, isError: Boolean = false, level: LogLevel? = null) {
            val actualLevel = level ?: if (isError) LogLevel.ERROR else LogLevel.INFO
            val entry = ConnectionLogEntry(
                timestamp = System.currentTimeMillis(),
                level = actualLevel,
                message = message
            )
            val current = _logs.value.toMutableList()
            if (current.size > 200) {
                current.removeAt(0)
            }
            current.add(entry)
            _logs.value = current
        }

        fun clearLogs() {
            _logs.value = emptyList()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenStateReceiver()
    }

    private fun registerScreenStateReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        log("Screen OFF: Engaging battery saver (reducing stats refresh & pausing latency pings).", level = LogLevel.INFO)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        log("Screen ON: Resuming normal active telemetry.", level = LogLevel.INFO)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun acquireTemporaryWakeLock(timeoutMs: Long = 25000L) {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SshVpn:HandshakeLock")
            wakeLock?.acquire(timeoutMs)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            wakeLock = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_CONNECT -> {
                isUserInitiatedStop = false
                // Call startForeground immediately to comply with Android 8+ requirements
                startForeground(NOTIFICATION_ID, buildNotification("Starting SSH Tunnel..."))
                val serverId = intent.getLongExtra(EXTRA_SERVER_ID, -1L)
                startVpn(serverId)
            }
            ACTION_DISCONNECT -> {
                isUserInitiatedStop = true
                stopVpn()
            }
            else -> {
                // If started without explicit action (e.g. Always-on VPN or system restart)
                val prefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
                val autoStart = prefs.getBoolean("auto_start_on_boot", false)
                if (autoStart) {
                    startForeground(NOTIFICATION_ID, buildNotification("Auto-connecting Always-on VPN..."))
                    startVpn(-1L)
                }
            }
        }

        // START_STICKY ensures Android OS restarts the service if it gets killed in low memory
        return START_STICKY
    }

    private fun startVpn(serverId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val serverProfile = if (serverId > 0) {
                db.serverDao().getServerById(serverId)
            } else {
                db.serverDao().getActiveServerSync()
            }

            if (serverProfile == null) {
                log("No SSH server profile found. Please add an SSH server first.", isError = true)
                _vpnStatus.value = VpnStatus.ERROR
                updateNotification("No SSH server profile configured")
                delay(2000)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            _activeServer.value = serverProfile
            connectAndStartPipeline(serverProfile)
        }
    }

    private suspend fun connectAndStartPipeline(serverProfile: SshServerProfile) {
        _vpnStatus.value = VpnStatus.CONNECTING
        acquireTemporaryWakeLock(25000L)
        log("Starting VPN tunnel for server: ${serverProfile.name} (${serverProfile.host}:${serverProfile.port})")
        updateNotification("Connecting to ${serverProfile.name}...")

        // Stop existing pipeline components cleanly
        statsJob?.cancel()
        pingJob?.cancel()
        packetHandler?.stop()
        socksServer?.stop()
        sshClient?.disconnect()
        runCatching { vpnInterface?.close() }

        val sharedPrefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
        val aggressiveBatterySaver = sharedPrefs.getBoolean("aggressive_battery_saver", false)
        val effectiveKeepAlive = if (aggressiveBatterySaver) {
            serverProfile.keepAliveIntervalSeconds.coerceAtLeast(60)
        } else {
            serverProfile.keepAliveIntervalSeconds
        }
        val tunedProfile = serverProfile.copy(keepAliveIntervalSeconds = effectiveKeepAlive)

        // Connect SSH with protected socket
        val client = SshTunnelClient(this@SshVpnService) { msg, isErr ->
            log(msg, isErr)
        }
        sshClient = client

        val connectResult = client.connect(tunedProfile)
        releaseWakeLock()
        if (connectResult.isFailure) {
            val err = connectResult.exceptionOrNull()?.message ?: "Connection failed"
            log("Failed to connect to SSH server: $err", isError = true)
            _vpnStatus.value = VpnStatus.ERROR
            handleAutoReconnect(serverProfile)
            return
        }

        // Start SOCKS5 proxy server
        socksServer = Socks5ProxyServer(
            port = serverProfile.socksPort,
            sshClient = client,
            vpnService = this@SshVpnService,
            onBytesTransferred = { up, down ->
                sessionBytesOut.addAndGet(up)
                sessionBytesIn.addAndGet(down)
            },
            onConnectionChange = { count ->
                _vpnStats.value = _vpnStats.value.copy(activeConnections = count)
            },
            onActivity = {
                if (_vpnStats.value.isIdleSuspended) {
                    _vpnStats.value = _vpnStats.value.copy(isIdleSuspended = false)
                    if (_vpnStatus.value == VpnStatus.IDLE_SUSPENDED) {
                        _vpnStatus.value = VpnStatus.CONNECTED
                    }
                }
            },
            onLog = { msg, isErr -> log(msg, isErr) }
        ).apply {
            start(serviceScope)
        }

        // Establish TUN Interface with per-app split tunneling
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val selectedApps = db.appRuleDao().getSelectedPackageNames().filterNot { it == packageName }

            val sharedPrefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
            val policyName = sharedPrefs.getString("routing_policy", RoutingPolicy.ROUTE_ONLY_SELECTED.name)
            val policy = runCatching { RoutingPolicy.valueOf(policyName!!) }.getOrDefault(RoutingPolicy.ROUTE_ONLY_SELECTED)
            val dnsServer = sharedPrefs.getString("dns_server", "1.1.1.1") ?: "1.1.1.1"

            val builder = Builder()
                .setSession("SSH Tunnel (${serverProfile.name})")
                .setMtu(1500)
                .addAddress("10.240.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dnsServer)

            // Prevent IPv6 leaks
            try {
                builder.addAddress("fd00::1", 64)
                builder.addRoute("::", 0)
            } catch (e: Exception) {
                // Ignore if device does not support IPv6 routing in TUN
            }

            if (policy == RoutingPolicy.ROUTE_ONLY_SELECTED) {
                if (selectedApps.isEmpty()) {
                    log("No apps selected for routing. Please select applications in the Apps tab.", isError = true)
                    _vpnStatus.value = VpnStatus.ERROR
                    updateNotification("Error: No apps selected for tunnel")
                    delay(3000)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }
                log("Split tunneling: Routing traffic exclusively for ${selectedApps.size} selected app(s)")
                for (pkg in selectedApps) {
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (e: Exception) {
                        log("Could not add allowed app $pkg: ${e.message}", isError = false)
                    }
                }
            } else {
                log("Split tunneling: Routing all traffic EXCEPT ${selectedApps.size} excluded app(s)")
                for (pkg in selectedApps) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    // Ignore
                }
            }

            val pfd = builder.establish()
            if (pfd == null) {
                log("Failed to establish TUN interface.", isError = true)
                _vpnStatus.value = VpnStatus.ERROR
                return
            }
            vpnInterface = pfd

            // Start packet handler
            val smartIdle = sharedPrefs.getBoolean("smart_battery_saver", true)
            val idleTimeout = sharedPrefs.getInt("idle_timeout_seconds", 60)

            packetHandler = VpnTunPacketHandler(
                vpnInterface = pfd,
                socksPort = serverProfile.socksPort,
                dnsServerIp = dnsServer,
                vpnService = this@SshVpnService,
                onPacketActivity = {
                    // Handled internally
                },
                onBytesTransferred = { up, down ->
                    sessionBytesOut.addAndGet(up)
                    sessionBytesIn.addAndGet(down)
                },
                onIdleStateChanged = { suspended ->
                    _vpnStats.value = _vpnStats.value.copy(isIdleSuspended = suspended)
                    if (suspended) {
                        _vpnStatus.value = VpnStatus.IDLE_SUSPENDED
                        updateNotification("SSH Tunnel Idle (Battery Saver Active)")
                    } else {
                        _vpnStatus.value = VpnStatus.CONNECTED
                        updateNotification("SSH Tunnel Connected to ${serverProfile.name}")
                    }
                },
                onLog = { msg, isErr -> log(msg, isErr) }
            ).apply {
                start(serviceScope, idleTimeoutSeconds = idleTimeout, smartIdleEnabled = smartIdle)
            }

            connectStartTime = System.currentTimeMillis()
            lastSpeedCheckTime = System.currentTimeMillis()
            lastBytesInSnapshot = sessionBytesIn.get()
            lastBytesOutSnapshot = sessionBytesOut.get()

            _vpnStatus.value = VpnStatus.CONNECTED
            log("VPN Tunnel is fully ACTIVE and routing TCP/IP via SSH SOCKS5!", isError = false, level = LogLevel.SUCCESS)
            updateNotification("Connected to ${serverProfile.name}")

            startPeriodicStatsAndPing(serverProfile)

        } catch (e: Exception) {
            log("Failed to initialize VPN TUN: ${e.message}", isError = true)
            _vpnStatus.value = VpnStatus.ERROR
            handleAutoReconnect(serverProfile)
        }
    }

    private fun startPeriodicStatsAndPing(profile: SshServerProfile) {
        statsJob?.cancel()
        statsJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && (_vpnStatus.value == VpnStatus.CONNECTED || _vpnStatus.value == VpnStatus.IDLE_SUSPENDED)) {
                val prefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
                val screenOffThrottling = prefs.getBoolean("screen_off_throttling", true)
                val aggressiveBatterySaver = prefs.getBoolean("aggressive_battery_saver", false)

                val loopDelay = if (!isScreenOn && screenOffThrottling) {
                    15000L
                } else if (aggressiveBatterySaver) {
                    3000L
                } else {
                    1000L
                }
                delay(loopDelay)

                val now = System.currentTimeMillis()
                val deltaSec = ((now - lastSpeedCheckTime).coerceAtLeast(1000)) / 1000.0

                val currentIn = sessionBytesIn.get()
                val currentOut = sessionBytesOut.get()

                val speedIn = (((currentIn - lastBytesInSnapshot) / deltaSec)).toLong().coerceAtLeast(0)
                val speedOut = (((currentOut - lastBytesOutSnapshot) / deltaSec)).toLong().coerceAtLeast(0)

                lastSpeedCheckTime = now
                lastBytesInSnapshot = currentIn
                lastBytesOutSnapshot = currentOut

                val duration = (now - connectStartTime) / 1000

                _vpnStats.value = _vpnStats.value.copy(
                    bytesIn = currentIn,
                    bytesOut = currentOut,
                    speedInBps = speedIn,
                    speedOutBps = speedOut,
                    connectedDurationSeconds = duration
                )

                val stats = _vpnStats.value
                val speedText = "↓ ${stats.formatSpeed(speedIn)}  ↑ ${stats.formatSpeed(speedOut)} • Total: ${stats.formatBytes(currentIn + currentOut)}"
                updateNotification("Connected: ${profile.name}", speedText)

                // Detect unexpected SSH session drops in both connected and idle states
                if (sshClient?.isSessionActive() == false && !isUserInitiatedStop &&
                    (_vpnStatus.value == VpnStatus.CONNECTED || _vpnStatus.value == VpnStatus.IDLE_SUSPENDED)
                ) {
                    log("SSH session lost unexpectedly. Triggering auto-reconnect...", isError = true)
                    _vpnStatus.value = VpnStatus.RECONNECTING
                    handleAutoReconnect(profile)
                    break
                }
            }
        }

        pingJob?.cancel()
        pingJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && (_vpnStatus.value == VpnStatus.CONNECTED || _vpnStatus.value == VpnStatus.IDLE_SUSPENDED)) {
                val prefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
                val screenOffThrottling = prefs.getBoolean("screen_off_throttling", true)
                val aggressiveBatterySaver = prefs.getBoolean("aggressive_battery_saver", false)

                // When screen is OFF and throttling is active, do not ping modem/network
                if (isScreenOn || !screenOffThrottling) {
                    val ping = sshClient?.measurePing(profile.host, profile.port)
                    if (ping != null) {
                        _vpnStats.value = _vpnStats.value.copy(pingMs = ping)
                        val db = AppDatabase.getInstance(applicationContext)
                        db.serverDao().updatePing(profile.id, ping)
                    }
                }

                val pingDelay = if (!isScreenOn && screenOffThrottling) 30000L else if (aggressiveBatterySaver) 30000L else 8000L
                delay(pingDelay)
            }
        }
    }

    private fun handleAutoReconnect(profile: SshServerProfile) {
        if (isUserInitiatedStop) return
        val sharedPrefs = getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
        val autoReconnect = sharedPrefs.getBoolean("auto_reconnect", true)
        if (!autoReconnect) {
            _vpnStatus.value = VpnStatus.DISCONNECTED
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch(Dispatchers.IO) {
            _vpnStatus.value = VpnStatus.RECONNECTING
            var delayMs = 3000L
            for (attempt in 1..5) {
                if (isUserInitiatedStop) break
                log("Auto-reconnect attempt $attempt/5 in ${delayMs / 1000}s...", isError = false, level = LogLevel.WARN)
                updateNotification("Reconnecting in ${delayMs / 1000}s (attempt $attempt)...")
                delay(delayMs)
                if (isUserInitiatedStop) break

                log("Restoring SSH tunnel pipeline...", false)
                connectAndStartPipeline(profile)
                if (_vpnStatus.value == VpnStatus.CONNECTED) {
                    log("Reconnected successfully to ${profile.host}!", level = LogLevel.SUCCESS)
                    return@launch
                }
                delayMs = (delayMs * 2).coerceAtMost(30000L)
            }
            log("Auto-reconnect failed after multiple attempts.", isError = true)
            stopVpn()
        }
    }

    override fun onRevoke() {
        log("VPN permission revoked by Android system.")
        stopVpn()
        super.onRevoke()
    }

    private fun stopVpn() {
        log("Stopping VPN Tunnel and closing SSH connections...")
        statsJob?.cancel()
        statsJob = null
        pingJob?.cancel()
        pingJob = null
        reconnectJob?.cancel()
        reconnectJob = null

        packetHandler?.stop()
        packetHandler = null

        socksServer?.stop()
        socksServer = null

        sshClient?.disconnect()
        sshClient = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            // Ignore
        }
        vpnInterface = null

        _vpnStatus.value = VpnStatus.DISCONNECTED
        _vpnStats.value = VpnStats()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SSH VPN Tunnel Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live connection status, speed, and statistics for the SSH VPN Tunnel"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String = "Tap to open"): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, SshVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_stat_vpn, "Disconnect", disconnectPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String = "Tap to open") {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignore
        }
        screenReceiver = null
        releaseWakeLock()
        stopVpn()
        super.onDestroy()
    }
}
