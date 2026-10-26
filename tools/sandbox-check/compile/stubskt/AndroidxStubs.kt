@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext = staticCompositionLocalOf<android.content.Context> { error("stub") }

val LocalConfiguration = staticCompositionLocalOf<android.content.res.Configuration> { error("stub") }
