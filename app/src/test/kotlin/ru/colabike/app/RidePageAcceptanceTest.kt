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
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.RideMetrics

@OptIn(ExperimentalCoilApi::class)
private val ridePhotos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * The acceptance of the ride page (issue #42): on a full portrait screen of 412 × 915 dp at the
 * system font of 1.0, with the bar of the sections under it, the first screen holds the title, the
 * status and the date, the route, all five figures (the distance, the time on the move, all the
 * time, the average speed, the climb) and the note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
class RidePageAcceptanceTest(private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the route, the five figures and the note are on the first screen`() {
        val base = rideDetail(0)
        val ride =
            base.copy(
                summary =
                    base.summary.copy(
                        title = "С Сашей по набережной и обратно",
                        time = Instant.parse("2026-09-16T15:28:00Z"),
                        metrics =
                            RideMetrics(
                                distanceM = 26_500,
                                movingTimeS = 5_580,
                                elapsedTimeS = 7_080,
                                avgSpeedMps = 16.9 / 3.6,
                                elevationGainM = 33.0,
                            ),
                    ),
                description = "Тогда у нас был отвал трансмиссии и тормоза",
                features = emptyList(),
                extraMetrics = emptyMap(),
            )
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalRippleConfiguration provides null,
                LocalAsyncImagePreviewHandler provides ridePhotos,
                LocalDensity provides Density(LocalDensity.current.density, 1f),
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = StatusBar, bottom = GestureStrip)
                    ) {
                        AppShell(
                            FakeDependencies(rides = FakeRides(details = mapOf("ride-0" to ride)))
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.section("Покатушки").performClick()
        compose.onNodeWithText("Состоявшиеся").performClick()
        compose.onNodeWithContentDescription("Покатушка 0", substring = true).performClick()
        compose.waitForIdle()
        compose.settle()

        val bar = compose.section("Покатушки").getUnclippedBoundsInRoot().top
        fun bottomOf(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().bottom
        listOf(
                "26,5 км",
                "1 ч 33 мин",
                "1 ч 58 мин",
                "16,9 км/ч",
                "33 м",
                "Тогда у нас был отвал трансмиссии и тормоза",
            )
            .forEach { assertThat(bottomOf(it).value).isAtMost(bar.value) }
        compose.captureWhenDrawn("src/test/screenshots/ride_page_${look.file}.png")
    }

    private companion object {
        val StatusBar = 32.dp
        val GestureStrip = 24.dp

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = listOf(arrayOf(Look.Light), arrayOf(Look.Dark))
    }
}
