package top.rootu.ssh.tunnel

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import top.rootu.ssh.tunnel.ui.MainViewModel
import top.rootu.ssh.tunnel.ui.screens.AppsScreen
import top.rootu.ssh.tunnel.ui.screens.HomeScreen
import top.rootu.ssh.tunnel.ui.screens.LogsScreen
import top.rootu.ssh.tunnel.ui.screens.ServersScreen
import top.rootu.ssh.tunnel.ui.screens.SettingsScreen
import top.rootu.ssh.tunnel.ui.theme.CyanPrimary
import top.rootu.ssh.tunnel.ui.theme.DarkBackground
import top.rootu.ssh.tunnel.ui.theme.DarkSurface
import top.rootu.ssh.tunnel.ui.theme.MyApplicationTheme
import top.rootu.ssh.tunnel.ui.theme.TextMuted
import top.rootu.ssh.tunnel.ui.theme.TextPrimary
import top.rootu.ssh.tunnel.ui.util.isTvDevice
import top.rootu.ssh.tunnel.ui.util.tvFocusVisual
import top.rootu.ssh.tunnel.vpn.SshVpnService

enum class NavDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Security, Icons.Outlined.Security),
    APPS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    SERVERS("Servers", Icons.Filled.Dns, Icons.Outlined.Dns),
    LOGS("Logs", Icons.Filled.Terminal, Icons.Outlined.Terminal),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SshVpnService.isUiVisible = true
    }

    override fun onStop() {
        SshVpnService.isUiVisible = false
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var currentDestination by remember { mutableStateOf(NavDestination.HOME) }
    val vpnStatus by viewModel.vpnStatus.collectAsState()

    // Detect Android TV / Google TV or wide screen landscape
    val isTvMode = remember { isTvDevice(context) }
    val isWideLayout = isTvMode || configuration.screenWidthDp >= 640 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Handled gracefully */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // BackHandler to return to HOME when on sub-screen
    BackHandler(enabled = currentDestination != NavDestination.HOME) {
        currentDestination = NavDestination.HOME
    }

    if (isWideLayout) {
        // Android TV / Landscape Widescreen Layout: Navigation Rail on left + Content on right
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            NavigationRail(
                containerColor = DarkSurface,
                header = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyanPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isTvMode) Icons.Default.Tv else Icons.Default.Security,
                                contentDescription = "SSH VPN",
                                tint = CyanPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (isTvMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "TV MODE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanPrimary
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .testTag("tv_navigation_rail")
            ) {
                Spacer(modifier = Modifier.weight(1f))
                NavDestination.values().forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF001F28),
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                            .testTag("nav_rail_${destination.name.lowercase()}")
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // Right Pane: Content with Top App Bar
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                containerColor = DarkBackground,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = when (currentDestination) {
                                    NavDestination.HOME -> "SSH Tunnel"
                                    NavDestination.APPS -> "Per-App Routing (Split Tunnel)"
                                    NavDestination.SERVERS -> "SSH Servers & Auth"
                                    NavDestination.LOGS -> "Terminal & Connection Logs"
                                    NavDestination.SETTINGS -> "Settings & TV Mode"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = DarkSurface,
                            titleContentColor = TextPrimary
                        )
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(DarkBackground)
                ) {
                    when (currentDestination) {
                        NavDestination.HOME -> HomeScreen(
                            viewModel = viewModel,
                            onNavigateToApps = { currentDestination = NavDestination.APPS },
                            onNavigateToServers = { currentDestination = NavDestination.SERVERS }
                        )
                        NavDestination.APPS -> AppsScreen(viewModel = viewModel)
                        NavDestination.SERVERS -> ServersScreen(viewModel = viewModel)
                        NavDestination.LOGS -> LogsScreen(viewModel = viewModel)
                        NavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    } else {
        // Mobile Portrait Layout: Bottom Navigation Bar
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentDestination) {
                                NavDestination.HOME -> "SSH Tunnel"
                                NavDestination.APPS -> "Per-App Routing"
                                NavDestination.SERVERS -> "SSH Servers"
                                NavDestination.LOGS -> "Terminal Logs"
                                NavDestination.SETTINGS -> "Settings"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = DarkSurface,
                        titleContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_navigation_bar")
                ) {
                    NavDestination.values().forEach { destination ->
                        val isSelected = currentDestination == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF001F28),
                                selectedTextColor = CyanPrimary,
                                indicatorColor = CyanPrimary,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier
                                .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                                .testTag("nav_item_${destination.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(DarkBackground)
            ) {
                when (currentDestination) {
                    NavDestination.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToApps = { currentDestination = NavDestination.APPS },
                        onNavigateToServers = { currentDestination = NavDestination.SERVERS }
                    )
                    NavDestination.APPS -> AppsScreen(viewModel = viewModel)
                    NavDestination.SERVERS -> ServersScreen(viewModel = viewModel)
                    NavDestination.LOGS -> LogsScreen(viewModel = viewModel)
                    NavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
