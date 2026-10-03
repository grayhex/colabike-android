package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.bikes.BikeDetailUiState
import ru.colabike.app.bikes.BikeDetailViewModel
import ru.colabike.app.bikes.BikesViewModel
import ru.colabike.app.login.LoginViewModel
import ru.colabike.app.profile.ProfileUiState
import ru.colabike.app.profile.ProfileViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.auth.AuthState
import ru.colabike.core.auth.YandexSignIn
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page

class ViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `bikes page through the keyset cursor without duplicates`() = runTest {
        val repo =
            FakeBikes(mapOf(null to Page(bikes(0, 3), "c1"), "c1" to Page(bikes(2, 3), null)))
        val vm = BikesViewModel(repo)
        assertThat(vm.state.value.bikes).hasSize(3)

        vm.loadMore()

        assertThat(vm.state.value.bikes.map { it.id.value })
            .containsExactly("b0", "b1", "b2", "b3", "b4")
            .inOrder()
        assertThat(vm.state.value.nextCursor).isNull()
        vm.loadMore() // the last page: nothing more to ask
        assertThat(repo.calls)
            .containsExactly(BikeScope.Public to null, BikeScope.Public to "c1")
            .inOrder()
    }

    @Test
    fun `a failed first page is a screen error, a failed next page keeps the list`() = runTest {
        val repo = FakeBikes(mapOf(null to Page(bikes(0, 3), "c1")))
        repo.nextError = DataError.Offline(java.io.IOException())
        val vm = BikesViewModel(repo)
        assertThat(vm.state.value.error).isEqualTo(UiText.Res(R.string.error_offline))

        vm.retry()
        repo.nextError = DataError.Server(502, "req-1")
        vm.loadMore()

        assertThat(vm.state.value.bikes).hasSize(3)
        assertThat(vm.state.value.moreError)
            .isEqualTo(UiText.Res(R.string.error_server, listOf("req-1")))
    }

    @Test
    fun `switching to my bikes starts over in that scope`() = runTest {
        val repo = FakeBikes()
        val vm = BikesViewModel(repo)
        vm.selectScope(BikeScope.Mine)
        assertThat(repo.calls.last()).isEqualTo(BikeScope.Mine to null)
        assertThat(vm.state.value.scope).isEqualTo(BikeScope.Mine)
    }

    @Test
    fun `bike detail loads and maps not found to words`() = runTest {
        val repo = FakeBikes()
        assertThat(BikeDetailViewModel(repo, BikeId("b1")).state.value)
            .isInstanceOf(BikeDetailUiState.Loaded::class.java)
        assertThat(BikeDetailViewModel(repo, BikeId("missing")).state.value)
            .isEqualTo(BikeDetailUiState.Failed(UiText.Res(R.string.error_not_found)))
    }

    @Test
    fun `sign-in validates, maps wrong credentials and forgets the password`() = runTest {
        val auth = FakeAuth(AuthState.SignedOut)
        val vm = LoginViewModel(auth)

        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(UiText.Res(R.string.login_empty_fields))

        vm.onEmailChange("rider@example.test")
        vm.onPasswordChange("wrong")
        auth.signInError =
            DataError.Rejected(401, "invalid_credentials", "Неверная почта или пароль")
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(UiText.Res(R.string.login_wrong_credentials))
        assertThat(vm.state.value.busy).isFalse()

        auth.signInError = null
        vm.onPasswordChange("right")
        vm.submit()
        assertThat(auth.state.value).isInstanceOf(AuthState.SignedIn::class.java)
        assertThat(vm.state.value.password).isEmpty()
        assertThat(vm.state.value.toString()).doesNotContain("right")
    }

    @Test
    fun `a failed Yandex return is shown on the sign-in screen`() = runTest {
        val auth = FakeAuth(AuthState.SignedOut)
        val vm = LoginViewModel(auth)
        auth.failures.emit(ru.colabike.app.auth.YandexFailure.Flow(YandexSignIn.Reason.Cancelled))
        assertThat(vm.state.value.error).isEqualTo(UiText.Res(R.string.login_yandex_cancelled))
    }

    @Test
    fun `profile shows me and signs out`() = runTest {
        val auth = FakeAuth()
        val vm = ProfileViewModel(FakeAccount(), auth)
        assertThat((vm.state.value as ProfileUiState.Loaded).account.username)
            .isEqualTo("test-rider")

        vm.signOut()

        assertThat(auth.signOuts).isEqualTo(1)
        assertThat(auth.state.value).isEqualTo(AuthState.SignedOut)
    }

    @Test
    fun `rate limits say how long to wait`() {
        assertThat(DataError.RateLimited(900).toUiText())
            .isEqualTo(UiText.Res(R.string.error_rate_limited, listOf(15)))
    }

    @Test
    fun `a session started any way clears the typed password`() = runTest {
        val auth = FakeAuth(AuthState.SignedOut)
        val vm = LoginViewModel(auth)
        vm.onEmailChange("rider@example.test")
        vm.onPasswordChange("typed-but-unused")
        vm.togglePasswordVisibility()

        // The Yandex ID flow ends outside this ViewModel: only the session state changes.
        auth.state.value = AuthState.SignedIn(account)

        assertThat(vm.state.value.password).isEmpty()
        assertThat(vm.state.value.passwordVisible).isFalse()
        assertThat(vm.state.value.email).isEqualTo("rider@example.test")
    }

    @Test
    fun `a failed refresh keeps the list and is retried as a refresh`() = runTest {
        val repo = FakeBikes(mapOf(null to Page(bikes(0, 3), "c1")))
        val vm = BikesViewModel(repo)

        repo.nextError = DataError.Offline(java.io.IOException())
        vm.refresh()

        assertThat(vm.state.value.bikes).hasSize(3)
        assertThat(vm.state.value.refreshError).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(vm.state.value.moreError).isNull()

        vm.refresh()

        assertThat(vm.state.value.refreshError).isNull()
        assertThat(repo.calls.takeLast(2))
            .containsExactly(BikeScope.Public to null, BikeScope.Public to null)
    }
}
