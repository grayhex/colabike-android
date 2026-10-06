package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.auth.YandexReauth
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.AccountDeletion
import ru.colabike.core.model.DataError
import ru.colabike.core.model.DeletionProof

/**
 * Deleting the account as a person meets it (docs/adr/0021): from the profile, with the word and
 * the proof on one screen, and the app a guest's afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class DeleteAccountFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun open(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
        compose.section("Профиль").performClick()
        compose.onNodeWithText("Удалить аккаунт").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun type(label: String, text: String) {
        compose.onNodeWithText(label).performScrollTo().performTextInput(text)
    }

    @Test
    fun `the profile offers the deletion and the screen says what goes`() {
        open(FakeDependencies())

        compose.onNodeWithText("Аккаунт будет удалён навсегда").assertIsDisplayed()
        compose.onNodeWithText("Слово подтверждения").assertIsDisplayed()
        compose.onNodeWithText("Текущий пароль").assertIsDisplayed()
        // Nothing can be sent before the word and the password are there.
        compose.onNodeWithText("Удалить аккаунт навсегда").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `with the word and the password the account is deleted and the app is a guest's`() {
        val dependencies = FakeDependencies()
        open(dependencies)

        type("Слово подтверждения", "УДАЛИТЬ")
        type("Текущий пароль", "мой-пароль-1")
        compose.onNodeWithText("Удалить аккаунт навсегда").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Удалить аккаунт навсегда").performClick()
        compose.waitForIdle()

        val proof = dependencies.accountDeletion.proofs.single() as DeletionProof.Password
        assertThat(proof.value).isEqualTo("мой-пароль-1")
        assertThat(dependencies.auth.deletedAccounts).isEqualTo(1)
        assertThat(dependencies.auth.state.value).isEqualTo(AuthState.SignedOut)
        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
    }

    @Test
    fun `a wrong password is told on the screen and the account is still there`() {
        val dependencies = FakeDependencies()
        open(dependencies)
        type("Слово подтверждения", "УДАЛИТЬ")
        type("Текущий пароль", "не-тот")
        dependencies.accountDeletion.nextError =
            DataError.Rejected(401, "invalid_credentials", "Пароль не подходит.")

        compose.onNodeWithText("Удалить аккаунт навсегда").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Пароль не подходит. Аккаунт не удалён.").assertIsDisplayed()
        assertThat(dependencies.auth.deletedAccounts).isEqualTo(0)
        assertThat(dependencies.auth.state.value).isInstanceOf(AuthState.SignedIn::class.java)
    }

    @Test
    fun `an account made through Yandex goes through the browser and is deleted on its return`() {
        val dependencies = FakeDependencies()
        dependencies.accountDeletion.terms =
            AccountDeletion(AccountDeletion.Method.Yandex, true, null)
        open(dependencies)

        compose.onNodeWithText("Текущий пароль").assertDoesNotExist()
        type("Слово подтверждения", "УДАЛИТЬ")
        compose.onNodeWithText("Войти через Яндекс и удалить").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(dependencies.auth.reauthStarts).isEqualTo(1)
        compose.onNodeWithText("Ждём подтверждения", substring = true).assertIsDisplayed()
        assertThat(dependencies.accountDeletion.proofs).isEmpty()

        dependencies.auth.reauths.tryEmit(
            YandexReauth.Proof("cola_ac_" + "c".repeat(43), "v".repeat(43))
        )
        compose.waitForIdle()

        assertThat(dependencies.accountDeletion.proofs.single())
            .isInstanceOf(DeletionProof.Provider::class.java)
        assertThat(dependencies.auth.deletedAccounts).isEqualTo(1)
    }

    @Test
    fun `an administrator is told to hand the rights over and gets no form`() {
        val dependencies = FakeDependencies()
        dependencies.accountDeletion.terms =
            AccountDeletion(null, false, AccountDeletion.Reason.Admin)
        open(dependencies)

        compose.onNodeWithText("Вы администратор", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Слово подтверждения").assertDoesNotExist()
    }
}
