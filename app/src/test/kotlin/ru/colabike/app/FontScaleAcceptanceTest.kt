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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import java.time.Instant
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
import ru.colabike.core.model.RideMetrics

@OptIn(ExperimentalCoilApi::class)
private val scalePhotos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/** The five screens of the acceptance. */
enum class KeyScreen(val file: String) {
    Catalog("catalog"),
    BikePage("bike_page"),
    RidePage("ride_page"),
    IntentForm("intent_form"),
    Profile("profile"),
}

/**
 * The evidence of issue #42 for a bigger system font: the five screens of the acceptance on a small
 * phone (360 × 800 dp) at the font of 1.3 and of 2.0. The text grows, wraps or scrolls, nothing
 * lies over anything else, and every action can still be reached (the pictures are in
 * `src/test/screenshots/acceptance_<screen>_360_font<percent>.png`). Two cards in the catalog at
 * one time are required of the font of 1.0 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class FontScaleAcceptanceTest(private val screen: KeyScreen, private val scale: Float) {
    @get:Rule val compose = createComposeRule()

    private fun show(dependencies: FakeDependencies) {
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalRippleConfiguration provides null,
                LocalAsyncImagePreviewHandler provides scalePhotos,
                LocalDensity provides Density(LocalDensity.current.density, scale),
            ) {
                ColaBikeTheme(darkTheme = true) {
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

    private val bike =
        PreviewData.bike.copy(id = BikeId("b0"), name = "Canyon Grail CF SLX 8 AXS (2026)")

    @Test
    fun capture() {
        val bikes = FakeBikes(mapOf(null to Page(listOf(bike), null)))
        when (screen) {
            KeyScreen.Catalog -> show(FakeDependencies(bikes = bikes))
            KeyScreen.BikePage -> {
                show(FakeDependencies(bikes = bikes))
                compose
                    .onNodeWithContentDescription("Canyon Grail", substring = true)
                    .performClick()
            }
            KeyScreen.RidePage -> {
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
                show(FakeDependencies(rides = FakeRides(details = mapOf("ride-0" to ride))))
                compose.section("Покатушки").performClick()
                compose.onNodeWithText("Состоявшиеся").performClick()
                compose.onNodeWithContentDescription("Покатушка 0", substring = true).performClick()
            }
            KeyScreen.IntentForm -> {
                show(FakeDependencies())
                compose.section("Покатушки").performClick()
                compose.onNodeWithTag("rides:intents").performClick()
                compose.onNodeWithTag("intents:create").performClick()
            }
            KeyScreen.Profile -> {
                show(FakeDependencies())
                compose.section("Профиль").performClick()
            }
        }
        compose.waitForIdle()
        compose.settle()
        compose.captureWhenDrawn(
            "src/test/screenshots/acceptance_${screen.file}_360_font${(scale * 100).toInt()}.png"
        )
    }

    private companion object {
        val StatusBar = 32.dp
        val GestureStrip = 24.dp

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> =
            KeyScreen.entries.flatMap { screen -> listOf(1.3f, 2f).map { arrayOf(screen, it) } }
    }
}
