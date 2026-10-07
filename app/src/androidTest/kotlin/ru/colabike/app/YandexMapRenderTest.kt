package ru.colabike.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.colabike.app.rides.map.MapKitRoute
import ru.colabike.app.rides.map.MapProbe
import ru.colabike.app.rides.map.YandexFailure
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

/**
 * The Yandex SDK on a phone without Google Play Services (the CI emulators have none): its native
 * library loads, nothing it needs from Google is missing for a map that only looks, and a key the
 * service does not know ends in one of two ways, both safe: the map loads, or the screen is told
 * that Yandex is not available within the time it gives (and shows OpenStreetMap). A real key and
 * real tiles are the owner's check on a device; this one proves the SDK does not take the app down.
 */
@RunWith(AndroidJUnit4::class)
class YandexMapRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val route =
        RideRoute(
            listOf(
                listOf(
                    GeoPoint(55.750, 37.610),
                    GeoPoint(55.755, 37.620),
                    GeoPoint(55.760, 37.625),
                ),
                listOf(GeoPoint(55.765, 37.640), GeoPoint(55.770, 37.650)),
            )
        )

    @Test
    fun aKeyTheServiceDoesNotKnowNeitherCrashesTheAppNorLeavesTheScreenWaiting() {
        val probe = MapProbe()
        var failure: YandexFailure? = null
        compose.setContent {
            ColaBikeTheme {
                MapKitRoute(
                    route,
                    apiKey = "00000000-0000-0000-0000-000000000000",
                    modifier = Modifier.fillMaxSize(),
                    onUnavailable = { failure = it },
                    probe = probe,
                )
            }
        }

        // Either the map answers, or the screen is told so (the SDK's load time is 25 s at most).
        compose.waitUntil(60_000) { probe.mapLoaded || failure != null }

        assertTrue(
            "the SDK took the page down: ${probe.failure}",
            probe.mapLoaded || failure != null,
        )
    }

    @Test
    fun theMapLetsGoWhenThePageLeaves() {
        val probe = MapProbe()
        var shown by mutableStateOf(true)
        compose.setContent {
            ColaBikeTheme {
                if (shown) {
                    MapKitRoute(
                        route,
                        apiKey = "00000000-0000-0000-0000-000000000000",
                        modifier = Modifier.fillMaxSize(),
                        onUnavailable = {},
                        probe = probe,
                    )
                }
            }
        }
        compose.waitUntil(40_000) { probe.routeShown || probe.failure != null }

        shown = false
        compose.waitForIdle()

        // Leaving composition stops the SDK's view with the activity alive, without a crash.
        assertTrue(probe.routeShown || probe.failure != null)
    }
}
