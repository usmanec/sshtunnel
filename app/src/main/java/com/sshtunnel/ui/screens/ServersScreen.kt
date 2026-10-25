package com.sshtunnel.ui.screens

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
import com.sshtunnel.ui.util.tvFocusVisual
import com.sshtunnel.ui.util.tvCardFocusable
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import android.content.ActivityNotFoundException
import com.sshtunnel.ui.components.SettingSwitchRow
import com.sshtunnel.ui.components.TvTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sshtunnel.data.model.AuthType
import com.sshtunnel.data.model.SshServerProfile
import com.sshtunnel.ui.MainViewModel
import com.sshtunnel.ui.theme.AmberWarning
import com.sshtunnel.ui.theme.CyanPrimary
import com.sshtunnel.ui.theme.DarkBackground
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
                            text = "SSH SERVERS (${servers.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                letterSpacing = 0.5.sp
                            )
                        )
                        if (servers.isNotEmpty()) {
                            Text(
                                text = "OK on a server makes it active (switches the running tunnel)",
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
                        Text("Add Server", fontWeight = FontWeight.SemiBold)
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
                                text = "No SSH servers added yet",
                                style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
                            )
                            Text(
                                text = "Press \"Add Server\" to add and verify your server",
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
                            text = "Delete SSH Server?",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete profile \"${target.name}\" (${target.username}@${target.host}:${target.port})? This action cannot be undone.",
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
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { serverToDelete = null },
                        modifier = Modifier
                            .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                            .testTag("cancel_delete_server_button")
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(color = TextMuted))
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
                        contentDescription = if (server.isActive) "Active server" else "Make active",
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
                                text = "ACTIVE",
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
                            imageVector = if (server.authType == AuthType.PASSWORD) Icons.Default.Password else Icons.Default.Key,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (server.authType == AuthType.PASSWORD) "Password" else "Key",
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
                            text = "${server.lastPingMs} ms",
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
                    Text("Edit", color = TextSecondary)
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
                    Text("Delete", color = RedError.copy(alpha = 0.85f))
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
    var importedFileName by remember {
        mutableStateOf(if (server.privateKey.isNotBlank()) "Configured Key" else "")
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
                Toast.makeText(context, "SSH Key imported: ${result.first}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to read SSH key file", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun launchKeyPicker() {
        try {
            keyFilePickerLauncher.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            // Many Android TV devices ship without a document picker
            showRawKeyField = true
            Toast.makeText(context, "No file picker on this device. Paste the key text instead.", Toast.LENGTH_LONG).show()
        }
    }

    val portValid = portText.toIntOrNull()?.let { it in 1..65535 } == true
    val socksPortValid = socksPortText.toIntOrNull()?.let { it in 1024..65535 } == true
    val keepAliveValid = keepAliveText.toIntOrNull()?.let { it in 0..3600 } == true
    val formValid = host.isNotBlank() && portValid && socksPortValid && keepAliveValid

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
            password = password,
            privateKey = privateKey.trim(),
            passphrase = passphrase,
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

    val testFailed = !isTesting && !isAlreadyVerified && testResult?.startsWith("Error") == true

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
                    text = if (server.id == 0L) "Add & Verify SSH Server" else "Edit SSH Server",
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
                        text = "Connection test is required before saving to ensure host, port, credentials, and key are 100% correct.",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }

                TvTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Profile Name",
                    placeholder = "e.g. Frankfurt VPS, Home Tunnel",
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
                        label = "Host / IP Address",
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
                        label = "Port",
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
                    label = "SSH Username",
                    placeholder = "root",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                )

                // Auth Type Selector
                TabRow(
                    selectedTabIndex = if (authType == AuthType.PASSWORD) 0 else 1,
                    containerColor = DarkSurfaceVariant,
                    contentColor = CyanPrimary
                ) {
                    Tab(
                        selected = authType == AuthType.PASSWORD,
                        onClick = { authType = AuthType.PASSWORD },
                        modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp)),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Password")
                            }
                        }
                    )
                    Tab(
                        selected = authType == AuthType.PRIVATE_KEY,
                        onClick = { authType = AuthType.PRIVATE_KEY },
                        modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp)),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SSH Key File")
                            }
                        }
                    )
                }

                if (authType == AuthType.PASSWORD) {
                    TvTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
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
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
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
                                        text = "Private Key File",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = if (privateKey.isNotBlank())
                                            (if (importedFileName.isNotBlank()) importedFileName else "Key loaded (${privateKey.length} chars)")
                                        else "No key file selected",
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
                                        contentDescription = "Choose file",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick File", style = MaterialTheme.typography.labelMedium)
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
                                            text = if (showRawKeyField) "Hide raw key" else "View / edit raw key",
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
                                        Text("Remove", style = MaterialTheme.typography.labelSmall.copy(color = RedError))
                                    }
                                }
                            }

                            AnimatedVisibility(visible = showRawKeyField || privateKey.isBlank()) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    TvTextField(
                                        value = privateKey,
                                        onValueChange = { privateKey = it },
                                        label = "Private Key text (or paste directly)",
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
                        label = "Key Passphrase (optional)",
                        placeholder = "Leave blank if unencrypted",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvTextField(
                        value = socksPortText,
                        onValueChange = { socksPortText = it.filter { ch -> ch.isDigit() } },
                        label = "SOCKS5 Port (-D)",
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
                        label = "KeepAlive (sec)",
                        singleLine = true,
                        isError = !keepAliveValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                    )
                }

                SettingSwitchRow(
                    title = "Enable Compression (zlib)",
                    description = "Helps on slow links with text traffic; useless for video and already compressed data",
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
                                        text = if (isAlreadyVerified) "Connection Verified" else "Verification Required",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAlreadyVerified) EmeraldSuccess else TextPrimary
                                        )
                                    )
                                    Text(
                                        text = if (isAlreadyVerified) "Ready to save" else "Test SSH connection before saving",
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
                                    Text("Testing...", style = MaterialTheme.typography.labelMedium)
                                } else {
                                    Text(
                                        text = if (isAlreadyVerified) "Re-test" else "Test Connection",
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
                            Toast.makeText(context, "Server verified and saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, "Cannot save server: $error", Toast.LENGTH_LONG).show()
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
                    Text("Verifying...")
                } else {
                    Icon(
                        imageVector = if (isAlreadyVerified) Icons.Default.Check else Icons.Default.NetworkPing,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isAlreadyVerified) "Save Server" else "Test & Save Server")
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
                        Text("Save anyway", style = MaterialTheme.typography.labelMedium.copy(color = AmberWarning))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                        .testTag("cancel_edit_server_button")
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(color = TextMuted))
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
