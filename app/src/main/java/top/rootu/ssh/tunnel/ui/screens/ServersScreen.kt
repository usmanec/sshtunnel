package top.rootu.ssh.tunnel.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import top.rootu.ssh.tunnel.ui.util.tvFocusVisual
import top.rootu.ssh.tunnel.ui.util.tvCardFocusable
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import top.rootu.ssh.tunnel.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import android.content.ActivityNotFoundException
import top.rootu.ssh.tunnel.ui.components.SettingSwitchRow
import top.rootu.ssh.tunnel.ui.components.TvTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.rootu.ssh.tunnel.data.model.AuthType
import top.rootu.ssh.tunnel.data.model.SshServerProfile
import top.rootu.ssh.tunnel.ui.MainViewModel
import top.rootu.ssh.tunnel.ui.theme.AmberWarning
import top.rootu.ssh.tunnel.ui.theme.CyanPrimary
import top.rootu.ssh.tunnel.ui.theme.DarkBackground
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
fun ServersScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val servers by viewModel.allServers.collectAsState()
    val isDialogOpen by viewModel.isServerDialogOpen.collectAsState()
    val editingServer by viewModel.editingServer.collectAsState()
    val testResult by viewModel.testConnectionResult.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val isTestSuccessful by viewModel.isTestSuccessful.collectAsState()

    val context = LocalContext.current
    var serverToDelete by remember { mutableStateOf<SshServerProfile?>(null) }
    val firstServerFocusRequester = remember { FocusRequester() }
    val addFabFocusRequester = remember { FocusRequester() }

    val editorOpen = isDialogOpen && editingServer != null

    // Focus when the screen opens, when the list size changes (add/delete) and after the editor closes
    androidx.compose.runtime.LaunchedEffect(servers.size, editorOpen) {
        if (editorOpen) return@LaunchedEffect
        delay(150)
        try {
            if (servers.isNotEmpty()) {
                firstServerFocusRequester.requestFocus()
            } else {
                addFabFocusRequester.requestFocus()
            }
        } catch (_: Exception) {}
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The editor replaces the list, so the remote cannot wander into cards behind it
        if (!editorOpen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header with the add action on top: easy to reach with the D-pad from any card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.servers_header, servers.size),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                letterSpacing = 0.5.sp
                            )
                        )
                        if (servers.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.servers_hint),
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.openAddServerDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color(0xFF001F28)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .focusRequester(addFabFocusRequester)
                            .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                            .testTag("add_server_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_add_server), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.servers_empty),
                                style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
                            )
                            Text(
                                text = stringResource(R.string.servers_empty_hint),
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(items = servers, key = { it.id }) { server ->
                            val isFirst = servers.firstOrNull()?.id == server.id
                            ServerItemCard(
                                server = server,
                                focusRequester = if (isFirst) firstServerFocusRequester else null,
                                onSelect = { viewModel.setActiveServer(context, server.id) },
                                onEdit = { viewModel.openEditServerDialog(server) },
                                onDelete = { serverToDelete = server }
                            )
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (serverToDelete != null) {
            val target = serverToDelete!!
            AlertDialog(
                onDismissRequest = { serverToDelete = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RedError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.delete_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                },
                text = {
                    Text(
                        text = stringResource(R.string.delete_text, target.name, "${target.username}@${target.host}:${target.port}"),
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteServer(target)
                            serverToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RedError,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                            .testTag("confirm_delete_server_button")
                    ) {
                        Text(stringResource(R.string.btn_delete), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { serverToDelete = null },
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                            .testTag("cancel_delete_server_button")
                    ) {
                        Text(stringResource(R.string.btn_cancel), style = MaterialTheme.typography.labelMedium.copy(color = TextMuted))
                    }
                },
                containerColor = DarkSurface
            )
        }

        // Add / Edit form
        if (editorOpen) {
            ServerEditDialog(
                server = editingServer!!,
                testResult = testResult,
                isTesting = isTesting,
                isTestSuccessful = isTestSuccessful,
                viewModel = viewModel,
                onDismiss = { viewModel.closeServerDialog() }
            )
        }
    }
}

@Composable
private fun ServerItemCard(
    server: SshServerProfile,
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server_item_${server.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (server.isActive) DarkSurfaceVariant else DarkSurface
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (server.isActive) CyanPrimary else DarkSurfaceBorder
        )
    ) {
        // The select area and the buttons are sibling focus targets, so Left/Right on the
        // remote moves between them (focusables nested in a focusable card are unreachable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .tvCardFocusable(
                        shape = RoundedCornerShape(10.dp),
                        focusBorderColor = CyanPrimary,
                        focusedScale = 1.01f
                    ) { onSelect() }
                    .padding(8.dp)
                    .testTag("select_server_${server.id}")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (server.isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(if (server.isActive) R.string.cd_active_server else R.string.cd_make_active),
                        tint = if (server.isActive) CyanPrimary else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (server.isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyanPrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.badge_active),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CyanPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${server.username}@${server.host}:${server.port}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = RobotoMonoFontFamily,
                        color = TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Badges row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge {
                        Icon(
                            imageVector = authOption(server.authType).icon,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(authOption(server.authType).label),
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                    Badge {
                        Text(
                            text = "SOCKS: 127.0.0.1:${server.socksPort}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = RobotoMonoFontFamily,
                                color = TextMuted
                            )
                        )
                    }
                    if (server.compression) {
                        Badge {
                            Text(text = "zlib", style = MaterialTheme.typography.labelSmall.copy(color = EmeraldSuccess))
                        }
                    }
                    if (server.lastPingMs != null) {
                        Text(
                            text = stringResource(R.string.unit_ms, server.lastPingMs),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = RobotoMonoFontFamily,
                                color = if (server.lastPingMs < 150) EmeraldSuccess else AmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Labeled buttons: on TV the focused action must be obvious
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                        .testTag("edit_server_${server.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_edit), color = TextSecondary)
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                        .testTag("delete_server_${server.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = RedError.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_delete), color = RedError.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun Badge(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DarkBackground)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerEditDialog(
    server: SshServerProfile,
    testResult: String?,
    isTesting: Boolean,
    isTestSuccessful: Boolean,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(server.name) }
    var host by remember { mutableStateOf(server.host) }
    var portText by remember { mutableStateOf(server.port.toString()) }
    var username by remember { mutableStateOf(server.username) }
    var authType by remember { mutableStateOf(server.authType) }
    var password by remember { mutableStateOf(server.password) }
    var privateKey by remember { mutableStateOf(server.privateKey) }
    val configuredKeyLabel = stringResource(R.string.key_configured)
    val isTestFailed by viewModel.isTestFailed.collectAsState()
    var importedFileName by remember {
        mutableStateOf(if (server.privateKey.isNotBlank()) configuredKeyLabel else "")
    }
    var showRawKeyField by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf(server.passphrase) }
    var socksPortText by remember { mutableStateOf(server.socksPort.toString()) }
    var compression by remember { mutableStateOf(server.compression) }
    var keepAliveText by remember { mutableStateOf(server.keepAliveIntervalSeconds.toString()) }
    var showPassword by remember { mutableStateOf(false) }

    // File Picker for SSH Private Key file
    val keyFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = viewModel.readKeyFileContent(context, uri)
            if (result != null) {
                importedFileName = result.first
                privateKey = result.second
                Toast.makeText(context, context.getString(R.string.toast_key_imported, result.first), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_key_read_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun launchKeyPicker() {
        try {
            keyFilePickerLauncher.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            // Many Android TV devices ship without a document picker
            showRawKeyField = true
            Toast.makeText(context, context.getString(R.string.toast_no_file_picker), Toast.LENGTH_LONG).show()
        }
    }

    val portValid = portText.toIntOrNull()?.let { it in 1..65535 } == true
    val socksPortValid = socksPortText.toIntOrNull()?.let { it in 1024..65535 } == true
    val keepAliveValid = keepAliveText.toIntOrNull()?.let { it in 0..3600 } == true
    val keyValid = !authType.usesKey || privateKey.isNotBlank()
    // With key + password both are required; a plain password may be empty (rare "none"-style setups)
    val passwordValid = authType != AuthType.PRIVATE_KEY_AND_PASSWORD || password.isNotEmpty()
    val formValid = host.isNotBlank() && portValid && socksPortValid && keepAliveValid && keyValid && passwordValid

    fun buildCurrentProfile(): SshServerProfile {
        val port = portText.toIntOrNull() ?: 22
        val socksPort = socksPortText.toIntOrNull() ?: 10808
        val keepAlive = keepAliveText.toIntOrNull() ?: 15
        return server.copy(
            name = if (name.isBlank()) host.trim() else name.trim(),
            host = host.trim(),
            port = port,
            username = username.trim().ifBlank { "root" },
            authType = authType,
            // Do not keep secrets the selected method does not use
            password = if (authType.usesPassword) password else "",
            privateKey = if (authType.usesKey) privateKey.trim() else "",
            passphrase = if (authType.usesKey) passphrase else "",
            socksPort = socksPort,
            compression = compression,
            keepAliveIntervalSeconds = keepAlive
        )
    }

    val currentProfile = buildCurrentProfile()
    val isAlreadyVerified = viewModel.isProfileVerified(currentProfile)

    val nameFocusRequester = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // New server: start typing right away. Editing: let the user pick a field with the remote
        if (server.id == 0L) {
            delay(150)
            try {
                nameFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val testFailed = !isTesting && !isAlreadyVerified && isTestFailed

    FullScreenEditor(
        onDismiss = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(if (server.id == 0L) R.string.editor_title_add else R.string.editor_title_edit),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Notice banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.editor_notice),
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }

                TvTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.field_profile_name),
                    placeholder = stringResource(R.string.hint_profile_name),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                        .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = stringResource(R.string.field_host),
                        placeholder = "ssh.example.com",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(2f)
                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                    )
                    TvTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = stringResource(R.string.field_port),
                        singleLine = true,
                        isError = !portValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                    )
                }

                TvTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.field_username),
                    placeholder = "root",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                )

                // Authentication: one selector, then only the fields it needs (key first, then password)
                Text(
                    text = stringResource(R.string.auth_label),
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AUTH_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = authType == option.type,
                            onClick = { authType = option.type },
                            label = { Text(stringResource(option.label)) },
                            leadingIcon = {
                                Icon(option.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CyanPrimary,
                                selectedLeadingIconColor = CyanPrimary
                            ),
                            modifier = Modifier
                                .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                                .testTag("auth_type_${option.type.name.lowercase()}")
                        )
                    }
                }

                if (authType.usesKey) {
                    // File Picker for Private Key
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.key_file_title),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = if (privateKey.isNotBlank())
                                            (if (importedFileName.isNotBlank()) importedFileName else stringResource(R.string.key_loaded, privateKey.length))
                                        else stringResource(R.string.key_none),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = RobotoMonoFontFamily,
                                            color = if (privateKey.isNotBlank()) EmeraldSuccess else TextMuted
                                        )
                                    )
                                }

                                Button(
                                    onClick = { launchKeyPicker() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanPrimary,
                                        contentColor = Color(0xFF001F28)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_pick_file), style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (privateKey.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showRawKeyField = !showRawKeyField },
                                        modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            imageVector = if (showRawKeyField) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = CyanPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(if (showRawKeyField) R.string.btn_hide_key else R.string.btn_show_key),
                                            style = MaterialTheme.typography.labelSmall.copy(color = CyanPrimary)
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            privateKey = ""
                                            importedFileName = ""
                                        },
                                        modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = RedError,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.btn_remove), style = MaterialTheme.typography.labelSmall.copy(color = RedError))
                                    }
                                }
                            }

                            AnimatedVisibility(visible = showRawKeyField || privateKey.isBlank()) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    TvTextField(
                                        value = privateKey,
                                        onValueChange = { privateKey = it },
                                        label = stringResource(R.string.field_key_text),
                                        placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----...",
                                        maxLines = 6,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                                    )
                                }
                            }
                        }
                    }

                    TvTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = stringResource(R.string.field_passphrase),
                        placeholder = stringResource(R.string.hint_passphrase),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                    )
                }

                if (authType.usesPassword) {
                    TvTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(if (authType.usesKey) R.string.field_password_after_key else R.string.field_password),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        trailingIcon = {
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.tvFocusVisual(shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(R.string.cd_toggle_password)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvTextField(
                        value = socksPortText,
                        onValueChange = { socksPortText = it.filter { ch -> ch.isDigit() } },
                        label = stringResource(R.string.field_socks_port),
                        singleLine = true,
                        isError = !socksPortValid,
                        supportingText = if (!socksPortValid) "1024–65535" else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                    )
                    TvTextField(
                        value = keepAliveText,
                        onValueChange = { keepAliveText = it.filter { ch -> ch.isDigit() } },
                        label = stringResource(R.string.field_keepalive),
                        singleLine = true,
                        isError = !keepAliveValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                    )
                }

                SettingSwitchRow(
                    title = stringResource(R.string.compression_title),
                    description = stringResource(R.string.compression_desc),
                    checked = compression,
                    onCheckedChange = { compression = it }
                )

                // Dedicated Connection Test Action
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAlreadyVerified) EmeraldSuccess.copy(alpha = 0.12f) else DarkBackground
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isAlreadyVerified) EmeraldSuccess.copy(alpha = 0.5f) else DarkSurfaceBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isAlreadyVerified) Icons.Default.CheckCircle else Icons.Default.NetworkPing,
                                    contentDescription = null,
                                    tint = if (isAlreadyVerified) EmeraldSuccess else CyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(if (isAlreadyVerified) R.string.verified_title else R.string.verify_required),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAlreadyVerified) EmeraldSuccess else TextPrimary
                                        )
                                    )
                                    Text(
                                        text = stringResource(if (isAlreadyVerified) R.string.ready_to_save else R.string.test_before_save),
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.testServer(currentProfile)
                                },
                                enabled = !isTesting && formValid,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAlreadyVerified) EmeraldSuccess else CyanPrimary,
                                    contentColor = Color(0xFF001F28)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                                    .testTag("test_server_button")
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF001F28)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_testing), style = MaterialTheme.typography.labelMedium)
                                } else {
                                    Text(
                                        text = stringResource(if (isAlreadyVerified) R.string.btn_retest else R.string.btn_test),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        if (testResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = if (isAlreadyVerified) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isAlreadyVerified) EmeraldSuccess else RedError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = testResult,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isAlreadyVerified) EmeraldSuccess else RedError,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveServerVerified(
                        context = context,
                        profile = currentProfile,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.toast_server_saved), Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, context.getString(R.string.toast_cannot_save, error), Toast.LENGTH_LONG).show()
                        }
                    )
                },
                enabled = formValid && !isTesting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAlreadyVerified) EmeraldSuccess else CyanPrimary,
                    contentColor = Color(0xFF001F28)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                    .testTag("save_server_button")
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF001F28)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_verifying))
                } else {
                    Icon(
                        imageVector = if (isAlreadyVerified) Icons.Default.Check else Icons.Default.NetworkPing,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(if (isAlreadyVerified) R.string.btn_save_server else R.string.btn_test_and_save))
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (testFailed && formValid) {
                    // The server may just be offline right now: allow saving anyway
                    TextButton(
                        onClick = { viewModel.saveServerUnverified(context, currentProfile) },
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                            .testTag("save_unverified_server_button")
                    ) {
                        Text(stringResource(R.string.btn_save_anyway), style = MaterialTheme.typography.labelMedium.copy(color = AmberWarning))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                        .testTag("cancel_edit_server_button")
                ) {
                    Text(stringResource(R.string.btn_cancel), style = MaterialTheme.typography.labelMedium.copy(color = TextMuted))
                }
            }
        },
    )
}

/**
 * Full-screen form in the activity window instead of a dialog: on TV a dialog window is re-sized
 * and re-centered whenever the keyboard or content changes, which makes the form jump.
 * Back closes it like the Cancel button.
 */
@Composable
private fun FullScreenEditor(
    onDismiss: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    BackHandler(onBack = onDismiss)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        title()
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            text()
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dismissButton()
            Spacer(modifier = Modifier.width(8.dp))
            confirmButton()
        }
    }
}

private class AuthOption(val type: AuthType, @StringRes val label: Int, val icon: ImageVector)

private val AUTH_OPTIONS = listOf(
    AuthOption(AuthType.PASSWORD, R.string.auth_password, Icons.Default.Password),
    AuthOption(AuthType.PRIVATE_KEY, R.string.auth_key, Icons.Default.Key),
    AuthOption(AuthType.PRIVATE_KEY_AND_PASSWORD, R.string.auth_key_password, Icons.Default.VpnKey)
)

private fun authOption(type: AuthType) = AUTH_OPTIONS.first { it.type == type }
