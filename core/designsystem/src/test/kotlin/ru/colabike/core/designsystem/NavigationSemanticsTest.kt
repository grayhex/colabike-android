package ru.colabike.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaNavItem
import ru.colabike.core.designsystem.component.ColaNavigationBar
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** What TalkBack and a finger get from the navigation bar and the top bar. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h900dp-xhdpi")
class NavigationSemanticsTest {
    @get:Rule val compose = createComposeRule()

    private val five =
        listOf(
            ColaNavItem("Лента", ColaIcons.Feed, ColaIcons.FeedFilled),
            ColaNavItem("Велосипеды", ColaIcons.Bike, ColaIcons.BikeFilled),
            ColaNavItem("Покатушки", ColaIcons.Route, ColaIcons.Route),
            ColaNavItem("Сообщения", ColaIcons.Chat, ColaIcons.ChatFilled),
            ColaNavItem("Профиль", ColaIcons.Person, ColaIcons.PersonFilled),
        )

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun `with five sections only the selected one shows its label, and every one keeps a name`() {
        compose.setContent {
            ColaBikeTheme { ColaNavigationBar(five, selectedIndex = 1, onSelect = {}) }
        }

        compose.onNodeWithText("Велосипеды").assertIsDisplayed()
        compose.onNodeWithText("Сообщения").assertDoesNotExist()
        compose.onNodeWithContentDescription("Сообщения").assertIsDisplayed()
    }

    @Test
    fun `with three sections or fewer every label is shown`() {
        compose.setContent {
            ColaBikeTheme { ColaNavigationBar(five.take(3), selectedIndex = 0, onSelect = {}) }
        }

        five.take(3).forEach { compose.onNodeWithText(it.label).assertIsDisplayed() }
    }

    @Test
    fun `each section is a tab that says whether it is selected`() {
        compose.setContent {
            ColaBikeTheme { ColaNavigationBar(five, selectedIndex = 1, onSelect = {}) }
        }

        compose.onNodeWithText("Велосипеды").assert(isTab).assertIsSelected()
        compose.onNodeWithContentDescription("Профиль").assert(isTab).assertIsNotSelected()
    }

    @Test
    fun `every section is a touch target of at least 48 dp`() {
        compose.setContent {
            ColaBikeTheme { ColaNavigationBar(five, selectedIndex = 1, onSelect = {}) }
        }

        listOf(
                compose.onNodeWithText("Велосипеды"),
                compose.onNodeWithContentDescription("Лента"),
                compose.onNodeWithContentDescription("Покатушки"),
                compose.onNodeWithContentDescription("Сообщения"),
                compose.onNodeWithContentDescription("Профиль"),
            )
            .forEach { it.assertLaidOutAtLeast(48.dp) }
    }

    @Test
    fun `a tap reports the index, the selected section included`() {
        val taps = mutableListOf<Int>()
        var selected by mutableIntStateOf(1)
        compose.setContent {
            ColaBikeTheme {
                ColaNavigationBar(
                    five,
                    selectedIndex = selected,
                    onSelect = {
                        taps += it
                        selected = it
                    },
                )
            }
        }

        compose.onNodeWithText("Велосипеды").performClick()
        compose.onNodeWithContentDescription("Профиль").performClick()

        assertThat(taps).containsExactly(1, 4).inOrder()
        compose.onNodeWithText("Профиль").assertIsSelected()
    }

    @Test
    fun `the title of a screen is its heading, and back is named`() {
        compose.setContent { ColaBikeTheme { ColaTopBar(title = "Велосипеды", onBack = {}) } }

        compose
            .onNodeWithText("Велосипеды")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription("Назад").assertTouchTargetAtLeast(48.dp)
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
