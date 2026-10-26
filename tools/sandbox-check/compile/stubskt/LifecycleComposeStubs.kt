@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.lifecycle.compose

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.LifecycleOwner

val LocalLifecycleOwner = staticCompositionLocalOf<LifecycleOwner> { error("stub") }
