package ru.colabike.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.cos
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.colabike.app.rides.map.BasemapStyle
import ru.colabike.app.rides.map.MapLibreRoute
import ru.colabike.app.rides.map.MapProbe
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

/**
 * The real map on a real device: MapLibre loads its native library, reaches the style (a plain one,
 * no tile request) and puts the route on it, in both themes, and lets go of the view when the page
 * leaves. Robolectric cannot do any of this, so the screenshot tests draw the route instead.
 */
@RunWith(AndroidJUnit4::class)
class RouteMapRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // Two lines with a privacy cut between them, in Moscow.
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

    private fun waitForRoute(probe: MapProbe) =
        compose.waitUntil(40_000) { probe.routeShown || probe.failure != null }

    @Test
    fun theScaleMatchesGroundDistanceAtTheDeviceDensity() {
        val probe = MapProbe()
        compose.setContent {
            ColaBikeTheme { MapLibreRoute(route, null, Modifier.fillMaxSize(), probe) }
        }
        compose.waitUntil(40_000) { probe.routeShown && (probe.scale?.zoom ?: 0.0) > 8.0 }
        val reading = checkNotNull(probe.scale)
        assertEquals(
            "opening fits the route before any fit-button tap",
            55.76,
            reading.latitude,
            0.05,
        )
        // Independent Web Mercator ground resolution for 512 logical pixels per world tile.
        // Allow the small spherical/ellipsoidal difference of Android's geodesic calculation.
        val expected =
            40075016.686 * cos(Math.toRadians(reading.latitude)) / (512 * 2.0.pow(reading.zoom))
        assertEquals(
            "the scale must not multiply density a second time",
            expected,
            reading.metresPerDp,
            expected * 0.02,
        )
    }

    @Test fun theRouteIsOnTheMapInTheLightTheme() = render(dark = false)

    @Test fun theRouteIsOnTheMapInTheDarkTheme() = render(dark = true)

    private fun render(dark: Boolean) {
        val probe = MapProbe()
        compose.setContent {
            ColaBikeTheme(darkTheme = dark) {
                MapLibreRoute(route, null, Modifier.fillMaxSize(), probe)
            }
        }

        waitForRoute(probe)

        assertTrue("the route layer is on the style", probe.routeShown)
        assertNull("the map failed: ${probe.failure}", probe.failure)
    }

    /**
     * The built-in OpenStreetMap style asks the network for its tiles. With the network the route
     * is on the style, without it the route is on the plain background the map falls back to;
     * either way the route is there, and nothing here depends on the provider being up.
     */
    @Test
    fun theRouteIsThereWithTheBuiltInStyleWhetherOrNotTheTilesCome() {
        val probe = MapProbe()
        compose.setContent {
            ColaBikeTheme {
                MapLibreRoute(
                    route,
                    BasemapStyle.choose(override = null, dark = false),
                    Modifier.fillMaxSize(),
                    probe,
                )
            }
        }

        compose.waitUntil(60_000) { probe.routeShown }

        assertTrue("the route layer is on the style", probe.routeShown)
    }

    @Test
    fun theMapLetsGoWhenThePageLeaves() {
        val probe = MapProbe()
        var shown by mutableStateOf(true)
        compose.setContent {
            ColaBikeTheme {
                if (shown) MapLibreRoute(route, null, Modifier.fillMaxSize(), probe)
            }
        }
        waitForRoute(probe)

        shown = false
        compose.waitForIdle()

        // Leaving composition destroys the view with the activity still alive, without a crash.
        assertTrue(probe.routeShown)
    }
}
