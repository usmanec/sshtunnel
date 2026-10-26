@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

abstract class ViewModel
open class AndroidViewModel(private val application: android.app.Application) : ViewModel()

val ViewModel.viewModelScope: CoroutineScope get() = MainScope()

abstract class Lifecycle {
    enum class State { DESTROYED, INITIALIZED, CREATED, STARTED, RESUMED }
}

interface LifecycleOwner {
    val lifecycle: Lifecycle
}

suspend fun Lifecycle.repeatOnLifecycle(state: Lifecycle.State, block: suspend CoroutineScope.() -> Unit) {}
