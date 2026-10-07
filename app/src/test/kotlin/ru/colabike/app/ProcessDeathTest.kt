package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Page

/**
 * The system may kill the app in the background and bring it back to where the person left it: the
 * section, what is open in it, and how "back" works from there. Screens load their data again, so
 * what is checked is the navigation, not the content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class ProcessDeathTest {
    @get:Rule val compose = createComposeRule()

    private val restoration = StateRestorationTester(compose)

    private fun start(dependencies: FakeDependencies = FakeDependencies(bikes = bikes())) {
        restoration.setContent { ColaBikeTheme { AppShell(dependencies) } }
        compose.waitForIdle()
    }

    private fun bikes() = FakeBikes(mapOf(null to Page(bikes(0, 6), null)))

    private fun die() {
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
    }

    @Test
    fun `an open bike is still open, and back still leads to the list`() {
        start()
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.onNodeWithText("Описание").assertIsDisplayed()

        die()

        compose.onNodeWithText("Описание").assertIsDisplayed()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("bikes:grid").assertIsDisplayed()
    }

    @Test
    fun `the section the person was in is the section they return to`() {
        start()
        compose.section("Профиль").performClick()
        compose.onNodeWithText("Тестовый Райдер").assertIsDisplayed()

        die()

        compose.onNodeWithText("Тестовый Райдер").assertIsDisplayed()
        compose.onNodeWithTag("bikes:grid").assertDoesNotExist()
    }

    @Test
    fun `a bike left open in one section waits in it while the person is in another`() {
        start()
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.section("Профиль").performClick()

        die()

        compose.onNodeWithText("Тестовый Райдер").assertIsDisplayed()
        compose.section("Велосипеды").performClick()
        compose.onNodeWithText("Описание").assertIsDisplayed()
    }

    @Test
    fun `a conversation is still open, and so is the box around it`() {
        start()
        compose.section("Чаты").performClick()
        compose.onNodeWithTag("chat:open:dm-1").performClick()
        compose.onNodeWithTag("chat:conversation").assertIsDisplayed()

        die()

        compose.onNodeWithTag("chat:conversation").assertIsDisplayed()
    }
}
