package ru.colabike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.Page

@OptIn(ExperimentalCoilApi::class)
private val pagePhotos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * The acceptance of the bike page (issue #42): on a full portrait screen of 412 × 915 dp at the
 * system font of 1.0 the first screen holds, under the photo and the name, the owner, the like and
 * the share, the start of the description, and the facts themselves (not only their heading).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
class BikePageAcceptanceTest(private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the owner, the actions, the description and the facts are on the first screen`() {
        val bike =
            PreviewData.bike.copy(id = BikeId("b0"), name = "Canyon Grail CF SLX 8 AXS (2026)")
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides pagePhotos,
                LocalDensity provides Density(LocalDensity.current.density, 1f),
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = StatusBar, bottom = GestureStrip)
                    ) {
                        AppShell(
                            FakeDependencies(
                                bikes = FakeBikes(mapOf(null to Page(listOf(bike), null)))
                            )
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Canyon Grail", substring = true).performClick()
        compose.waitForIdle()
        compose.settle()

        val bottom = 915.dp - GestureStrip
        fun bottomOf(text: String) =
            compose.onNodeWithText(text, substring = true).getUnclippedBoundsInRoot().bottom
        assertThat(bottomOf("Поделиться").value).isAtMost(bottom.value)
        assertThat(bottomOf("Надёжный горный велосипед").value).isAtMost(bottom.value)
        // The value of a fact, not just the heading of the section.
        assertThat(bottomOf("14,2 кг").value).isAtMost(bottom.value)
        compose.captureWhenDrawn("src/test/screenshots/bike_page_${look.file}.png")
    }

    private companion object {
        val StatusBar = 32.dp
        val GestureStrip = 24.dp

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = listOf(arrayOf(Look.Light), arrayOf(Look.Dark))
    }
}
