@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.lifecycle.viewmodel.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel

@Composable
inline fun <reified VM : ViewModel> viewModel(key: String? = null): VM = error("stub")
