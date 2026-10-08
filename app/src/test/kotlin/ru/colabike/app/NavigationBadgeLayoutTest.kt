package ru.colabike.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.notifications.BellBadge
import ru.colabike.app.notifications.LocalNotificationsEntry
import ru.colabike.app.notifications.NotificationsBell
import ru.colabike.app.notifications.NotificationsEntry
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaNavItem
import ru.colabike.core.designsystem.component.ColaNavigationBar
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.designsystem.theme.ColaCanvas

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationBadgeLayoutTest(private val scale: Float, private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    private fun check(width: Int) {
        val badges =
            listOf(
                BellBadge.None,
                BellBadge.Exact(1),
                BellBadge.Exact(9),
                BellBadge.Exact(10),
                BellBadge.Exact(99),
                BellBadge.Many,
            )
        val items =
            listOf(
                ColaNavItem("Лента", ColaIcons.Feed, ColaIcons.FeedFilled),
                ColaNavItem("Велосипеды", ColaIcons.Bike, ColaIcons.BikeFilled, "Вело"),
                ColaNavItem("Покатушки", ColaIcons.Route, ColaIcons.Route, "Поездки"),
                ColaNavItem("Рынок", ColaIcons.Tag, ColaIcons.Tag),
                ColaNavItem("Профиль", ColaIcons.Person, ColaIcons.PersonFilled),
            )
        val taps = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, scale),
                LocalRippleConfiguration provides null,
            ) {
                ColaBikeTheme(darkTheme = dark) {
                    ColaCanvas {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            FlowRow {
                                badges.forEach { badge ->
                                    CompositionLocalProvider(
                                        LocalNotificationsEntry provides
                                            NotificationsEntry(badge) {}
                                    ) {
                                        NotificationsBell()
                                    }
                                }
                            }
                            ColaNavigationBar(items, 1, { taps += it })
                        }
                    }
                }
            }
        }
        compose.settle()
        val buttons = compose.onAllNodesWithTag("notifications:button").fetchSemanticsNodes()
        val counts =
            compose
                .onAllNodesWithTag("notifications:count", useUnmergedTree = true)
                .fetchSemanticsNodes()
        assertThat(buttons).hasSize(6)
        buttons.forEach { button ->
            val minimum = with(button.layoutInfo.density) { 48.dp.toPx() }
            assertThat(button.boundsInRoot.width).isAtLeast(minimum)
            assertThat(button.boundsInRoot.height).isAtLeast(minimum)
        }
        assertThat(counts).hasSize(5)
        counts.forEachIndexed { index, badge ->
            val bounds = buttons[index + 1].boundsInRoot
            assertThat(bounds.contains(badge.boundsInRoot.topLeft)).isTrue()
            assertThat(bounds.contains(badge.boundsInRoot.bottomRight)).isTrue()
        }
        items.forEachIndexed { index, item ->
            compose
                .onNodeWithContentDescription(item.label)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .performClick()
            val layouts = mutableListOf<TextLayoutResult>()
            compose
                .onNodeWithText(item.compactLabel, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertThat(layouts).hasSize(1)
            assertWithMessage(
                    "${item.label}: size=${layouts.single().size}, paragraph=${layouts.single().multiParagraph.width}x${layouts.single().multiParagraph.height}, input=${layouts.single().layoutInput.constraints}"
                )
                .that(layouts.single().hasVisualOverflow)
                .isFalse()
            assertThat(taps.last()).isEqualTo(index)
        }
        compose.captureWhenDrawn(
            "src/test/screenshots/navigation_badges_${width}_${scale}_${if (dark) "dark" else "light"}.png"
        )
    }

    @Test @Config(qualifiers = "ru-w360dp-h360dp-xhdpi") fun narrow() = check(360)

    @Test @Config(qualifiers = "ru-w412dp-h360dp-xhdpi") fun phone() = check(412)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "font {0}, dark {1}")
        fun cases(): List<Array<Any>> =
            listOf(1f, 1.3f, 2f).flatMap { scale ->
                listOf(false, true).map { arrayOf(scale, it) }
            }
    }
}
