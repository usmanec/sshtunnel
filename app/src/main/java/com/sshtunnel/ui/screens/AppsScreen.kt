package com.sshtunnel.ui.screens

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sshtunnel.ui.components.ReconnectBanner
import com.sshtunnel.ui.components.TvTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.sshtunnel.ui.util.tvFocusVisual
import com.sshtunnel.ui.util.tvCardFocusable
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.sshtunnel.data.model.AppRoutingRule
import com.sshtunnel.data.model.RoutingPolicy
import com.sshtunnel.ui.AppFilterMode
import com.sshtunnel.ui.MainViewModel
import com.sshtunnel.ui.theme.CyanPrimary
import com.sshtunnel.ui.theme.DarkBackground
import com.sshtunnel.ui.theme.DarkSurface
import com.sshtunnel.ui.theme.DarkSurfaceBorder
import com.sshtunnel.ui.theme.DarkSurfaceVariant
import com.sshtunnel.ui.theme.RobotoMonoFontFamily
import com.sshtunnel.ui.theme.TextMuted
import com.sshtunnel.ui.theme.TextPrimary
import com.sshtunnel.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val routingPolicy by viewModel.routingPolicy.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.appSearchQuery.collectAsState()
    val filterMode by viewModel.appFilterMode.collectAsState()
    val selectedCount by viewModel.selectedAppCount.collectAsState()

    val tabFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        delay(150)
        try {
            tabFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Pick up apps installed or removed while the app was in background
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshInstalledApps()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ReconnectBanner(viewModel, modifier = Modifier.padding(bottom = 10.dp))

        // Routing Policy Tabs
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "ROUTING POLICY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                TabRow(
                    selectedTabIndex = if (routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) 0 else 1,
                    containerColor = DarkSurfaceVariant,
                    contentColor = CyanPrimary,
                    indicator = { tabPositions ->
                        val index = if (routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) 0 else 1
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                            color = CyanPrimary
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED,
                        onClick = { viewModel.setRoutingPolicy(RoutingPolicy.ROUTE_ONLY_SELECTED) },
                        modifier = Modifier
                            .focusRequester(tabFocusRequester)
                            .tvFocusVisual(shape = RoundedCornerShape(8.dp)),
                        text = {
                            Text(
                                "Route ONLY Selected",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    )
                    Tab(
                        selected = routingPolicy == RoutingPolicy.ROUTE_ALL_EXCEPT,
                        onClick = { viewModel.setRoutingPolicy(RoutingPolicy.ROUTE_ALL_EXCEPT) },
                        modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp)),
                        text = {
                            Text(
                                "Route ALL Except",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (routingPolicy == RoutingPolicy.ROUTE_ALL_EXCEPT) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (routingPolicy == RoutingPolicy.ROUTE_ONLY_SELECTED)
                        "Default mode: All device traffic goes direct. Only checked apps are routed via SSH tunnel."
                    else
                        "Bypass mode: All device traffic is routed via SSH tunnel, except checked apps.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Field
        TvTextField(
            value = searchQuery,
            onValueChange = { viewModel.appSearchQuery.value = it },
            placeholder = "Search by name or package (e.g. Chrome, telegram)...",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = CyanPrimary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.appSearchQuery.value = "" },
                        modifier = Modifier.tvFocusVisual(shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = TextMuted
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = DarkSurfaceBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusVisual(shape = RoundedCornerShape(12.dp))
                .testTag("app_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == AppFilterMode.USER_ONLY,
                onClick = { viewModel.appFilterMode.value = AppFilterMode.USER_ONLY },
                label = { Text("User Apps") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = CyanPrimary
                ),
                modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
            )
            FilterChip(
                selected = filterMode == AppFilterMode.SELECTED_ONLY,
                onClick = { viewModel.appFilterMode.value = AppFilterMode.SELECTED_ONLY },
                label = { Text("Selected ($selectedCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = CyanPrimary
                ),
                modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
            )
            FilterChip(
                selected = filterMode == AppFilterMode.ALL,
                onClick = { viewModel.appFilterMode.value = AppFilterMode.ALL },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = CyanPrimary
                ),
                modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
            )
            FilterChip(
                selected = filterMode == AppFilterMode.SYSTEM_ONLY,
                onClick = { viewModel.appFilterMode.value = AppFilterMode.SYSTEM_ONLY },
                label = { Text("System") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = CyanPrimary
                ),
                modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(8.dp))
            )
        }

        // Selection Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredApps.size} apps shown (${selectedCount} selected)",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { viewModel.selectShownApps(true) },
                    enabled = filteredApps.isNotEmpty(),
                    modifier = Modifier
                        .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                        .testTag("select_all_apps_button")
                ) {
                    Text("Select Shown", style = MaterialTheme.typography.labelMedium.copy(color = CyanPrimary))
                }
                TextButton(
                    onClick = { viewModel.selectShownApps(false) },
                    enabled = filteredApps.isNotEmpty(),
                    modifier = Modifier
                        .tvFocusVisual(shape = RoundedCornerShape(8.dp))
                        .testTag("deselect_all_apps_button")
                ) {
                    Text("Clear Shown", style = MaterialTheme.typography.labelMedium.copy(color = TextMuted))
                }
            }
        }

        // Apps List
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No applications match your filter",
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    items = filteredApps,
                    key = { it.packageName }
                ) { rule ->
                    AppItemRow(
                        rule = rule,
                        onToggle = { isSelected ->
                            viewModel.toggleAppSelection(rule.packageName, isSelected)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItemRow(
    rule: AppRoutingRule,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // Load and rasterize the app icon once, off the main thread
    val appIcon by produceState<ImageBitmap?>(initialValue = null, key1 = rule.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(rule.packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardFocusable(
                shape = RoundedCornerShape(12.dp),
                focusBorderColor = CyanPrimary,
                focusedScale = 1.02f
            ) {
                onToggle(!rule.isSelected)
            }
            .testTag("app_item_${rule.packageName}"),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isSelected) DarkSurfaceVariant.copy(alpha = 0.9f) else DarkSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (rule.isSelected) CyanPrimary.copy(alpha = 0.5f) else DarkSurfaceBorder
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                val icon = appIcon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = rule.appName,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.appName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.isSystemApp) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkSurfaceBorder)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "SYS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    color = TextSecondary
                                )
                            )
                        }
                    }
                }
                Text(
                    text = rule.packageName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = RobotoMonoFontFamily,
                        color = TextMuted
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Status indicator icon (replaces Checkbox to avoid duplicate focus node on Android TV)
            Icon(
                imageVector = if (rule.isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (rule.isSelected) "Selected" else "Not selected",
                tint = if (rule.isSelected) CyanPrimary else TextMuted,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
