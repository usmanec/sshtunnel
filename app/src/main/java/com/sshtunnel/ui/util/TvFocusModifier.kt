package com.sshtunnel.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sshtunnel.ui.theme.CyanPrimary

/**
 * Visual focus highlight only (border & scale), without adding extra focusable nodes.
 * Use for ready-made Material components (Button, Switch, Chip, Tab, etc.).
 */
fun Modifier.tvFocusVisual(
    shape: Shape = RoundedCornerShape(12.dp),
    focusBorderColor: Color = CyanPrimary,
    focusBorderWidth: Dp = 2.dp,
    focusedScale: Float = 1.03f
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_focus_scale"
    )

    this
        .onFocusChanged { isFocused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(
            width = focusBorderWidth,
            color = if (isFocused) focusBorderColor else Color.Transparent,
            shape = shape
        )
}

/**
 * Focusable and clickable modifier for custom Cards, Rows, or Boxes on Android TV / D-Pad.
 *
 * `clickable` already is a single focus target that handles DPAD_CENTER/ENTER. The focus visual
 * goes before it so its focus observer sees that target (an extra `focusable()` in front would
 * create a second, outer focus target that swallows OK presses and never shows the highlight).
 */
fun Modifier.tvCardFocusable(
    shape: Shape = RoundedCornerShape(12.dp),
    focusBorderColor: Color = CyanPrimary,
    focusBorderWidth: Dp = 2.dp,
    focusedScale: Float = 1.03f,
    onClick: () -> Unit
): Modifier = this
    .tvFocusVisual(
        shape = shape,
        focusBorderColor = focusBorderColor,
        focusBorderWidth = focusBorderWidth,
        focusedScale = focusedScale
    )
    .clickable(onClick = onClick)
