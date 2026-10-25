package com.sshtunnel.ui.screens

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sshtunnel.ui.util.tvFocusVisual
import com.sshtunnel.ui.util.tvCardFocusable
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sshtunnel.data.model.RoutingPolicy
import com.sshtunnel.data.model.VpnStatus
import com.sshtunnel.ui.MainViewModel
import com.sshtunnel.ui.theme.AmberWarning
import com.sshtunnel.ui.theme.CyanPrimary
import com.sshtunnel.ui.theme.DarkSurface
import com.sshtunnel.ui.theme.DarkSurfaceBorder
import com.sshtunnel.ui.theme.DarkSurfaceVariant
import com.sshtunnel.ui.theme.EmeraldSuccess
import com.sshtunnel.ui.theme.RedError
import com.sshtunnel.ui.theme.RobotoMonoFontFamily
import com.sshtunnel.ui.theme.TextMuted
import com.sshtunnel.ui.theme.TextPrimary
import com.sshtunnel.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val vpnStats by viewModel.vpnStats.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()
    val routingPolicy by viewModel.routingPolicy.collectAsState()
    val selectedAppCount by viewModel.selectedAppCount.collectAsState()
    val smartBatterySaver by viewModel.smartBatterySaver.collectAsState()

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn(context, activeServer?.id)
        }
    }

    val powerButtonFocusRequester = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(150)
        try {
            powerButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val isConnected = vpnStatus == VpnStatus.CONNECTED || vpnStatus == VpnStatus.IDLE_SUSPENDED
    val isConnecting = vpnStatus == VpnStatus.CONNECTING || vpnStatus == VpnStatus.RECONNECTING

    // Pulsing animation when connected
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            vpnStatus == VpnStatus.CONNECTED -> EmeraldSuccess
            vpnStatus == VpnStatus.IDLE_SUSPENDED -> AmberWarning
            isConnecting -> CyanPrimary
            vpnStatus == VpnStatus.ERROR -> RedError
            else -> CyanPrimary.copy(alpha = 0.85f)
        },
        label = "btnColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // Top Status Badge
        StatusBadge(vpnStatus = vpnStatus, durationSeconds = vpnStats.connectedDurationSeconds)

        // Master Power Connect Button
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow halo ring
            if (isConnected || isConnecting) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = 0.15f))
                )
            }

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                buttonColor.copy(alpha = 0.35f),
                                DarkSurface
                            )
                        )
                    )
                    .border(3.dp, buttonColor, CircleShape)
                    .focusRequester(powerButtonFocusRequester)
                    .tvCardFocusable(
                        shape = CircleShape,
                        focusBorderColor = CyanPrimary,
                        focusBorderWidth = 4.dp,
                        focusedScale = 1.08f
                    ) {
                        if (isConnected || isConnecting) {
                            viewModel.stopVpn(context)
                        } else {
                            if (activeServer == null) {
                                onNavigateToServers()
                            } else {
                                val prepareIntent = VpnService.prepare(context)
                                if (prepareIntent != null) {
                                    vpnPrepareLauncher.launch(prepareIntent)
                                } else {
                                    viewModel.startVpn(context, activeServer?.id)
                                }
                            }
                        }
                    }
                    .testTag("power_connect_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = CyanPrimary,
                            modifier = Modifier.size(46.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = if (isConnected) "Disconnect VPN" else "Connect VPN",
                            tint = buttonColor,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (vpnStatus) {
                            VpnStatus.CONNECTED -> "CONNECTED"
                            VpnStatus.IDLE_SUSPENDED -> "IDLE (PAUSED)"
                            VpnStatus.CONNECTING -> "CONNECTING"
                            VpnStatus.RECONNECTING -> "RETRYING"
                            VpnStatus.ERROR -> "RECONNECT"
                            else -> if (activeServer == null) "ADD SERVER" else "CONNECT"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = buttonColor,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }

        // Active Server Card
        ActiveServerCard(
            server = activeServer,
            pingMs = vpnStats.pingMs,
            onSwitchServer = onNavigateToServers
        )

        // Real-Time Telemetry Grid
        TelemetryGrid(
            stats = vpnStats,
            isConnected = isConnected
        )

        // Split Tunneling Status Card
        SplitTunnelingCard(
            routingPolicy = routingPolicy,
            selectedCount = selectedAppCount,
            onManageApps = onNavigateToApps
        )

        // Battery Saver Info Card
        if (smartBatterySaver) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Smart Battery Saver",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Smart Battery Saver Active",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (vpnStats.isIdleSuspended)
                                "SSH tunnel currently suspended while apps are idle. Resumes automatically on network activity."
                            else
                                "Stops SSH background keepalive when selected apps are idle to reduce battery drain.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(vpnStatus: VpnStatus, durationSeconds: Long) {
    val (statusText, badgeColor) = when (vpnStatus) {
        VpnStatus.CONNECTED -> "ACTIVE • ${formatDuration(durationSeconds)}" to EmeraldSuccess
        VpnStatus.IDLE_SUSPENDED -> "IDLE (BATTERY SAVER)" to AmberWarning
        VpnStatus.CONNECTING -> "CONNECTING TO SSH..." to CyanPrimary
        VpnStatus.RECONNECTING -> "RECONNECTING..." to AmberWarning
        VpnStatus.ERROR -> "CONNECTION FAILED" to RedError
        VpnStatus.DISCONNECTED -> "DISCONNECTED" to TextMuted
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(badgeColor.copy(alpha = 0.12f))
            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor
                )
            )
        }
    }
}

