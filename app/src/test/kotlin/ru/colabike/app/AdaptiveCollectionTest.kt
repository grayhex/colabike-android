package ru.colabike.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Page

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w1200dp-h900dp-mdpi")
class AdaptiveCollectionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `selection and list position survive live compact medium and expanded changes`() {
        var width by mutableIntStateOf(1200)
        val repository = FakeBikes(mapOf(null to Page(bikes(0, 24), null)))
        val dependencies = FakeDependencies(bikes = repository)
        compose.setContent {
            val window =
                object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize = IntSize(width, 900)
                    override val containerDpSize = DpSize(width.dp, 900.dp)
                }
            CompositionLocalProvider(LocalWindowInfo provides window) {
                Box(Modifier.width(width.dp)) { ColaBikeTheme { AppShell(dependencies) } }
            }
        }
        compose.onNodeWithTag("bikes:grid").assertWidthIsAtLeast(950.dp)
        compose.onNodeWithTag("bikes:grid").performScrollToIndex(12)
        compose.onNodeWithTag("bike:b12").performClick()
        for (next in listOf(600, 840, 360, 1200)) {
            compose.runOnIdle { width = next }
            compose.waitForIdle()
            compose.onNodeWithTag("bike:author").assertExists()
            compose.onNodeWithContentDescription("Назад").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithTag("bikes:grid").assertWidthIsAtLeast(950.dp)
        compose.onNodeWithTag("bike:b12").assertIsDisplayed()
        assertThat(repository.calls).hasSize(1)
    }
}
