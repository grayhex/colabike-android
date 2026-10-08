package ru.colabike.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.colabike.app.rides.map.MapSnapshots
import ru.colabike.app.rides.map.SnapshotKey
import ru.colabike.app.rides.map.SnapshotLook
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

/** Deterministic native rendering: no remote tiles or production data. */
@RunWith(AndroidJUnit4::class)
class RouteSnapshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nativeSnapshotRendersSegmentsAndUsesItsMemoryCache() {
        val route =
            RideRoute(
                listOf(
                    listOf(GeoPoint(55.75, 37.61), GeoPoint(55.76, 37.62)),
                    listOf(GeoPoint(55.765, 37.63), GeoPoint(55.77, 37.64)),
                )
            )
        render(route)
    }

    @Test
    fun aSinglePointStillProducesAUsefulSnapshot() {
        render(RideRoute(listOf(listOf(GeoPoint(55.75, 37.61)))))
    }

    @Test
    fun leavingTheSessionCancelsAnActiveNativeSnapshot() {
        val owner = MapSnapshots()
        lateinit var job: Job
        compose.runOnUiThread {
            job =
                compose.activity.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    owner.image(
                        compose.activity,
                        SnapshotKey(
                            RideRoute(listOf(listOf(GeoPoint(55.75, 37.61)))),
                            null,
                            SnapshotLook(
                                0xffa9dc40.toInt(),
                                0xff202020.toInt(),
                                0xffeeeeee.toInt(),
                            ),
                            600,
                            300,
                            1f,
                        ),
                    )
                }
            owner.clear()
        }
        compose.waitUntil(5_000) { job.isCompleted }
        assertTrue("leaving cancels the renderer, not just its cached bitmap", job.isCancelled)
        // A new owner can render after the old native operation has been cancelled.
        render(RideRoute(listOf(listOf(GeoPoint(55.75, 37.61)))))
    }

    @Test
    fun highDensityStillRespectsTheBitmapPixelBudget() {
        render(RideRoute(listOf(listOf(GeoPoint(55.75, 37.61)))), density = 2f)
    }

    private fun render(route: RideRoute, density: Float = 1f) {
        val owner = MapSnapshots()
        var first: Bitmap? = null
        var second: Bitmap? = null
        var finished = false
        compose.runOnUiThread {
            compose.activity.lifecycleScope.launch {
                val key =
                    SnapshotKey(
                        route,
                        null,
                        SnapshotLook(0xffa9dc40.toInt(), 0xff202020.toInt(), 0xffeeeeee.toInt()),
                        600,
                        300,
                        density,
                    )
                first = owner.image(compose.activity, key)
                second = owner.image(compose.activity, key)
                finished = true
            }
        }
        compose.waitUntil(30_000) { finished }
        assertNotNull("native snapshot must finish without tiles", first)
        assertSame("repeated visible route reuses the bitmap", first, second)
        val bitmap = checkNotNull(first)
        assertTrue("width stays within the pixel budget", bitmap.width <= 600)
        assertTrue("height stays within the pixel budget", bitmap.height <= 300)
        var routePixels = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (
                android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) + 20 &&
                    android.graphics.Color.green(pixel) > android.graphics.Color.blue(pixel) + 50
            )
                routePixels++
        }
        assertTrue("the route/endpoint is actually drawn", routePixels > 10)
        compose.runOnUiThread { owner.clear() }
    }
}
