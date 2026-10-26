package top.rootu.ssh.tunnel.ui.screens

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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import top.rootu.ssh.tunnel.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import top.rootu.ssh.tunnel.data.repository.VpnPrefs
import top.rootu.ssh.tunnel.ui.MainViewModel
import top.rootu.ssh.tunnel.ui.MainViewModel.UpdateError
import top.rootu.ssh.tunnel.ui.MainViewModel.UpdateState
import top.rootu.ssh.tunnel.ui.components.ReconnectBanner
import top.rootu.ssh.tunnel.ui.components.SettingSwitchRow
import top.rootu.ssh.tunnel.ui.components.TvTextField
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
import top.rootu.ssh.tunnel.ui.util.OemBatteryOptimizationHelper
import top.rootu.ssh.tunnel.ui.util.rememberIsTv
import top.rootu.ssh.tunnel.ui.util.tvFocusVisual

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
    val brandName = remember(detectedBrand) { OemBatteryOptimizationHelper.brandName(context, detectedBrand) }

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
        SectionTitle(stringResource(R.string.section_reliability))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.auto_reconnect_title),
                description = stringResource(R.string.auto_reconnect_desc),
                checked = autoReconnect,
                onCheckedChange = { viewModel.setAutoReconnect(it) },
                icon = Icons.Default.Loop,
                modifier = Modifier.testTag("auto_reconnect_switch")
            )
            SettingSwitchRow(
                title = stringResource(R.string.boot_title),
                description = stringResource(R.string.boot_desc),
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
        SectionTitle(stringResource(R.string.section_network))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.bypass_lan_title),
                description = stringResource(R.string.bypass_lan_desc),
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
                    text = stringResource(R.string.dns_title, dnsServer),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                )
            }
            Text(
                text = stringResource(R.string.dns_desc),
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
        SectionTitle(stringResource(R.string.section_battery))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.idle_mode_title),
                description = stringResource(R.string.idle_mode_desc),
                checked = smartBatterySaver,
                onCheckedChange = { viewModel.setSmartBatterySaver(it) },
                icon = Icons.Default.BatteryChargingFull,
                iconTint = EmeraldSuccess,
                modifier = Modifier.testTag("battery_saver_switch")
            )
            if (smartBatterySaver) {
                Text(
                    text = stringResource(R.string.idle_after),
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary),
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    listOf(30, 60, 120, 300).forEach { seconds ->
                        FilterChip(
                            selected = idleTimeoutSeconds == seconds,
                            onClick = { viewModel.setIdleTimeoutSeconds(seconds) },
                            label = { Text(if (seconds < 60) stringResource(R.string.seconds_short, seconds) else stringResource(R.string.minutes_short, seconds / 60)) },
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
                    title = stringResource(R.string.screen_off_title),
                    description = stringResource(R.string.screen_off_desc),
                    checked = screenOffThrottling,
                    onCheckedChange = { viewModel.setScreenOffThrottling(it) },
                    modifier = Modifier.testTag("screen_off_throttling_switch")
                )
            }
            SettingSwitchRow(
                title = stringResource(R.string.radio_saver_title),
                description = stringResource(R.string.radio_saver_desc),
                checked = aggressiveBatterySaver,
                onCheckedChange = { viewModel.setAggressiveBatterySaver(it) },
                modifier = Modifier.testTag("aggressive_battery_saver_switch")
            )
        }

        // ==========================================
        // 4. OEM / XIAOMI ANTI-KILL PROTECTION (phones and tablets)
        // ==========================================
        if (!isTv || !isBatteryOptimizedIgnored) {
            SectionTitle(stringResource(R.string.section_background))
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
                                text = stringResource(R.string.device_protection, brandName),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            )
                            Text(
                                text = if (isBatteryOptimizedIgnored) {
                                    stringResource(R.string.battery_opt_off)
                                } else {
                                    stringResource(R.string.battery_opt_on)
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (isBatteryOptimizedIgnored) EmeraldSuccess else AmberWarning
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    // Not weighted: long labels get their own line instead of wrapping inside the buttons
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                                .testTag("request_battery_exemption_button")
                        ) {
                            Text(
                                text = stringResource(if (isBatteryOptimizedIgnored) R.string.btn_recheck else R.string.btn_disable_opt),
                                color = if (isBatteryOptimizedIgnored) TextPrimary else Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        if (!isTv) {
                            OutlinedButton(
                                onClick = { OemBatteryOptimizationHelper.openOemAutostartSettings(context) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                                    .testTag("open_autostart_settings_button")
                            ) {
                                Text(text = stringResource(R.string.btn_autostart_settings), color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                            Text(text = stringResource(R.string.btn_oem_guide, brandName), color = CyanPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. APP UPDATES (GitHub releases)
        // ==========================================
        SectionTitle(stringResource(R.string.section_updates))
        UpdatesCard(viewModel)

        // ==========================================
        // 6. SOCKS5 TUNNEL INFO
        // ==========================================
        val socksPort = activeServer?.socksPort ?: 10808
        InfoCard(
            icon = Icons.Default.Info,
            title = stringResource(R.string.socks_info_title),
            text = stringResource(R.string.socks_info_text, socksPort, activeServer?.let { "${it.username}@${it.host}" } ?: "user@server"),
            monospace = true
        )

        // ==========================================
        // 7. ANDROID TV & ALWAYS-ON VPN GUIDE
        // ==========================================
        InfoCard(
            icon = Icons.Default.Tv,
            title = stringResource(R.string.tv_info_title),
            text = stringResource(R.string.tv_info_text)
        )

        Spacer(modifier = Modifier.height(20.dp))
    }

    // OEM Instructions Dialog
    if (showOemGuideDialog) {
        val instructions = remember(detectedBrand) { OemBatteryOptimizationHelper.getOemInstructions(context, detectedBrand) }
        AlertDialog(
            onDismissRequest = { showOemGuideDialog = false },
            title = { Text(text = stringResource(R.string.oem_dialog_title, brandName), fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.oem_dialog_text, brandName),
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
                    Text(stringResource(R.string.btn_open_settings), color = Color.Black, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOemGuideDialog = false },
                    modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(stringResource(R.string.btn_close), color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdatesCard(viewModel: MainViewModel) {
    val state by viewModel.updateState.collectAsState()
    LaunchedEffect(Unit) { viewModel.checkForUpdateOnce() }

    SettingsCard {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.update_current_version, viewModel.currentVersion),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
                )
            }

            val s = state
            val (statusText, statusColor) = when (s) {
                UpdateState.Idle -> null to TextSecondary
                UpdateState.Checking -> stringResource(R.string.update_checking) to TextSecondary
                UpdateState.UpToDate -> stringResource(R.string.update_up_to_date) to EmeraldSuccess
                is UpdateState.Available -> stringResource(R.string.update_available, s.info.version, formatSize(s.info.size)) to CyanPrimary
                is UpdateState.Downloading -> stringResource(R.string.update_downloading, s.info.version) to CyanPrimary
                is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready, s.info.version) to EmeraldSuccess
                is UpdateState.NeedsInstallPermission -> stringResource(R.string.update_needs_permission) to AmberWarning
                is UpdateState.Failed -> {
                    val base = stringResource(
                        when (s.error) {
                            UpdateError.CHECK_FAILED -> R.string.update_error_check
                            UpdateError.DOWNLOAD_FAILED -> R.string.update_error_download
                            UpdateError.NOT_THIS_APP -> R.string.update_error_not_this_app
                            UpdateError.SIGNATURE_MISMATCH -> R.string.update_error_signature
                            UpdateError.NO_INSTALLER -> R.string.update_error_no_installer
                            UpdateError.NO_PERMISSION_SCREEN -> R.string.update_error_no_permission_screen
                        }
                    )
                    (if (s.detail.isNullOrBlank()) base else "$base (${s.detail})") to RedError
                }
            }
            if (statusText != null) {
                Text(text = statusText, style = MaterialTheme.typography.bodyMedium.copy(color = statusColor))
            }

            if (s is UpdateState.Downloading) {
                if (s.progress >= 0) {
                    LinearProgressIndicator(progress = { s.progress / 100f }, color = CyanPrimary, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(color = CyanPrimary, modifier = Modifier.fillMaxWidth())
                }
            }

            val notes = (s as? UpdateState.Available)?.info?.notes
            if (!notes.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.update_changes),
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = notes.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, lineHeight = 18.sp),
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (s) {
                    is UpdateState.Available -> UpdateButton(stringResource(R.string.btn_update_install), primary = true, tag = "update_download_button") {
                        viewModel.downloadAndInstallUpdate(s.info)
                    }
                    is UpdateState.ReadyToInstall, is UpdateState.NeedsInstallPermission ->
                        UpdateButton(stringResource(R.string.btn_update_install_now), primary = true, tag = "update_install_button") {
                            viewModel.installUpdate()
                        }
                    else -> Unit
                }
                if (s !is UpdateState.Checking && s !is UpdateState.Downloading) {
                    UpdateButton(stringResource(R.string.btn_update_check), primary = false, tag = "update_check_button") {
                        viewModel.checkForUpdate()
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateButton(text: String, primary: Boolean, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val modifier = Modifier.tvFocusVisual(shape = shape).testTag(tag)
    if (primary) {
        Button(onClick = onClick, shape = shape, colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary), modifier = modifier) {
            Text(text, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = shape, modifier = modifier) {
            Text(text, color = CyanPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) stringResource(R.string.size_mb, bytes / (1024.0 * 1024.0))
    else stringResource(R.string.size_kb, (bytes / 1024).coerceAtLeast(1))

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
    val errorMessage = stringResource(R.string.error_dns)
    val apply = {
        if (input.isNotBlank()) {
            onApply(input.trim()) { ok ->
                error = if (ok) null else errorMessage
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
            label = stringResource(R.string.field_custom_dns),
            placeholder = stringResource(R.string.hint_custom_dns),
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
            Text(stringResource(R.string.btn_apply), maxLines = 1)
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
