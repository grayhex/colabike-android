package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.app.rides.map.prepareSnapshot
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

@RunWith(RobolectricTestRunner::class)
class SnapshotGeometryTest {
    @Test
    fun `large geometry is traversed off the caller thread and retains every segment`() =
        runBlocking {
            val caller = Thread.currentThread()
            val line =
                object : AbstractList<GeoPoint>() {
                    override val size = 10000

                    override fun get(index: Int): GeoPoint {
                        check(Thread.currentThread() !== caller) {
                            "route preprocessing blocked the caller"
                        }
                        return GeoPoint(55.0 + index * 0.00001, 37.0 + index * 0.00001)
                    }
                }
            val prepared = prepareSnapshot(RideRoute(listOf(line, line)))
            val segments =
                Json.parseToJsonElement(prepared.lineJson)
                    .jsonObject
                    .getValue("coordinates")
                    .jsonArray
            assertThat(segments).hasSize(2)
            assertThat(segments.map { it.jsonArray.size }).containsExactly(10000, 10000)
            assertThat(prepared.bounds.latitudeSouth).isEqualTo(55.0)
            assertThat(prepared.bounds.latitudeNorth).isWithin(0.000001).of(55.09999)
        }

    @Test
    fun `singleton endpoints survive without an invented connecting line`() = runBlocking {
        val prepared = prepareSnapshot(RideRoute(listOf(listOf(GeoPoint(55.75, 37.61)))))
        assertThat(prepared.lineJson).isEqualTo("""{"type":"MultiLineString","coordinates":[]}""")
        assertThat(prepared.startJson).isEqualTo(prepared.endJson)
        assertThat(prepared.bounds.latitudeSpan).isEqualTo(0.0)
    }
}
