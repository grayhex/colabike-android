package ru.colabike.app

import androidx.lifecycle.ViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.colabike.app.ui.SessionStores
import ru.colabike.app.ui.Viewer

class SessionStoresTest {
    private class Marker : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun `the same viewer keeps its ViewModels`() {
        val stores = SessionStores()
        val first = stores.storeFor(Viewer.Guest)

        assertThat(stores.storeFor(Viewer.Guest)).isSameInstanceAs(first)
    }

    @Test
    fun `another viewer starts empty and the previous one's ViewModels are cleared`() {
        val stores = SessionStores()
        val guestStore = stores.storeFor(Viewer.Guest)
        val marker = Marker()
        guestStore.put("marker", marker)

        val memberStore = stores.storeFor(Viewer.Member)

        assertThat(memberStore).isNotSameInstanceAs(guestStore)
        assertThat(marker.cleared).isTrue()
        assertThat(memberStore.keys()).isEmpty()
    }

    @Test
    fun `ending the session clears the store even for the same viewer`() {
        val stores = SessionStores()
        val marker = Marker()
        stores.storeFor(Viewer.Member).put("marker", marker)

        stores.end()

        assertThat(marker.cleared).isTrue()
        assertThat(stores.storeFor(Viewer.Member).keys()).isEmpty()
    }
}
