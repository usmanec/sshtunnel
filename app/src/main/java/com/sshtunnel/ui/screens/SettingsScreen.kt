package com.sshtunnel.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sshtunnel.ui.MainViewModel
import com.sshtunnel.ui.theme.CyanPrimary
import com.sshtunnel.ui.theme.DarkSurface
import com.sshtunnel.ui.theme.DarkSurfaceBorder
import com.sshtunnel.ui.theme.DarkSurfaceVariant
import com.sshtunnel.ui.theme.EmeraldSuccess
import com.sshtunnel.ui.theme.RobotoMonoFontFamily
import com.sshtunnel.ui.theme.TextMuted
import com.sshtunnel.ui.theme.TextPrimary
import com.sshtunnel.ui.theme.TextSecondary
import com.sshtunnel.ui.util.OemBatteryOptimizationHelper
import com.sshtunnel.ui.util.tvFocusVisual

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val autoReconnect by viewModel.autoReconnect.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val smartBatterySaver by viewModel.smartBatterySaver.collectAsState()
    val screenOffThrottling by viewModel.screenOffThrottling.collectAsState()
    val aggressiveBatterySaver by viewModel.aggressiveBatterySaver.collectAsState()
    val idleTimeoutSeconds by viewModel.idleTimeoutSeconds.collectAsState()
    val dnsServer by viewModel.dnsServer.collectAsState()
    val isolateDnsToSelectedApps by viewModel.isolateDnsToSelectedApps.collectAsState()

    var dnsInput by remember(dnsServer) { mutableStateOf(dnsServer) }
    var showOemGuideDialog by remember { mutableStateOf(false) }
    var isBatteryOptimizedIgnored by remember {
        mutableStateOf(OemBatteryOptimizationHelper.isBatteryOptimizationIgnored(context))
    }

    val detectedBrand = remember { OemBatteryOptimizationHelper.getDetectedBrand() }

    // Periodically re-check battery exemption when returning to screen
    LaunchedEffect(Unit) {
        isBatteryOptimizedIgnored = OemBatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ==========================================
        // 1. BATTERY SAVING & CPU THROTTLING
        // ==========================================
        Text(
            text = "BATTERY SAVING & RESOURCE CONTROL",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Smart Battery Saver (idle suspension)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
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
                                text = "Smart Tunnel Suspension",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Suspend tunnel keepalive when selected apps are idle to save battery",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                        }
                    }

                    Switch(
                        checked = smartBatterySaver,
                        onCheckedChange = { viewModel.setSmartBatterySaver(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary),
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(16.dp))
                            .testTag("battery_saver_switch")
                    )
                }

                if (smartBatterySaver) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Idle timeout before suspension:",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 60, 120, 300).forEach { seconds ->
                            FilterChip(
                                selected = idleTimeoutSeconds == seconds,
                                onClick = { viewModel.setIdleTimeoutSeconds(seconds) },
                                label = { Text("${seconds}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = CyanPrimary
                                ),
                                modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Screen-Off Power Throttling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen-Off Throttling",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Slow down stats updates to 15s and pause network pings when phone screen is locked/off",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }

                    Switch(
                        checked = screenOffThrottling,
                        onCheckedChange = { viewModel.setScreenOffThrottling(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary),
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(16.dp))
                            .testTag("screen_off_throttling_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Aggressive Radio Saver
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aggressive Radio Saver (60s+ keepalive)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Lengthens SSH keepalive to 60s to prevent waking up mobile/Wi-Fi modem radios",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }

                    Switch(
                        checked = aggressiveBatterySaver,
                        onCheckedChange = { viewModel.setAggressiveBatterySaver(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary),
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(16.dp))
                            .testTag("aggressive_battery_saver_switch")
                    )
                }
            }
        }

        // ==========================================
        // 2. OEM / XIAOMI ANTI-KILL PROTECTION
        // ==========================================
        Text(
            text = "OEM BACKGROUND PROTECTION & ANTI-KILL",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isBatteryOptimizedIgnored) DarkSurfaceBorder else androidx.compose.ui.graphics.Color(0xFFE5A93C).copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isBatteryOptimizedIgnored) EmeraldSuccess else androidx.compose.ui.graphics.Color(0xFFE5A93C),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device Protection: ${detectedBrand.displayName}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isBatteryOptimizedIgnored) {
                                "Battery optimizations disabled (Service protected from sleep)"
                            } else {
                                "Battery optimizations active (MIUI / OS may kill VPN in background)"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isBatteryOptimizedIgnored) EmeraldSuccess else androidx.compose.ui.graphics.Color(0xFFE5A93C)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Aggressive power managers (especially Xiaomi MIUI/HyperOS, Huawei, and Samsung) kill background VPN services when the screen locks unless explicitly exempted.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            OemBatteryOptimizationHelper.requestIgnoreBatteryOptimization(context)
                            isBatteryOptimizedIgnored = OemBatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBatteryOptimizedIgnored) DarkSurfaceVariant else CyanPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                            .testTag("request_battery_exemption_button")
                    ) {
                        Text(
                            text = if (isBatteryOptimizedIgnored) "Re-check Exemption" else "Disable Optimization",
                            color = if (isBatteryOptimizedIgnored) TextPrimary else androidx.compose.ui.graphics.Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            OemBatteryOptimizationHelper.openOemAutostartSettings(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                            .testTag("open_autostart_settings_button")
                    ) {
                        Text(
                            text = "Autostart Settings",
                            color = CyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showOemGuideDialog = true },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "View Step-by-Step ${detectedBrand.displayName} Guide",
                        color = CyanPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ==========================================
        // 3. RELIABILITY & AUTOMATION
        // ==========================================
        Text(
            text = "RELIABILITY & AUTOMATION",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Auto-Reconnect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Loop,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Auto-Reconnect",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Automatically restore tunnel on network disconnect or packet loss",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                        }
                    }

                    Switch(
                        checked = autoReconnect,
                        onCheckedChange = { viewModel.setAutoReconnect(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary),
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(16.dp))
                            .testTag("auto_reconnect_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-Start on Boot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Auto-Start on Boot",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Automatically connect VPN when device or Android TV boots up",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                        }
                    }

                    Switch(
                        checked = autoStartOnBoot,
                        onCheckedChange = { viewModel.setAutoStartOnBoot(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary),
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(16.dp))
                            .testTag("auto_start_boot_switch")
                    )
                }
            }
        }

        // ==========================================
        // 4. NETWORK & PER-APP DNS ISOLATION
        // ==========================================
        Text(
            text = "NETWORK & PER-APP DNS ISOLATION",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Strict DNS Isolation Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strict Per-App DNS Isolation",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "DNS queries go through VPN strictly for selected apps. Other applications use default local network DNS and cannot detect foreign VPN presence.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }

                    Switch(
                        checked = isolateDnsToSelectedApps,
                        onCheckedChange = { viewModel.setIsolateDnsToSelectedApps(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary),
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(16.dp))
                            .testTag("isolate_dns_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TUN DNS Server",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "DNS queries from selected apps will resolve through this address via SOCKS5 TCP.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = dnsInput,
                    onValueChange = {
                        dnsInput = it
                        if (it.isNotBlank()) {
                            viewModel.setDnsServer(it.trim())
                        }
                    },
                    label = { Text("DNS Server IP") },
                    placeholder = { Text("1.1.1.1") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                        .testTag("dns_server_input")
                )
            }
        }

        // ==========================================
        // 5. SOCKS5 TUNNEL INFO
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SSH Dynamic Forwarding (-D)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This app establishes an SSH session equivalent to:\nssh -D 10808 user@rootu.ssh.tunnel.com\n\nIn addition to the VPN TUN interface routing selected apps, local applications supporting SOCKS5 can connect directly to 127.0.0.1:10808.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = RobotoMonoFontFamily,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                )
            }
        }

        // ==========================================
        // 6. ANDROID TV & ALWAYS-ON VPN GUIDE
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Android TV / Google TV & Remote D-Pad",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• D-Pad Remote: Navigate with the arrow keys. Focused items glow with a cyan border. Press Center/OK to click or toggle.\n• Auto-Connect on Boot: Enable above so your TV box connects automatically on startup.\n• Always-On VPN: In Android Settings -> Network & Internet -> VPN -> SSH Tunnel -> enable 'Always-on VPN' so network requests automatically start the tunnel.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // OEM Instructions Dialog
    if (showOemGuideDialog) {
        val instructions = remember(detectedBrand) {
            OemBatteryOptimizationHelper.getOemInstructions(detectedBrand)
        }

        AlertDialog(
            onDismissRequest = { showOemGuideDialog = false },
            title = {
                Text(
                    text = "${detectedBrand.displayName} Background Setup",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To guarantee the VPN runs continuously in the background without being closed by ${detectedBrand.displayName}:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                    instructions.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOemGuideDialog = false
                        OemBatteryOptimizationHelper.openOemAutostartSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
                ) {
                    Text("Open Settings", color = androidx.compose.ui.graphics.Color.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOemGuideDialog = false },
                    modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
                ) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
