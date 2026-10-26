package top.rootu.ssh.tunnel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import top.rootu.ssh.tunnel.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.rootu.ssh.tunnel.ui.MainViewModel
import top.rootu.ssh.tunnel.ui.theme.AmberWarning
import top.rootu.ssh.tunnel.ui.theme.CyanPrimary
import top.rootu.ssh.tunnel.ui.theme.TextPrimary
import top.rootu.ssh.tunnel.ui.theme.TextSecondary
import top.rootu.ssh.tunnel.ui.util.tvFocusVisual

/**
 * A settings row that is toggled as a whole: one large focus target for the TV remote
 * (OK toggles it) and a bigger touch target on phones.
 */
@Composable
fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = CyanPrimary,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusVisual(shape = shape, focusedScale = 1.01f)
            .clip(shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary)
            )
            Text(text = description, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        }
        Spacer(modifier = Modifier.width(12.dp))
        // Not focusable on its own: the whole row handles the toggle
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary)
        )
    }
}

/**
 * Shown when routing settings were changed while the VPN is running: they only take effect
 * after the tunnel interface is rebuilt.
 */
@Composable
fun ReconnectBanner(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val needsReconnect by viewModel.needsReconnect.collectAsState()
    if (!needsReconnect) return
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AmberWarning.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.reconnect_banner),
            style = MaterialTheme.typography.bodyMedium.copy(color = AmberWarning, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { viewModel.startVpn(context) },
            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = Color.Black),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .tvFocusVisual(shape = RoundedCornerShape(10.dp))
                .testTag("apply_reconnect_button")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.btn_reconnect), maxLines = 1)
        }
    }
}
