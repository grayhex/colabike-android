package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.links.LinkOpener
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.navigation.Destination
import ru.colabike.app.settings.ThemeMode
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.DataError

/**
 * The account side of the app as a person meets it, on a phone: the first screen, looking around as
 * a guest, signing in from a guest's place, the devices of the account, the theme, the pages that
 * open in the browser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class AccountFlowTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun start(dependencies: FakeDependencies) {
        compose.setContent {
            CompositionLocalProvider(LocalLinkOpener provides LinkOpener { opened += it }) {
                ColaBikeTheme { ColaBikeApp(dependencies) }
            }
        }
        compose.waitForIdle()
    }

    private fun section(name: String) = compose.section(name)

    private fun guestApp() =
        FakeDependencies(
            auth = FakeAuth(initial = AuthState.SignedOut),
            settings = FakeSettings(guest = true),
        )

    // --- first screen, guest ---------------------------------------------------------------

    @Test
    fun `a fresh start asks to sign in and offers to look around`() {
        start(FakeDependencies(auth = FakeAuth(initial = AuthState.SignedOut)))

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        compose.onNodeWithText("Смотреть без входа").assertIsDisplayed()
    }

    @Test
    fun `looking around shows the public bikes without the account-only choice`() {
        val dependencies = FakeDependencies(auth = FakeAuth(initial = AuthState.SignedOut))
        start(dependencies)

        compose.onNodeWithText("Смотреть без входа").performClick()
        compose.waitForIdle()

        assertThat(dependencies.settings.browsingAsGuest.value).isTrue()
        compose.onNodeWithContentDescription("Велосипед 0", substring = true).assertIsDisplayed()
        // "Mine" needs an account.
        compose.onNodeWithText("Мои").assertDoesNotExist()
        // A guest asked nothing of the account.
        assertThat(dependencies.bikes.calls.map { it.first.scope })
            .containsExactly(ru.colabike.core.model.BikeScope.Public)
    }

    @Test
    fun `a guest's profile is an invitation, not an account`() {
        val dependencies = guestApp()
        start(dependencies)

        section("Профиль").performClick()

        compose.onNodeWithText("Вы смотрите как гость").assertIsDisplayed()
        compose.onNodeWithText("Устройства и входы").assertDoesNotExist()
        compose.onNodeWithText("Выйти").assertDoesNotExist()
        // Nothing of an account was asked for.
        assertThat(dependencies.account.calls).isEqualTo(0)
        // The theme and the "about" are for everyone.
        compose.onNodeWithText("Тёмная").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("О приложении").performScrollTo().assertIsDisplayed()
    }

    // --- signing in from a guest's place ---------------------------------------------------

    @Test
    fun `sign-in opened by a guest closes back to the same place`() {
        start(guestApp())
        section("Профиль").performClick()

        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        // No second invitation to look around: the guest is already looking.
        compose.onNodeWithText("Смотреть без входа").assertDoesNotExist()

        compose.onNodeWithContentDescription("Закрыть вход").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вы смотрите как гость").assertIsDisplayed()
    }

    @Test
    fun `Back closes the sign-in a guest opened`() {
        start(guestApp())
        section("Профиль").performClick()
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithText("Вы смотрите как гость").assertIsDisplayed()
    }

    @Test
    fun `a guest who signs in is where they were, as a member`() {
        val dependencies = guestApp()
        start(dependencies)
        section("Профиль").performClick()
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Почта").performTextInput("rider@example.test")
        compose.onNodeWithText("Пароль").performTextInput("password")
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()

        assertThat(dependencies.auth.signIns).containsExactly("rider@example.test" to "password")
        // Still on the profile, which is now the account's.
        compose.onNodeWithText("Тестовый Райдер").assertIsDisplayed()
        compose.onNodeWithText("Вы смотрите как гость").assertDoesNotExist()
        // "A guest" ends with signing in: after a sign-out the app asks again.
        assertThat(dependencies.settings.browsingAsGuest.value).isFalse()
    }

    @Test
    fun `a member sees the account choice on bikes, a guest who signs in gets it`() {
        val dependencies = guestApp()
        start(dependencies)
        compose.onNodeWithText("Мои").assertDoesNotExist()

        dependencies.auth.state.value = AuthState.SignedIn(account)
        compose.waitForIdle()

        compose.onNodeWithText("Мои").assertIsDisplayed()
    }

    @Test
    fun `signing out leads to sign-in, not to the guest's view`() {
        val dependencies = FakeDependencies()
        start(dependencies)
        section("Профиль").performClick()

        compose.onNodeWithText("Выйти").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(dependencies.auth.signOuts).isEqualTo(1)
    }

    // --- devices ---------------------------------------------------------------------------

    private fun openDevices(dependencies: FakeDependencies) {
        start(dependencies)
        section("Профиль").performClick()
        compose.onNodeWithText("Устройства и входы").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the devices list marks this device and offers to end the others`() {
        openDevices(FakeDependencies())

        compose.onNodeWithText("Google Pixel 9").assertIsDisplayed()
        // The badge is set in capitals, as the reference sets its captions.
        compose.onNodeWithText("ЭТО УСТРОЙСТВО").assertIsDisplayed()
        compose.onNodeWithText("Chrome · Windows").assertIsDisplayed()
        // One "end" action: for the browser, none for this device.
        compose
            .onNodeWithContentDescription("Завершить сеанс: Chrome · Windows")
            .assertIsDisplayed()
        compose
            .onNode(hasContentDescription("Завершить сеанс: Google Pixel 9"))
            .assertDoesNotExist()
    }

    @Test
    fun `ending another session asks first, then removes it`() {
        val dependencies = FakeDependencies()
        openDevices(dependencies)

        compose.onNodeWithContentDescription("Завершить сеанс: Chrome · Windows").performClick()
        compose.onNodeWithText("Завершить сеанс?").assertIsDisplayed()
        // Asking sends nothing.
        assertThat(dependencies.sessions.revoked).isEmpty()

        compose.onNode(hasText("Завершить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertThat(dependencies.sessions.revoked).containsExactly(browser.id)
        compose.onNodeWithText("Chrome · Windows").assertDoesNotExist()
        compose.onNodeWithText("Сеанс завершён").assertIsDisplayed()
        compose.onNodeWithText("Google Pixel 9").assertIsDisplayed()
    }

    @Test
    fun `cancelling the question keeps the session`() {
        val dependencies = FakeDependencies()
        openDevices(dependencies)

        compose.onNodeWithContentDescription("Завершить сеанс: Chrome · Windows").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.waitForIdle()

        assertThat(dependencies.sessions.revoked).isEmpty()
        compose.onNodeWithText("Chrome · Windows").assertIsDisplayed()
    }

    @Test
    fun `a failed list can be retried and Back returns to the profile`() {
        val dependencies = FakeDependencies()
        dependencies.sessions.nextError = DataError.Offline(java.io.IOException())
        openDevices(dependencies)
        compose
            .onNodeWithText("Нет соединения. Проверьте интернет и повторите.")
            .assertIsDisplayed()

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Google Pixel 9").assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Тестовый Райдер").assertIsDisplayed()
    }

    // --- theme, pages in the browser -------------------------------------------------------

    @Test
    fun `the theme choice is kept in the settings`() {
        val dependencies = FakeDependencies()
        start(dependencies)
        section("Профиль").performClick()

        compose.onNodeWithText("Тёмная").performScrollTo().performClick()
        assertThat(dependencies.settings.themeMode.value).isEqualTo(ThemeMode.Dark)

        compose.onNodeWithText("Как в системе").performScrollTo().performClick()
        assertThat(dependencies.settings.themeMode.value).isEqualTo(ThemeMode.System)
    }

    @Test
    fun `registration and recovery open on the site, not in the app`() {
        start(FakeDependencies(auth = FakeAuth(initial = AuthState.SignedOut)))

        compose.onNode(hasText("Регистрация") and hasClickAction()).performClick()
        compose.onNode(hasText("Забыли пароль?") and hasClickAction()).performClick()

        assertThat(opened)
            .containsExactly(
                "https://colabike.test/register",
                "https://colabike.test/forgot-password",
            )
            .inOrder()
    }

    @Test
    fun `account management, the terms and the privacy policy open on the site`() {
        start(FakeDependencies())
        section("Профиль").performClick()
        compose.onNodeWithText("Управление аккаунтом").performScrollTo().performClick()

        compose.onNodeWithText("О приложении").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Условия использования").performClick()
        compose.onNodeWithText("Политика конфиденциальности").performClick()

        assertThat(opened)
            .containsExactly(
                "https://colabike.test/account?tab=account",
                "https://colabike.test/legal/terms",
                "https://colabike.test/legal/privacy",
            )
            .inOrder()
    }

    @Test
    fun `the licences are readable inside the app`() {
        start(FakeDependencies())
        section("Профиль").performClick()
        compose.onNodeWithText("О приложении").performScrollTo().performClick()
        compose.onNodeWithText("Лицензии").performClick()
        compose.waitForIdle()

        // The texts are read from the resources on a background thread.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Lora").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Lora").assertIsDisplayed()
        // The cards are long (the whole licence text); the rest are one scroll away.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Apache License 2.0"))
        compose.onNodeWithText("Apache License 2.0").assertIsDisplayed()
    }

    // --- links through sign-in and a guest's view -------------------------------------------

    @Test
    fun `a link that arrived before sign-in is carried out after it`() {
        val dependencies = FakeDependencies(auth = FakeAuth(initial = AuthState.SignedOut))
        dependencies.pending.offer(Destination.Bike("b1"))
        start(dependencies)
        // Sign-in is on screen and the place waits.
        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(dependencies.pending.destination.value).isNotNull()

        compose.onNodeWithText("Почта").performTextInput("rider@example.test")
        compose.onNodeWithText("Пароль").performTextInput("password")
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Описание").assertIsDisplayed()
        assertThat(dependencies.pending.destination.value).isNull()
    }

    @Test
    fun `a link that arrived before a guest chose to look around opens for the guest`() {
        val dependencies = FakeDependencies(auth = FakeAuth(initial = AuthState.SignedOut))
        dependencies.pending.offer(Destination.Bike("b1"))
        start(dependencies)

        compose.onNodeWithText("Смотреть без входа").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Описание").assertIsDisplayed()
        assertThat(dependencies.pending.destination.value).isNull()
    }

    @Test
    fun `a link while the app is open takes the person to the bike at once`() {
        val dependencies = FakeDependencies()
        start(dependencies)
        section("Профиль").performClick()

        dependencies.pending.offer(Destination.Bike("b2"))
        compose.waitForIdle()

        compose.onNodeWithText("Описание").assertIsDisplayed()
        // Back is the bikes list, not the profile the person came from.
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Велосипед 0", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a bike a guest cannot see offers to sign in, and signing in returns to it`() {
        val dependencies =
            FakeDependencies(
                auth = FakeAuth(initial = AuthState.SignedOut),
                settings = FakeSettings(guest = true),
            )
        dependencies.pending.offer(Destination.Bike("private-one"))
        start(dependencies)

        compose
            .onNodeWithText("Если это приватный велосипед, войдите в свой аккаунт.")
            .assertIsDisplayed()
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()

        compose.onNodeWithContentDescription("Закрыть вход").performClick()
        compose.waitForIdle()
        // The guest is where they were.
        compose
            .onNodeWithText("Если это приватный велосипед, войдите в свой аккаунт.")
            .assertIsDisplayed()
    }

    @Test
    fun `a missing bike does not suggest signing in to someone who is signed in`() {
        val dependencies = FakeDependencies()
        dependencies.pending.offer(Destination.Bike("gone"))
        start(dependencies)

        compose.onNodeWithText("Не нашли: возможно, его скрыли или удалили.").assertIsDisplayed()
        compose
            .onNodeWithText("Если это приватный велосипед, войдите в свой аккаунт.")
            .assertDoesNotExist()
    }

    @Test
    fun `when the session ends for good the person lands on sign-in`() {
        val dependencies = FakeDependencies()
        start(dependencies)
        section("Профиль").performClick()

        // The interceptor found invalid_token and ended the session: nobody pressed "Выйти".
        dependencies.auth.state.value = AuthState.SignedOut
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
    }
}
