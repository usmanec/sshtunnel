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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
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

    var serverToDelete by remember { mutableStateOf<SshServerProfile?>(null) }
    val firstServerFocusRequester = remember { FocusRequester() }
    val addFabFocusRequester = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(servers.size) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "SSH SERVERS (${servers.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )
            )
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
                            text = "Tap the + button to add and verify your server",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(items = servers, key = { it.id }) { server ->
                        val isFirst = servers.firstOrNull()?.id == server.id
                        ServerItemCard(
                            server = server,
                            focusRequester = if (isFirst) firstServerFocusRequester else null,
                            onSelect = { viewModel.setActiveServer(server.id) },
                            onEdit = { viewModel.openEditServerDialog(server) },
                            onDelete = { serverToDelete = server }
                        )
                    }
                }
            }
        }

        // FAB to add server
        FloatingActionButton(
            onClick = { viewModel.openAddServerDialog() },
            containerColor = CyanPrimary,
            contentColor = Color(0xFF001F28),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .focusRequester(addFabFocusRequester)
                .tvFocusVisual(shape = RoundedCornerShape(16.dp))
                .testTag("add_server_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add SSH Server")
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

        // Add / Edit Dialog
        if (isDialogOpen && editingServer != null) {
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
    val cardModifier = Modifier
        .fillMaxWidth()
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .tvCardFocusable(
            shape = RoundedCornerShape(14.dp),
            focusBorderColor = CyanPrimary,
            focusedScale = 1.02f
        ) { onSelect() }
        .testTag("server_item_${server.id}")

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = if (server.isActive) DarkSurfaceVariant else DarkSurface
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (server.isActive) CyanPrimary else DarkSurfaceBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        imageVector = if (server.isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Active Server",
                        tint = if (server.isActive) CyanPrimary else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
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

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .tvFocusVisual(shape = CircleShape)
                            .testTag("edit_server_${server.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .tvFocusVisual(shape = CircleShape)
                            .testTag("delete_server_${server.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = RedError.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Details row
            Text(
                text = "${server.username}@${server.host}:${server.port}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = RobotoMonoFontFamily,
                    color = TextSecondary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auth mode badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkBackground)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                }

                // SOCKS Port badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkBackground)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "SOCKS: 127.0.0.1:${server.socksPort}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMonoFontFamily,
                            color = TextMuted
                        )
                    )
                }

                // Compression badge
                if (server.compression) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkBackground)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "zlib",
                            style = MaterialTheme.typography.labelSmall.copy(color = EmeraldSuccess)
                        )
                    }
                }

                // Ping result
                if (server.lastPingMs != null) {
                    val color = if (server.lastPingMs < 150) EmeraldSuccess else AmberWarning
                    Text(
                        text = "${server.lastPingMs} ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMonoFontFamily,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
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
        delay(150)
        try {
            nameFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g. Frankfurt VPS, Home Tunnel") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                        .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / IP Address") },
                        placeholder = { Text("ssh.rootu.ssh.tunnel.com") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("SSH Username") },
                    placeholder = { Text("root") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
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
                                    onClick = {
                                        keyFilePickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanPrimary,
                                        contentColor = Color(0xFF001F28)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
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
                                    TextButton(onClick = { showRawKeyField = !showRawKeyField }) {
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

                                    TextButton(onClick = {
                                        privateKey = ""
                                        importedFileName = ""
                                    }) {
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
                                    OutlinedTextField(
                                        value = privateKey,
                                        onValueChange = { privateKey = it },
                                        label = { Text("Private Key text (or paste directly)") },
                                        placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----...") },
                                        maxLines = 6,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Key Passphrase (optional)") },
                        placeholder = { Text("Leave blank if unencrypted") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = socksPortText,
                        onValueChange = { socksPortText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("SOCKS5 Port (-D)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = keepAliveText,
                        onValueChange = { keepAliveText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("KeepAlive (sec)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Compression (zlib)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = compression,
                        onCheckedChange = { compression = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary)
                    )
                }

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
                                enabled = !isTesting && host.isNotBlank(),
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
                        profile = currentProfile,
                        onSuccess = {
                            Toast.makeText(context, "Server verified and saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, "Cannot save server: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                },
                enabled = host.isNotBlank() && !isTesting,
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                    .testTag("cancel_edit_server_button")
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(color = TextMuted))
            }
        },
        containerColor = DarkSurface
    )
}
