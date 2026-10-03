package ru.colabike.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/** Who is looking at the app: a guest, or a signed-in member. */
enum class Viewer {
    Guest,
    Member,
}

/**
 * ViewModels of the app after the start screen live in a store that belongs to the viewer, not to
 * the activity: when the viewer changes (a guest signs in, a member signs out) nothing of the
 * previous one survives into the next (a profile, a list), whatever Navigation 3 does with its
 * entries, while rotation keeps all.
 */
class SessionStores : ViewModel() {
    private var store = ViewModelStore()
    private var viewer: Viewer? = null

    /** The store of [viewer]; asking for another viewer drops every ViewModel of the previous. */
    fun storeFor(viewer: Viewer): ViewModelStore {
        if (this.viewer != viewer) {
            end()
            this.viewer = viewer
        }
        return store
    }

    /** Drops every ViewModel of the ended session. */
    fun end() {
        store.clear()
        store = ViewModelStore()
        viewer = null
    }

    override fun onCleared() = store.clear()
}

@Composable
fun SessionScope(
    viewer: Viewer,
    stores: SessionStores = viewModel { SessionStores() },
    content: @Composable () -> Unit,
) {
    // Asking inside remember is idempotent: the same viewer gets the same store again.
    val store = remember(viewer, stores) { stores.storeFor(viewer) }
    val owner =
        remember(store) {
            object : ViewModelStoreOwner {
                override val viewModelStore = store
            }
        }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
