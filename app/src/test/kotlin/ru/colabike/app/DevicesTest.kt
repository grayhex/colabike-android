package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.devices.DevicesUiState
import ru.colabike.app.devices.DevicesViewModel
import ru.colabike.app.devices.describeUserAgent
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.DataError

class DevicesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val sessions = FakeSessions()

    private fun loaded(vm: DevicesViewModel) = vm.state.value as DevicesUiState.Loaded

    @Test
    fun `it shows the sessions in the server's order`() = runTest {
        val vm = DevicesViewModel(sessions)

        assertThat(loaded(vm).sessions).containsExactly(thisDevice, browser).inOrder()
    }

    @Test
    fun `this device cannot be ended from the list`() = runTest {
        val vm = DevicesViewModel(sessions)

        vm.askToEnd(thisDevice.id)

        assertThat(loaded(vm).confirming).isNull()
    }

    @Test
    fun `asking sends nothing, confirming ends the session and tells so`() = runTest {
        val vm = DevicesViewModel(sessions)

        vm.askToEnd(browser.id)
        assertThat(loaded(vm).confirming).isEqualTo(browser)
        assertThat(sessions.revoked).isEmpty()

        vm.confirmEnd()

        assertThat(sessions.revoked).containsExactly(browser.id)
        assertThat(loaded(vm).sessions).containsExactly(thisDevice)
        assertThat(loaded(vm).confirming).isNull()
        assertThat(loaded(vm).ending).isEmpty()
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.devices_ended))
    }

    @Test
    fun `dismissing the question changes nothing`() = runTest {
        val vm = DevicesViewModel(sessions)
        vm.askToEnd(browser.id)

        vm.dismissQuestion()
        vm.confirmEnd() // nothing to confirm any more

        assertThat(loaded(vm).confirming).isNull()
        assertThat(sessions.revoked).isEmpty()
        assertThat(loaded(vm).sessions).hasSize(2)
    }

    @Test
    fun `a failed end keeps the session and says why`() = runTest {
        val vm = DevicesViewModel(sessions)
        vm.askToEnd(browser.id)
        sessions.nextError = DataError.Offline(java.io.IOException())

        vm.confirmEnd()

        assertThat(loaded(vm).sessions).hasSize(2)
        assertThat(loaded(vm).ending).isEmpty()
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.error_offline))
    }

    @Test
    fun `a session that is already gone disappears from the list`() = runTest {
        val vm = DevicesViewModel(sessions)
        vm.askToEnd(browser.id)
        sessions.nextError = DataError.NotFound()

        vm.confirmEnd()

        assertThat(loaded(vm).sessions).containsExactly(thisDevice)
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.devices_already_gone))
    }

    @Test
    fun `a failed list is an error that load() recovers from`() = runTest {
        sessions.nextError = DataError.Server(502, "req-7")
        val vm = DevicesViewModel(sessions)
        assertThat(vm.state.value)
            .isEqualTo(DevicesUiState.Failed(UiText.Res(R.string.error_server, listOf("req-7"))))

        vm.load()

        assertThat(vm.state.value).isInstanceOf(DevicesUiState.Loaded::class.java)
    }
}

class UserAgentTest {
    private fun label(ua: String) = describeUserAgent(ua).label

    @Test
    fun `common browsers are named with their system`() {
        assertThat(
                label(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
                )
            )
            .isEqualTo("Chrome · Windows")
        assertThat(
                label(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
                )
            )
            .isEqualTo("Safari · macOS")
        assertThat(label("Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"))
            .isEqualTo("Firefox · Linux")
        assertThat(
                label(
                    "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
                )
            )
            .isEqualTo("Chrome · Android")
    }

    @Test
    fun `the specific names win over the generic ones they imitate`() {
        // Edge, Opera and the Yandex browser all say "Chrome" and "Safari" as well.
        val chrome = "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        assertThat(label("Mozilla/5.0 (Windows NT 10.0) $chrome Edg/141.0.0.0"))
            .isEqualTo("Edge · Windows")
        assertThat(label("Mozilla/5.0 (Windows NT 10.0) $chrome OPR/120.0.0.0"))
            .isEqualTo("Opera · Windows")
        assertThat(label("Mozilla/5.0 (Windows NT 10.0) $chrome YaBrowser/25.8.0.0 Safari/537.36"))
            .isEqualTo("Яндекс Браузер · Windows")
    }

    @Test
    fun `an unknown header names nothing instead of guessing`() {
        assertThat(label("")).isNull()
        assertThat(label("curl/8.5.0")).isNull()
        assertThat(describeUserAgent("")).isEqualTo(ru.colabike.app.devices.BrowserInfo(null, null))
    }
}
