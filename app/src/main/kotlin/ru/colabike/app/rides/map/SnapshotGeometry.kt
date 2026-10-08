package ru.colabike.app.rides.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

internal data class SnapshotGeometry(
    val bounds: LatLngBounds,
    val lineJson: String,
    val startJson: String,
    val endJson: String,
)

/** No native APIs: walk and serialize potentially 20,000 points away from scrolling/composition. */
internal suspend fun prepareSnapshot(route: RideRoute): SnapshotGeometry =
    withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        val bounds = LatLngBounds.Builder()
        var count = 0
        route.lines.forEach { line ->
            line.forEach { point ->
                if (++count % 256 == 0) context.ensureActive()
                bounds.include(LatLng(point.latitude, point.longitude))
            }
        }
        val start = route.lines.first().first()
        val end = route.lines.last().last()
        // The native bounds builder requires two entries even for a single measured point.
        bounds.include(LatLng(start.latitude, start.longitude))
        val json =
            route.lines
                .filter { it.size >= 2 }
                .joinToString(
                    separator = ",",
                    prefix = """{"type":"MultiLineString","coordinates":[""",
                    postfix = "]}",
                ) { line ->
                    line.joinToString(",", "[", "]") { point ->
                        if (++count % 256 == 0) context.ensureActive()
                        "[${point.longitude},${point.latitude}]"
                    }
                }
        fun pointJson(point: GeoPoint) =
            """{"type":"Point","coordinates":[${point.longitude},${point.latitude}]}"""
        SnapshotGeometry(bounds.build(), json, pointJson(start), pointJson(end))
    }
