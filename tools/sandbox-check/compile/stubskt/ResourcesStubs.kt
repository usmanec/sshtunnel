@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.compose.ui.res

import androidx.compose.runtime.Composable

// Android-only in Compose (StringResources.android.kt)
@Composable
fun stringResource(id: Int): String = ""

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = ""
