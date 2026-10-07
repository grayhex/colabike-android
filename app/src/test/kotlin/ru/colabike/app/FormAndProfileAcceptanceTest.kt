package ru.colabike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.RideRoute

@OptIn(ExperimentalCoilApi::class)
private val formPhotos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * The form "I want to ride" and the profile on the screen of the acceptance (issue #42): 412 × 915
 * dp at the system font of 1.0, the bars with their real height. The form's main action is on the
 * bottom edge from the first moment; the screens are saved as the screenshots of the acceptance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
class FormAndProfileAcceptanceTest(private val look: Look) {
    @get:Rule val compose = createComposeRule()

    private fun show(dependencies: FakeDependencies = FakeDependencies()) {
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalRippleConfiguration provides null,
                LocalAsyncImagePreviewHandler provides formPhotos,
                LocalDensity provides Density(LocalDensity.current.density, 1f),
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = StatusBar, bottom = GestureStrip)
                    ) {
                        AppShell(dependencies)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the form has its main action on the bottom edge`() {
        show()
        compose.section("Покатушки").performClick()
        compose.onNodeWithTag("rides:intents").performClick()
        compose.onNodeWithTag("intents:create").performClick()
        compose.waitForIdle()
        compose.settle()

        compose.onNodeWithTag("intent-editor:save").assertIsDisplayed()
        compose.captureWhenDrawn("src/test/screenshots/intent_form_${look.file}.png")
    }

    @Test
    fun `the profile is grouped`() {
        show()
        compose.section("Профиль").performClick()
        compose.waitForIdle()
        compose.settle()

        compose.captureWhenDrawn("src/test/screenshots/profile_page_${look.file}.png")
    }

    /** The build has the owner's key for the Yandex map: the profile offers the two maps. */
    @Test
    fun `the profile offers the two maps when the build has the Yandex key`() {
        show(FakeDependencies(maps = MapsWithYandex))
        compose.section("Профиль").performClick()
        compose.waitForIdle()
        compose.settle()
        compose.onNodeWithText("Яндекс Карты").performScrollTo().assertIsDisplayed()
        compose.settle()

        compose.captureWhenDrawn("src/test/screenshots/profile_map_setting_${look.file}.png")
    }

    private object MapsWithYandex : RouteMaps {
        override val hasBasemap: Boolean = true
        override val offersYandex: Boolean = true

        @Composable override fun Map(route: RideRoute, modifier: Modifier) = Unit
    }

    private companion object {
        val StatusBar = 32.dp
        val GestureStrip = 24.dp

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = listOf(arrayOf(Look.Light), arrayOf(Look.Dark))
    }
}
