package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** A ride in a window wide enough for the map to stand beside the page. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w1280dp-h800dp-mdpi")
class RidesExpandedTest {
    @get:Rule val compose = createComposeRule()

    private fun openCompleted() {
        compose.setContent { ColaBikeTheme { ColaBikeApp(FakeDependencies()) } }
        compose.waitForIdle()
        compose.section("Покатушки").performClick()
        compose.onNode(hasText("Состоявшиеся") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Покатушка 0", substring = true).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the map stands beside the page and no button opens it`() {
        openCompleted()

        compose.onNodeWithText("Покатушка 0").assertIsDisplayed()
        compose.onNodeWithContentDescription("Схема маршрута", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Открыть карту").assertDoesNotExist()
        compose.onNodeWithText("Подложка карты не подключена", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the charts are in the page beside the map`() {
        openCompleted()

        compose.onNodeWithText("Разбор маршрута").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Высота: от", substring = true).assertExists()
    }
}
