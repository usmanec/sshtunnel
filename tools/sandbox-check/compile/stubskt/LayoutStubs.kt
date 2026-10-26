@file:Suppress("unused")

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable

// Android-only in Compose (WindowInsets.android.kt)
@ExperimentalLayoutApi
val WindowInsets.Companion.isImeVisible: Boolean
    @Composable get() = false
