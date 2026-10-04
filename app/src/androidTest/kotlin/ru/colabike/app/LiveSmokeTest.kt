package ru.colabike.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The live smoke of #325 on an Android 17 emulator against production: sign in with the dedicated
 * smoke account, the bike list (`/bikes`), a bike (`/bikes/{id}`), the rides (`/rides/upcoming`,
 * `/rides`, and a ride's page if there is one), the profile (`/me`), the devices of the account
 * (`/auth/sessions`), sign out (the device session is revoked, the account does not collect
 * devices). Everything it asks for is read-only.
 *
 * Credentials come only as instrumentation arguments from CI secrets (smokeEmail, smokePassword);
 * without them the test is skipped, never faked. CI leaves the class out instead (notClass): AGP's
 * test engine reports an assumption failure as a failure.
 */
@RunWith(AndroidJUnit4::class)
class LiveSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val arguments = InstrumentationRegistry.getArguments()
    private val email = arguments.getString("smokeEmail").orEmpty()
    private val password = arguments.getString("smokePassword").orEmpty()

    private val bikeCard =
        SemanticsMatcher("a bike card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("bike:") == true
        }

    private val rideCard =
        SemanticsMatcher("a ride card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("ride:") == true
        }

    /** A section tab: with more than three sections only the selected one has its text. */
    private fun sectionTab(name: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(name)) or
                SemanticsMatcher("text $name") {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == name } ==
                        true
                })

    private fun waitFor(timeoutMs: Long = 30_000, condition: () -> Boolean) =
        compose.waitUntil(timeoutMs, condition)

    private fun hasText(text: String) =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun signInBikesBikeProfileSignOut() {
        assumeTrue(
            "smoke credentials are not configured",
            email.isNotBlank() && password.isNotBlank(),
        )

        waitFor { hasText("Вход в ColaBike") }
        compose.onNodeWithText("Почта").performTextInput(email)
        compose.onNodeWithText("Пароль").performTextInput(password)
        compose.onNodeWithText("Войти").performClick()

        // /bikes through the Bearer token.
        waitFor { compose.onAllNodes(bikeCard).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(bikeCard).onFirst().performClick()

        // /bikes/{id}: the detail pane with its back button (a phone is one pane).
        waitFor {
            compose
                .onAllNodes(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.ContentDescription,
                        listOf("Назад"),
                    )
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Назад").performClick()

        // /rides/upcoming and /rides: plans may be none, a completed public ride is opened if any.
        compose.onNode(sectionTab("Покатушки")).performClick()
        waitFor {
            compose.onAllNodes(rideCard).fetchSemanticsNodes().isNotEmpty() ||
                hasText("Ближайших планов нет")
        }
        compose.onNodeWithText("Состоявшиеся").performClick()
        waitFor {
            compose.onAllNodes(rideCard).fetchSemanticsNodes().isNotEmpty() ||
                hasText("Покатушек пока нет")
        }
        if (compose.onAllNodes(rideCard).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(rideCard).onFirst().performClick()
            // /rides/{id}: the page with its back button.
            waitFor {
                compose
                    .onAllNodes(
                        SemanticsMatcher.expectValue(
                            SemanticsProperties.ContentDescription,
                            listOf("Назад"),
                        )
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithContentDescription("Назад").performClick()
        }

        // /me: the profile shows the account.
        compose.onNode(sectionTab("Профиль")).performClick()
        waitFor { hasText("Устройства и входы") }

        // /auth/sessions: this device is in the list, marked as this one.
        compose.onNodeWithText("Устройства и входы").performScrollTo().performClick()
        waitFor { hasText("ЭТО УСТРОЙСТВО") }
        compose.onNodeWithContentDescription("Назад").performClick()

        waitFor { hasText("Выйти") }
        compose.onNodeWithText("Выйти").performScrollTo().performClick()
        waitFor { hasText("Вход в ColaBike") }
    }
}
