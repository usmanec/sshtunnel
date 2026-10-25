package com.sshtunnel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sshtunnel.data.repository.VpnPrefs
import com.sshtunnel.ui.MainViewModel
import com.sshtunnel.ui.components.ReconnectBanner
import com.sshtunnel.ui.components.SettingSwitchRow
import com.sshtunnel.ui.components.TvTextField
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
import com.sshtunnel.ui.util.OemBatteryOptimizationHelper
import com.sshtunnel.ui.util.rememberIsTv
import com.sshtunnel.ui.util.tvFocusVisual

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = rememberIsTv()
    val autoReconnect by viewModel.autoReconnect.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val smartBatterySaver by viewModel.smartBatterySaver.collectAsState()
    val screenOffThrottling by viewModel.screenOffThrottling.collectAsState()
    val aggressiveBatterySaver by viewModel.aggressiveBatterySaver.collectAsState()
    val idleTimeoutSeconds by viewModel.idleTimeoutSeconds.collectAsState()
    val dnsServer by viewModel.dnsServer.collectAsState()
    val bypassLan by viewModel.bypassLan.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()

    var showOemGuideDialog by remember { mutableStateOf(false) }
    var isBatteryOptimizedIgnored by remember {
        mutableStateOf(OemBatteryOptimizationHelper.isBatteryOptimizationIgnored(context))
    }
    val detectedBrand = remember { OemBatteryOptimizationHelper.getDetectedBrand() }

    // Re-check the battery exemption every time the user comes back from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isBatteryOptimizedIgnored = OemBatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReconnectBanner(viewModel)

        // ==========================================
        // 1. RELIABILITY & AUTOMATION
        // ==========================================
        SectionTitle("RELIABILITY & AUTOMATION")
        SettingsCard {
            SettingSwitchRow(
                title = "Auto-Reconnect",
                description = "Restore the tunnel automatically after network changes or SSH drops. Routed apps stay blocked while reconnecting, so nothing leaks.",
                checked = autoReconnect,
                onCheckedChange = { viewModel.setAutoReconnect(it) },
                icon = Icons.Default.Loop,
                modifier = Modifier.testTag("auto_reconnect_switch")
            )
            SettingSwitchRow(
                title = "Auto-Start on Boot",
                description = "Connect automatically when the device or Android TV boots up",
                checked = autoStartOnBoot,
                onCheckedChange = { viewModel.setAutoStartOnBoot(it) },
                icon = Icons.Default.PowerSettingsNew,
                iconTint = EmeraldSuccess,
                modifier = Modifier.testTag("auto_start_boot_switch")
            )
        }

        // ==========================================
        // 2. NETWORK & DNS
        // ==========================================
        SectionTitle("NETWORK & DNS")
        SettingsCard {
            SettingSwitchRow(
                title = "Bypass Local Network (LAN)",
                description = "Routed apps reach devices in your home network (NAS, DLNA, Chromecast, router) directly. Internet traffic still goes through SSH.",
                checked = bypassLan,
                onCheckedChange = { viewModel.setBypassLan(it) },
                icon = Icons.Default.Home,
                modifier = Modifier.testTag("bypass_lan_switch")
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tunnel DNS Server: $dnsServer",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                )
            }
            Text(
                text = "DNS queries of routed apps are resolved by this server through the SSH tunnel (DNS over TCP). Use a public resolver: local addresses are not reachable from the SSH server.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                VpnPrefs.DNS_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = dnsServer == preset,
                        onClick = { viewModel.setDnsServer(preset) },
                        label = { Text(preset, fontFamily = RobotoMonoFontFamily) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyanPrimary
                        ),
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                            .testTag("dns_preset_$preset")
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            CustomDnsInput(currentDns = dnsServer, onApply = { value, onResult -> viewModel.setDnsServer(value, onResult) })
        }

        // ==========================================
        // 3. BATTERY SAVING & RESOURCE CONTROL
        // ==========================================
        SectionTitle("BATTERY SAVING & RESOURCE CONTROL")
        SettingsCard {
            SettingSwitchRow(
                title = "Smart Idle Mode",
                description = "When routed apps are idle, refresh statistics less often and pause latency checks",
                checked = smartBatterySaver,
                onCheckedChange = { viewModel.setSmartBatterySaver(it) },
                icon = Icons.Default.BatteryChargingFull,
                iconTint = EmeraldSuccess,
                modifier = Modifier.testTag("battery_saver_switch")
            )
            if (smartBatterySaver) {
                Text(
                    text = "Idle after:",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary),
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    listOf(30, 60, 120, 300).forEach { seconds ->
                        FilterChip(
                            selected = idleTimeoutSeconds == seconds,
                            onClick = { viewModel.setIdleTimeoutSeconds(seconds) },
                            label = { Text(if (seconds < 60) "${seconds}s" else "${seconds / 60} min") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CyanPrimary
                            ),
                            modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (!isTv) {
                SettingSwitchRow(
                    title = "Screen-Off Throttling",
                    description = "Refresh statistics every 15s and pause latency checks while the screen is off",
                    checked = screenOffThrottling,
                    onCheckedChange = { viewModel.setScreenOffThrottling(it) },
                    modifier = Modifier.testTag("screen_off_throttling_switch")
                )
            }
            SettingSwitchRow(
                title = "Aggressive Radio Saver (60s+ keepalive)",
                description = "Sends SSH keepalives at most once per minute so Wi-Fi/mobile radios wake up less often. Dead connections are detected more slowly.",
                checked = aggressiveBatterySaver,
                onCheckedChange = { viewModel.setAggressiveBatterySaver(it) },
                modifier = Modifier.testTag("aggressive_battery_saver_switch")
            )
        }

        // ==========================================
        // 4. OEM / XIAOMI ANTI-KILL PROTECTION (phones and tablets)
        // ==========================================
        if (!isTv || !isBatteryOptimizedIgnored) {
            SectionTitle("BACKGROUND PROTECTION")
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBatteryOptimizedIgnored) DarkSurfaceBorder else AmberWarning.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isBatteryOptimizedIgnored) EmeraldSuccess else AmberWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device Protection: ${detectedBrand.displayName}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            )
                            Text(
                                text = if (isBatteryOptimizedIgnored) {
                                    "Battery optimizations disabled (service protected from sleep)"
                                } else {
                                    "Battery optimizations active (the system may stop the VPN in background)"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (isBatteryOptimizedIgnored) EmeraldSuccess else AmberWarning
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                color = if (isBatteryOptimizedIgnored) TextPrimary else Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (!isTv) {
                            OutlinedButton(
                                onClick = { OemBatteryOptimizationHelper.openOemAutostartSettings(context) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                                    .testTag("open_autostart_settings_button")
                            ) {
                                Text(text = "Autostart Settings", color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (!isTv) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { showOemGuideDialog = true },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                        ) {
                            Text(text = "View Step-by-Step ${detectedBrand.displayName} Guide", color = CyanPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. SOCKS5 TUNNEL INFO
        // ==========================================
        val socksPort = activeServer?.socksPort ?: 10808
        InfoCard(
            icon = Icons.Default.Info,
            title = "SSH Dynamic Forwarding (-D)",
            text = "Equivalent to: ssh -D $socksPort ${activeServer?.let { "${it.username}@${it.host}" } ?: "user@server"}\n\n" +
                "Besides the VPN, apps with SOCKS5 support on this device can use 127.0.0.1:$socksPort while the tunnel is connected.",
            monospace = true
        )

        // ==========================================
        // 6. ANDROID TV & ALWAYS-ON VPN GUIDE
        // ==========================================
        InfoCard(
            icon = Icons.Default.Tv,
            title = "Android TV Remote & Always-on VPN",
            text = "• Use the arrow keys to move, OK to select or toggle, Back to return to Home.\n" +
                "• On Home, OK on the big button connects or disconnects.\n" +
                "• Auto-Start on Boot connects your TV box automatically after power on.\n" +
                "• Always-on VPN: Android Settings → Network & Internet → VPN → SSH Tunnel → Always-on."
        )

        Spacer(modifier = Modifier.height(20.dp))
    }

    // OEM Instructions Dialog
    if (showOemGuideDialog) {
        val instructions = remember(detectedBrand) { OemBatteryOptimizationHelper.getOemInstructions(detectedBrand) }
        AlertDialog(
            onDismissRequest = { showOemGuideDialog = false },
            title = { Text(text = "${detectedBrand.displayName} Background Setup", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To keep the VPN running in the background on ${detectedBrand.displayName}:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                    instructions.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
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
                    Text("Open Settings", color = Color.Black)
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, letterSpacing = 0.5.sp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/** Free-form DNS input, validated and saved only on "Apply" (not on every keystroke). */
@Composable
private fun CustomDnsInput(currentDns: String, onApply: (String, (Boolean) -> Unit) -> Unit) {
    val focusManager = LocalFocusManager.current
    var input by remember(currentDns) { mutableStateOf(if (currentDns in VpnPrefs.DNS_PRESETS) "" else currentDns) }
    var error by remember { mutableStateOf<String?>(null) }
    val apply = {
        if (input.isNotBlank()) {
            onApply(input.trim()) { ok ->
                error = if (ok) null else "Enter a public IPv4 address, e.g. 1.1.1.1"
                if (ok) focusManager.clearFocus()
            }
        }
    }
    val errorText = error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TvTextField(
            value = input,
            onValueChange = {
                input = it.filter { ch -> ch.isDigit() || ch == '.' }.take(15)
                error = null
            },
            label = "Custom DNS (IPv4)",
            placeholder = "e.g. 94.140.14.14",
            singleLine = true,
            isError = errorText != null,
            supportingText = errorText,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { apply() }),
            modifier = Modifier
                .weight(1f)
                .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                .testTag("dns_server_input")
        )
        Button(
            onClick = { apply() },
            enabled = input.isNotBlank() && input.trim() != currentDns,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF001F28)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                .testTag("dns_apply_button")
        ) {
            Text("Apply")
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    monospace: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (monospace) RobotoMonoFontFamily else null,
                    fontSize = if (monospace) 12.sp else MaterialTheme.typography.bodyMedium.fontSize,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            )
        }
    }
}
