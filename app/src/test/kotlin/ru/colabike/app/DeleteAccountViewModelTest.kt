package ru.colabike.app

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.account.DELETE_WORD
import ru.colabike.app.account.DeleteAccountEvent
import ru.colabike.app.account.DeleteAccountUiState
import ru.colabike.app.account.DeleteAccountViewModel
import ru.colabike.app.auth.YandexReauth
import ru.colabike.app.ui.UiText
import ru.colabike.core.auth.AuthState
import ru.colabike.core.auth.YandexSignIn
import ru.colabike.core.model.AccountDeletion
import ru.colabike.core.model.DataError
import ru.colabike.core.model.DeletionProof

class DeleteAccountViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val deletion = FakeAccountDeletion()
    private val auth = FakeAuth()
    private val code = "cola_ac_" + "b".repeat(43)

    private fun viewModel() = DeleteAccountViewModel(deletion, auth)

    private fun ready(vm: DeleteAccountViewModel) = vm.state.value as DeleteAccountUiState.Ready

    private val rejected = DataError.Rejected(401, "invalid_credentials", "Пароль не подходит.")

    @Test
    fun `an account with a password is asked for the word and the password, nothing is sent before`() =
        runTest {
            val vm = viewModel()

            assertThat(ready(vm).method).isEqualTo(AccountDeletion.Method.Password)
            assertThat(ready(vm).canSubmit).isFalse()
            vm.onConfirmationChange("удалить")
            assertThat(ready(vm).canSubmit).isFalse() // the password is still missing
            vm.onPasswordChange("пароль-1")
            assertThat(ready(vm).canSubmit).isTrue()
            assertThat(deletion.proofs).isEmpty()
        }

    @Test
    fun `a wrong word is no word, whatever else is given`() = runTest {
        val vm = viewModel()
        vm.onPasswordChange("пароль-1")

        for (word in listOf("", "УДАЛИТ", "ДА", "удалить аккаунт")) {
            vm.onConfirmationChange(word)
            assertThat(ready(vm).canSubmit).isFalse()
            vm.delete()
        }

        assertThat(deletion.proofs).isEmpty()
        vm.onConfirmationChange(" $DELETE_WORD ")
        assertThat(ready(vm).canSubmit).isTrue()
    }

    @Test
    fun `deleting sends the password once and the app forgets the person as at sign-out`() =
        runTest {
            val vm = viewModel()
            vm.onConfirmationChange(DELETE_WORD)
            vm.onPasswordChange("пароль-1")

            vm.delete()

            val proof = deletion.proofs.single() as DeletionProof.Password
            assertThat(proof.value).isEqualTo("пароль-1")
            assertThat(auth.deletedAccounts).isEqualTo(1)
            assertThat(auth.signOuts).isEqualTo(0) // nothing to revoke: the account is gone
            assertThat(auth.state.value).isEqualTo(AuthState.SignedOut)
        }

    @Test
    fun `a wrong password leaves the account and the session alone and says so`() = runTest {
        deletion.terms = AccountDeletion(AccountDeletion.Method.Password, true, null)
        val vm = viewModel()
        vm.onConfirmationChange(DELETE_WORD)
        vm.onPasswordChange("не тот")
        deletion.nextError = rejected

        vm.delete()

        assertThat(auth.deletedAccounts).isEqualTo(0)
        assertThat(auth.state.value).isInstanceOf(AuthState.SignedIn::class.java)
        assertThat(ready(vm).busy).isFalse()
        assertThat(ready(vm).error).isEqualTo(UiText.Res(R.string.delete_account_wrong_password))
        // Fixing the password is enough: it is still there.
        assertThat(ready(vm).password).isEqualTo("не тот")
    }

    @Test
    fun `a failure of the network is told as the network's and the account stays`() = runTest {
        val vm = viewModel()
        vm.onConfirmationChange(DELETE_WORD)
        vm.onPasswordChange("пароль-1")
        deletion.nextError = DataError.Offline(java.io.IOException("down"))

        vm.delete()

        assertThat(ready(vm).error).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(auth.deletedAccounts).isEqualTo(0)
    }

    @Test
    fun `the state never prints the password`() = runTest {
        val vm = viewModel()
        vm.onPasswordChange("очень-секретный-пароль")

        assertThat(ready(vm).toString()).doesNotContain("очень-секретный-пароль")
    }

    @Test
    fun `an account without a password opens the browser first and sends the confirmation it brings`() =
        runTest {
            deletion.terms = AccountDeletion(AccountDeletion.Method.Yandex, true, null)
            val vm = viewModel()
            vm.onConfirmationChange(DELETE_WORD)

            vm.events.test {
                vm.delete()
                assertThat(awaitItem()).isEqualTo(DeleteAccountEvent.OpenProvider)
                assertThat(ready(vm).waitingForProvider).isTrue()
                assertThat(deletion.proofs).isEmpty()

                auth.reauths.emit(YandexReauth.Proof(code, "v".repeat(43)))
                cancelAndIgnoreRemainingEvents()
            }

            val proof = deletion.proofs.single() as DeletionProof.Provider
            assertThat(proof.code).isEqualTo(code)
            assertThat(auth.deletedAccounts).isEqualTo(1)
        }

    @Test
    fun `a confirmation that arrives before the word waits for the word`() = runTest {
        deletion.terms = AccountDeletion(AccountDeletion.Method.Yandex, true, null)
        val vm = viewModel()

        auth.reauths.emit(YandexReauth.Proof(code, "v".repeat(43)))

        assertThat(ready(vm).providerConfirmed).isTrue()
        assertThat(deletion.proofs).isEmpty()
        vm.onConfirmationChange(DELETE_WORD)
        vm.delete()
        assertThat(deletion.proofs.single()).isInstanceOf(DeletionProof.Provider::class.java)
    }

    @Test
    fun `a confirmation the server did not accept is spent, so the person signs in with Yandex again`() =
        runTest {
            deletion.terms = AccountDeletion(AccountDeletion.Method.Yandex, true, null)
            val vm = viewModel()
            vm.onConfirmationChange(DELETE_WORD)
            deletion.nextError = rejected

            // The word was typed already, so the confirmation is sent as soon as it arrives.
            auth.reauths.emit(YandexReauth.Proof(code, "v".repeat(43)))

            assertThat(deletion.proofs).hasSize(1)
            assertThat(auth.deletedAccounts).isEqualTo(0)
            assertThat(ready(vm).providerConfirmed).isFalse()
            assertThat(ready(vm).error)
                .isEqualTo(UiText.Res(R.string.delete_account_wrong_provider))
            vm.events.test {
                vm.delete() // the spent code is not sent again: the browser is asked again
                assertThat(awaitItem()).isEqualTo(DeleteAccountEvent.OpenProvider)
            }
            assertThat(deletion.proofs).hasSize(1)
        }

    @Test
    fun `a cancelled browser says so and deletes nothing`() = runTest {
        deletion.terms = AccountDeletion(AccountDeletion.Method.Yandex, true, null)
        val vm = viewModel()
        vm.onConfirmationChange(DELETE_WORD)
        vm.delete()

        auth.reauths.emit(YandexReauth.Failed(YandexSignIn.Reason.Cancelled))

        assertThat(ready(vm).waitingForProvider).isFalse()
        assertThat(ready(vm).error)
            .isEqualTo(UiText.Res(R.string.delete_account_provider_cancelled))
        assertThat(deletion.proofs).isEmpty()
    }

    @Test
    fun `an administrator, an account with no way to confirm and an unknown reason are told what to do`() =
        runTest {
            for ((reason, text) in
                listOf(
                    AccountDeletion.Reason.Admin to R.string.delete_account_admin,
                    AccountDeletion.Reason.NoMethod to R.string.delete_account_no_method,
                    AccountDeletion.Reason.Other to R.string.delete_account_unavailable,
                )) {
                deletion.terms = AccountDeletion(null, false, reason)

                val vm = viewModel()

                assertThat(vm.state.value)
                    .isEqualTo(DeleteAccountUiState.Unavailable(UiText.Res(text)))
            }
        }

    @Test
    fun `when the terms cannot be read the screen offers to try again`() = runTest {
        deletion.nextError = DataError.Offline(java.io.IOException("down"))

        val vm = viewModel()

        assertThat(vm.state.value).isInstanceOf(DeleteAccountUiState.Failed::class.java)
        vm.load()
        assertThat(vm.state.value).isInstanceOf(DeleteAccountUiState.Ready::class.java)
        assertThat(deletion.termsCalls).isEqualTo(2)
    }
}
