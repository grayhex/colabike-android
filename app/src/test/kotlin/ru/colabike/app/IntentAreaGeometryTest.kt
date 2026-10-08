package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test
import ru.colabike.app.rides.map.areaOutline
import ru.colabike.core.model.RideAreaPoint

class IntentAreaGeometryTest {
    @Test
    fun `circles crossing either side of the antimeridian keep local bounds and short edges`() {
        for (longitude in listOf(-179.99, 179.99, -180.0, 180.0)) {
            val area = RideAreaPoint(longitude, 55.0, 100000)
            val route = areaOutline(area)
            val ring = route.lines.first()
            val points = route.lines.flatten()
            val west = points.minOf { it.longitude }
            val east = points.maxOf { it.longitude }
            assertThat(east - west).isLessThan(4.0)
            assertThat(abs((west + east) / 2 - longitude)).isLessThan(0.01)
            for ((a, b) in ring.zipWithNext()) {
                assertThat(abs(a.longitude - b.longitude)).isLessThan(0.3)
            }
            assertThat(abs(ring.first().longitude - ring.last().longitude)).isLessThan(1e-8)
            assertThat(abs(ring.first().latitude - ring.last().latitude)).isLessThan(1e-8)
            assertThat(ring.all { it.latitude.isFinite() && it.longitude.isFinite() }).isTrue()
        }
    }
}
