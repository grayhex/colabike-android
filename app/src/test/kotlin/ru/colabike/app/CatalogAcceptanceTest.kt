package ru.colabike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
private val previewPhotos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * The acceptance of the catalog (issue #42): on a full portrait screen of 412 × 915 dp at the
 * system font of 1.0, with a status bar and a gesture bar that take their real height, the first
 * two cards are whole above the bar, with at least 8 dp between them and above the bar, however the
 * name is written: here two lines long. The same screen is saved as the screenshot of the
 * acceptance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
class CatalogAcceptanceTest(private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun `two whole cards of two-line names are on the first screen, eight dp apart`() {
        val bikes =
            (0 until 4).map {
                PreviewData.bike.copy(
                    id = BikeId("b$it"),
                    name = "Cannondale Trail SE 2: перебранный трейловый хардтейл $it",
                    liked = it % 2 == 0,
                )
            }
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalRippleConfiguration provides null,
                LocalAsyncImagePreviewHandler provides previewPhotos,
                LocalDensity provides Density(LocalDensity.current.density, 1f),
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    // The system bars take their height from the window: here, as on a phone with
                    // gesture navigation, a status bar of 32 dp and a gesture strip of 24 dp.
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = StatusBar, bottom = GestureStrip)
                    ) {
                        AppShell(
                            FakeDependencies(bikes = FakeBikes(mapOf(null to Page(bikes, null))))
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.settle()

        val first = compose.onNodeWithTag("bike:b0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("bike:b1").getUnclippedBoundsInRoot()
        val bar = compose.section("Велосипеды").getUnclippedBoundsInRoot()

        assertThat((second.top - first.bottom).value).isAtLeast(MinGap.value)
        assertThat((bar.top - second.bottom).value).isAtLeast(MinGap.value)
        compose.captureWhenDrawn("src/test/screenshots/bikes_two_cards_${look.file}.png")
    }

    private companion object {
        val StatusBar = 32.dp
        val GestureStrip = 24.dp
        val MinGap = 8.dp

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = listOf(arrayOf(Look.Light), arrayOf(Look.Dark))
    }
}
