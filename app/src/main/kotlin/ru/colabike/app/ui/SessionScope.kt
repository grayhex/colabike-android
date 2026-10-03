package ru.colabike.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * ViewModels of the signed-in app live in a store that belongs to the session, not to the activity:
 * after sign-out nothing of the previous account survives into the next one (a profile, a list),
 * whatever Navigation 3 does with its entries, while rotation keeps all.
 */
class SessionStores : ViewModel() {
    private var store = ViewModelStore()

    val current: ViewModelStore
        get() = store

    /** Drops every ViewModel of the ended session. */
    fun end() {
        store.clear()
        store = ViewModelStore()
    }

    override fun onCleared() = store.clear()
}

@Composable
fun SessionScope(
    stores: SessionStores = viewModel { SessionStores() },
    content: @Composable () -> Unit,
) {
    val owner =
        remember(stores.current) {
            object : ViewModelStoreOwner {
                override val viewModelStore = stores.current
            }
        }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
