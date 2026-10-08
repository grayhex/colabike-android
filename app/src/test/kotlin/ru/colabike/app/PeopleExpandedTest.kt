package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** The people screens in a window wide enough for list and detail side by side. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w1280dp-h800dp-mdpi")
class PeopleExpandedTest {
    @get:Rule val compose = createComposeRule()

    private fun openPersonFromBike() {
        compose.setContent { ColaBikeTheme { ColaBikeApp(FakeDependencies()) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.onNodeWithText("Тестовый Райдер").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a person's page takes the whole area and Back brings the pair back`() {
        openPersonFromBike()

        compose.onNodeWithText("Катаюсь круглый год.").assertIsDisplayed()
        compose.onNodeWithTag("bikes:grid").assertDoesNotExist()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bikes:grid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Нравится", substring = true).assertExists()
    }

    @Test
    fun `a bike opened from a person's page fills the area and Back returns to the person`() {
        openPersonFromBike()

        compose
            .onNodeWithTag("person:grid")
            .performScrollToNode(hasContentDescription("Велосипед 0", substring = true))
        compose.onNodeWithContentDescription("Велосипед 0", substring = true).performClick()
        compose.waitForIdle()

        // The person is between the list and this bike, so there is no pair to show.
        compose.onNodeWithContentDescription("Нравится", substring = true).assertExists()
        compose.onNodeWithTag("bikes:grid").assertDoesNotExist()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Катаюсь круглый год.").assertIsDisplayed()
    }

    @Test
    fun `a bike found by search fills the area and has its Back arrow`() {
        compose.setContent { ColaBikeTheme { ColaBikeApp(FakeDependencies()) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Поиск").performClick()
        compose.onNode(androidx.compose.ui.test.hasText("MTB") and hasClickAction()).performClick()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Поиск").assertIsDisplayed()
    }

    @Test
    fun `Back beside the list restores the full catalog grid`() {
        compose.setContent { ColaBikeTheme { ColaBikeApp(FakeDependencies()) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Нравится", substring = true).assertExists()
        compose.onNodeWithContentDescription("Назад").assertIsDisplayed().performClick()
        compose.onNodeWithTag("bikes:grid").assertWidthIsAtLeast(950.dp)
    }
}
