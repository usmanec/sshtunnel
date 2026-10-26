package top.rootu.ssh.tunnel.ui.screens

import android.content.ActivityNotFoundException
import android.net.VpnService
import android.widget.Toast
import top.rootu.ssh.tunnel.vpn.SshVpnService
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
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.res.stringResource
import top.rootu.ssh.tunnel.R
import top.rootu.ssh.tunnel.ui.util.tvFocusVisual
import top.rootu.ssh.tunnel.ui.util.tvCardFocusable
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.rootu.ssh.tunnel.data.model.LogLevel
import top.rootu.ssh.tunnel.data.model.RoutingPolicy
import top.rootu.ssh.tunnel.ui.components.ReconnectBanner
import androidx.compose.ui.text.style.TextAlign
import top.rootu.ssh.tunnel.data.model.VpnStatus
import top.rootu.ssh.tunnel.ui.MainViewModel
import top.rootu.ssh.tunnel.ui.theme.AmberWarning
import top.rootu.ssh.tunnel.ui.theme.CyanPrimary
import top.rootu.ssh.tunnel.ui.theme.DarkSurface
import top.rootu.ssh.tunnel.ui.theme.DarkSurfaceBorder
import top.rootu.ssh.tunnel.ui.theme.DarkSurfaceVariant
import top.rootu.ssh.tunnel.ui.theme.EmeraldSuccess
import top.rootu.ssh.tunnel.ui.theme.RedError
import top.rootu.ssh.tunnel.ui.theme.RobotoMonoFontFamily
import top.rootu.ssh.tunnel.ui.theme.TextMuted
import top.rootu.ssh.tunnel.ui.theme.TextPrimary
import top.rootu.ssh.tunnel.ui.theme.TextSecondary

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
    val logs by viewModel.logs.collectAsState()
    val externalIp by viewModel.externalIp.collectAsState()

    // Re-check when the path changes: direct <-> tunnel, or another server
    androidx.compose.runtime.LaunchedEffect(vpnStatus) {
        viewModel.refreshExternalIp()
    }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn(context, activeServer?.id)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_permission_denied), Toast.LENGTH_LONG).show()
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
            vpnStatus == VpnStatus.IDLE_SUSPENDED -> EmeraldSuccess
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

        ReconnectBanner(viewModel)

        // Top Status Badge
        StatusBadge(vpnStatus = vpnStatus, durationSeconds = vpnStats.connectedDurationSeconds)

        // Explain failures right here: on a TV the notification shade is not visible
        if (vpnStatus == VpnStatus.ERROR || vpnStatus == VpnStatus.RECONNECTING) {
            val lastError = logs.lastOrNull { it.level == LogLevel.ERROR || it.level == LogLevel.WARN }
            if (lastError != null) {
                Text(
                    text = lastError.message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (vpnStatus == VpnStatus.ERROR) RedError else AmberWarning
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 3
                )
            }
        }

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
                                    try {
                                        vpnPrepareLauncher.launch(prepareIntent)
                                    } catch (e: ActivityNotFoundException) {
                                        // Some Android TV firmwares ship without the VPN consent dialog
                                        val hint = context.getString(R.string.tv_no_permission_dialog, context.packageName)
                                        SshVpnService.log(hint, isError = true)
                                        Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
                                    }
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
                            contentDescription = stringResource(if (isConnected) R.string.cd_disconnect else R.string.cd_connect),
                            tint = buttonColor,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            when (vpnStatus) {
                                VpnStatus.CONNECTED, VpnStatus.IDLE_SUSPENDED -> R.string.power_connected
                                VpnStatus.CONNECTING -> R.string.power_connecting
                                VpnStatus.RECONNECTING -> R.string.power_retrying
                                VpnStatus.ERROR -> R.string.power_reconnect
                                else -> if (activeServer == null) R.string.power_add_server else R.string.power_connect
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = buttonColor,
                            letterSpacing = 0.5.sp
                        ),
                        // Long translations ("ДОБАВИТЬ СЕРВЕР") wrap inside the circle instead of being cut
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 14.dp)
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

        // External IP and location (exit address of the tunnel while connected)
        ExternalIpCard(
            state = externalIp,
            onRefresh = { viewModel.refreshExternalIp(force = true) }
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

        // Idle mode info
        if (smartBatterySaver && vpnStats.isIdleSuspended) {
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
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.idle_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = stringResource(R.string.idle_text),
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
        VpnStatus.CONNECTED -> stringResource(R.string.status_active, formatDuration(durationSeconds)) to EmeraldSuccess
        VpnStatus.IDLE_SUSPENDED -> stringResource(R.string.status_active_idle, formatDuration(durationSeconds)) to EmeraldSuccess
        VpnStatus.CONNECTING -> stringResource(R.string.status_connecting) to CyanPrimary
        VpnStatus.RECONNECTING -> stringResource(R.string.status_reconnecting) to AmberWarning
        VpnStatus.ERROR -> stringResource(R.string.status_error) to RedError
        VpnStatus.DISCONNECTED -> stringResource(R.string.status_disconnected) to TextMuted
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
    server: top.rootu.ssh.tunnel.data.model.SshServerProfile?,
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
                        contentDescription = stringResource(R.string.cd_server),
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = server?.name ?: stringResource(R.string.no_server),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = if (server != null) "${server.username}@${server.host}:${server.port}" else stringResource(R.string.no_server_hint),
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
                                text = stringResource(R.string.ping_value, pingMs),
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
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(if (server == null) R.string.btn_add else R.string.btn_switch), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TelemetryGrid(stats: top.rootu.ssh.tunnel.data.model.VpnStats, isConnected: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryCard(
                title = stringResource(R.string.telemetry_download),
                value = stats.formatSpeed(if (isConnected) stats.speedInBps else 0L),
                subValue = stringResource(R.string.telemetry_total, stats.formatBytes(stats.bytesIn)),
                icon = Icons.Default.ArrowDownward,
                iconColor = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                title = stringResource(R.string.telemetry_upload),
                value = stats.formatSpeed(if (isConnected) stats.speedOutBps else 0L),
                subValue = stringResource(R.string.telemetry_total, stats.formatBytes(stats.bytesOut)),
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
                title = stringResource(R.string.telemetry_latency),
                value = if (stats.pingMs != null && isConnected) stringResource(R.string.unit_ms, stats.pingMs) else stringResource(R.string.latency_none),
                subValue = stringResource(if (stats.pingMs != null) R.string.latency_live else R.string.latency_standby),
                icon = Icons.Default.Speed,
                iconColor = if (stats.pingMs != null && stats.pingMs < 120) EmeraldSuccess else AmberWarning,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                title = stringResource(R.string.telemetry_total_traffic),
                value = stats.formatBytes(stats.bytesIn + stats.bytesOut),
                subValue = stringResource(R.string.active_sockets, stats.activeConnections),
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
                        text = stringResource(R.string.split_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    val policyDesc = if (routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) {
                        stringResource(R.string.split_only_selected)
                    } else {
                        stringResource(R.string.split_all_except)
                    }
                    Text(
                        text = policyDesc,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                    Text(
                        text = stringResource(R.string.apps_configured, selectedCount),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (selectedCount == 0 && routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) AmberWarning else CyanPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (selectedCount == 0 && routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) {
                        Text(
                            text = stringResource(R.string.no_apps_hint),
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
                    Text(stringResource(R.string.btn_select_apps), style = MaterialTheme.typography.labelLarge, maxLines = 1)
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

@Composable
private fun ExternalIpCard(
    state: top.rootu.ssh.tunnel.ui.MainViewModel.ExternalIpState,
    onRefresh: () -> Unit
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background((if (state.viaTunnel) EmeraldSuccess else AmberWarning).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = if (state.viaTunnel) EmeraldSuccess else AmberWarning,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ext_ip_title),
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, letterSpacing = 0.5.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (state.viaTunnel) R.string.ext_ip_via_tunnel else R.string.ext_ip_direct),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (state.viaTunnel) EmeraldSuccess else AmberWarning,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                val info = state.info
                when {
                    info != null -> {
                        Text(
                            text = info.ip,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = RobotoMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        val place = listOfNotNull(info.city, info.country).joinToString(", ")
                        Text(
                            text = place.ifEmpty { stringResource(R.string.ext_ip_unknown_location) } +
                                (info.countryCode?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                    state.loading -> Text(
                        text = stringResource(R.string.ext_ip_loading),
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                    state.failed -> Text(
                        text = stringResource(R.string.ext_ip_failed),
                        style = MaterialTheme.typography.bodyMedium.copy(color = RedError)
                    )
                }
            }
            if (state.loading) {
                CircularProgressIndicator(
                    color = CyanPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            } else {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .tvFocusVisual(shape = CircleShape)
                        .testTag("refresh_external_ip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_ip),
                        tint = CyanPrimary
                    )
                }
            }
        }
    }
}
