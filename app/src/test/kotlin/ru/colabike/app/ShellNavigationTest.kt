package ru.colabike.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Page

/**
 * Bike → another section → Bike finds the same bike, and the list the same place: the acceptance of
 * the navigation shell, on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class ShellNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val bikes = FakeBikes(mapOf(null to Page(bikes(0, 8), null)))

    @Before
    fun start() {
        compose.setContent { ColaBikeTheme { AppShell(FakeDependencies(bikes = bikes)) } }
        compose.waitForIdle()
    }

    private fun section(name: String) = compose.section(name)

    private fun bike(number: Int) =
        compose.onNodeWithContentDescription("Велосипед $number", substring = true)

    @Test
    fun `the list keeps its place after a visit to another section`() {
        compose.onNodeWithTag("bikes:grid").performScrollToIndex(6)
        bike(6).assertIsDisplayed()

        section("Профиль").performClick()
        section("Велосипеды").performClick()

        bike(6).assertIsDisplayed()
    }

    @Test
    fun `a second tap on the current section scrolls the list to the top`() {
        compose.onNodeWithTag("bikes:grid").performScrollToIndex(6)

        section("Велосипеды").performClick()
        compose.waitForIdle()

        bike(0).assertIsDisplayed()
    }

    @Test
    fun `sections are tabs with the current one selected`() {
        section("Велосипеды").assert(isTab).assertIsSelected()
        section("Профиль").assert(isTab).assertIsNotSelected()

        section("Профиль").performClick()

        section("Профиль").assertIsSelected()
        section("Велосипеды").assertIsNotSelected()
    }

    @Test
    fun `controls are at least 48 dp, a bike card is one button, a title is a heading`() {
        section("Велосипеды").assertLaidOutAtLeast(48.dp)
        section("Профиль").assertLaidOutAtLeast(48.dp)
        compose.onNodeWithText("Мои").assertTouchTargetAtLeast(48.dp)
        bike(0).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose
            .onNode(
                hasText("Велосипеды") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
            )
            .assertIsDisplayed()
    }
}

/**
 * The same, on a window wide enough for the rail: on a phone the bar steps aside for the page of a
 * bike, so a visit to another section from an open bike is made at the side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w700dp-h900dp-xhdpi")
class ShellNavigationRailTest {
    @get:Rule val compose = createComposeRule()

    private val bikes = FakeBikes(mapOf(null to Page(bikes(0, 8), null)))

    @Before
    fun start() {
        compose.setContent { ColaBikeTheme { AppShell(FakeDependencies(bikes = bikes)) } }
        compose.waitForIdle()
    }

    private fun section(name: String) = compose.section(name)

    private fun bike(number: Int) =
        compose.onNodeWithContentDescription("Велосипед $number", substring = true)

    @Test
    fun `a bike stays open, and is not loaded again, after a visit to another section`() {
        bike(1).performClick()
        compose.onNodeWithText("Описание").assertIsDisplayed()

        section("Профиль").performClick()
        compose.onNodeWithText("Тестовый Райдер").assertIsDisplayed()
        compose.onNodeWithText("Описание").assertDoesNotExist()

        section("Велосипеды").performClick()
        compose.onNodeWithText("Описание").assertIsDisplayed()
        assertThat(bikes.detailCalls).isEqualTo(1)
    }

    @Test
    fun `a tap on the current section closes the bike`() {
        bike(1).performClick()
        compose.onNodeWithText("Описание").assertIsDisplayed()

        section("Велосипеды").performClick()

        compose.onNodeWithText("Описание").assertDoesNotExist()
        bike(0).assertIsDisplayed()
    }

    @Test
    fun `back from another section returns to the start section with its bike`() {
        bike(1).performClick()
        section("Профиль").performClick()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithText("Описание").assertIsDisplayed()
    }
}

/**
 * Our own element, as laid out, is at least [size] each way. The touch bounds would not do for it:
 * Compose widens those to 48 dp on its own, so a small element would still pass while its
 * neighbours overlap it.
 */
private fun SemanticsNodeInteraction.assertLaidOutAtLeast(size: Dp): SemanticsNodeInteraction =
    assertWidthIsAtLeast(size).assertHeightIsAtLeast(size)

/**
 * The area that reacts to a finger is at least [size] each way. For Material components, which draw
 * smaller than 48 dp and get the rest from `minimumInteractiveComponentSize`.
 */
private fun SemanticsNodeInteraction.assertTouchTargetAtLeast(size: Dp): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    val minimum = with(node.layoutInfo.density) { size.toPx() }
    assertThat(node.touchBoundsInRoot.width).isAtLeast(minimum)
    assertThat(node.touchBoundsInRoot.height).isAtLeast(minimum)
    return this
}
