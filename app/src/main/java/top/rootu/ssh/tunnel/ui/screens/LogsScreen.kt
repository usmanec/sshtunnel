package top.rootu.ssh.tunnel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import top.rootu.ssh.tunnel.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.rootu.ssh.tunnel.ui.util.tvFocusVisual
import kotlinx.coroutines.delay
import top.rootu.ssh.tunnel.data.model.ConnectionLogEntry
import top.rootu.ssh.tunnel.data.model.LogLevel
import top.rootu.ssh.tunnel.ui.MainViewModel
import top.rootu.ssh.tunnel.ui.theme.AmberWarning
import top.rootu.ssh.tunnel.ui.theme.CyanPrimary
import top.rootu.ssh.tunnel.ui.theme.DarkBackground
import top.rootu.ssh.tunnel.ui.theme.DarkSurfaceBorder
import top.rootu.ssh.tunnel.ui.theme.EmeraldSuccess
import top.rootu.ssh.tunnel.ui.theme.RedError
import top.rootu.ssh.tunnel.ui.theme.RobotoMonoFontFamily
import top.rootu.ssh.tunnel.ui.theme.TextMuted
import top.rootu.ssh.tunnel.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val scope = rememberCoroutineScope()

    // Follow new entries only while the list is scrolled to the end, so reading older lines works
    val followTail by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && followTail) {
            listState.scrollToItem(logs.size - 1)
        }
    }

    val copyFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try {
            copyFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.logs_header),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = stringResource(R.string.logs_count, logs.size),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        val text = logs.joinToString("\n") { entry ->
                            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))
                            "[$time] [${entry.level}] ${entry.message}"
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.title_logs), text))
                        Toast.makeText(context, context.getString(R.string.toast_logs_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .focusRequester(copyFocusRequester)
                        .tvFocusVisual(shape = CircleShape)
                        .testTag("copy_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_logs),
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.clearLogs() },
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusVisual(shape = CircleShape)
                        .testTag("clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.cd_clear_logs),
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = DarkBackground),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.logs_empty),
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                        )
                        Text(
                            text = stringResource(R.string.logs_empty_hint),
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                    }
                }
            } else {
                // Plain text rows are not focusable, so the list itself takes focus and scrolls with Up/Down
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusVisual(shape = RoundedCornerShape(12.dp), focusedScale = 1f)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> listState.canScrollForward.also { can ->
                                    if (can) scope.launch { listState.animateScrollBy(240f) }
                                }
                                Key.DirectionUp -> listState.canScrollBackward.also { can ->
                                    if (can) scope.launch { listState.animateScrollBy(-240f) }
                                }
                                else -> false
                            }
                        }
                        .focusable()
                        .padding(12.dp)
                        .testTag("logs_list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = logs) { entry ->
                        LogEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: ConnectionLogEntry) {
    val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestamp))
    val levelColor = when (entry.level) {
        LogLevel.SUCCESS -> EmeraldSuccess
        LogLevel.INFO -> CyanPrimary
        LogLevel.WARN -> AmberWarning
        LogLevel.ERROR -> RedError
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = RobotoMonoFontFamily,
                color = TextMuted,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = RobotoMonoFontFamily,
                color = levelColor,
                fontSize = 12.sp
            )
        )
    }
}