@Composable
private fun ActiveServerCard(
    server: com.sshtunnel.data.model.SshServerProfile?,
    pingMs: Long?,
    onSwitchServer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CyanPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server",
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = server?.name ?: "No Server Configured",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = if (server != null) "${server.username}@${server.host}:${server.port}" else "Tap to add SSH server",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                    if (pingMs != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val pingColor = when {
                                pingMs < 100 -> EmeraldSuccess
                                pingMs < 250 -> AmberWarning
                                else -> RedError
                            }
                            Text(
                                text = "Ping: ${pingMs}ms",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = pingColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onSwitchServer,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanPrimary),
                modifier = Modifier
                    .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                    .testTag("switch_server_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Switch",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (server == null) "Add" else "Switch", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TelemetryGrid(stats: com.sshtunnel.data.model.VpnStats, isConnected: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryCard(
                title = "DOWNLOAD",
                value = stats.formatSpeed(if (isConnected) stats.speedInBps else 0L),
                subValue = "Total: ${stats.formatBytes(stats.bytesIn)}",
                icon = Icons.Default.ArrowDownward,
                iconColor = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                title = "UPLOAD",
                value = stats.formatSpeed(if (isConnected) stats.speedOutBps else 0L),
                subValue = "Total: ${stats.formatBytes(stats.bytesOut)}",
                icon = Icons.Default.ArrowUpward,
                iconColor = EmeraldSuccess,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryCard(
                title = "LATENCY (PING)",
                value = if (stats.pingMs != null && isConnected) "${stats.pingMs} ms" else "-- ms",
                subValue = if (stats.pingMs != null) "Real-time SSH" else "Standby",
                icon = Icons.Default.Speed,
                iconColor = if (stats.pingMs != null && stats.pingMs < 120) EmeraldSuccess else AmberWarning,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                title = "TOTAL TRAFFIC",
                value = stats.formatBytes(stats.bytesIn + stats.bytesOut),
                subValue = "${stats.activeConnections} active sockets",
                icon = Icons.Default.Security,
                iconColor = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TelemetryCard(
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = RobotoMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
        }
    }
}

@Composable
private fun SplitTunnelingCard(
    routingPolicy: RoutingPolicy,
    selectedCount: Int,
    onManageApps: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Per-App Routing (Split Tunnel)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    val policyDesc = if (routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) {
                        "Only selected apps go through SSH (all others direct)"
                    } else {
                        "All traffic through SSH (except selected apps)"
                    }
                    Text(
                        text = policyDesc,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                    Text(
                        text = "$selectedCount app(s) configured",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (selectedCount == 0 && routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) AmberWarning else CyanPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (selectedCount == 0 && routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) {
                        Text(
                            text = "No apps selected. Tap to choose apps to route via SSH.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AmberWarning,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Button(
                    onClick = onManageApps,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF001F28)
                    ),
                    modifier = Modifier
                        .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                        .testTag("manage_apps_button")
                ) {
                    Text("Select Apps", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
