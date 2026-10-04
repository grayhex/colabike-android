package ru.colabike.app.rides.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import ru.colabike.app.R
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

/**
 * Where a ride's public route is drawn on a map. A screen asks for [Map] and does not know what is
 * behind it: the real map (MapLibre, [MapLibreRouteMaps]) in the app, a drawing of the route
 * ([SketchRouteMaps]) in tests, where a native renderer cannot run.
 */
interface RouteMaps {
    /**
     * Whether a map lies under the route. Without a tile source there is only the route on a plain
     * background, and the screen says so instead of passing the plain background for a map.
     */
    val hasBasemap: Boolean

    /** The route, panned and zoomed by the hand. Fills [modifier]. */
    @Composable fun Map(route: RideRoute, modifier: Modifier)
}

/** The route drawn by Compose alone: no tiles, no gestures, no network. */
object SketchRouteMaps : RouteMaps {
    override val hasBasemap: Boolean = false

    @Composable
    override fun Map(route: RideRoute, modifier: Modifier) = RouteSketch(route, modifier)
}

/**
 * A small drawing of the route that fits its box. It is what the page shows before the map is
 * opened: it costs nothing, asks nothing of the network, and keeps the cuts the server made as
 * cuts. North is up and the east-west scale is the true one at the route's latitude.
 */
@Composable
fun RouteSketch(route: RideRoute, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val halo = MaterialTheme.colorScheme.surfaceContainer
    val frame = remember(route) { RouteFrame(route) }
    val description = stringResource(R.string.route_sketch_description, route.lines.size)
    Canvas(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clearAndSetSemantics { contentDescription = description }
            .fillMaxSize()
    ) {
        val padding = 20.dp.toPx()
        val width = 4.dp.toPx()
        route.lines.forEach { points ->
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { index, point ->
                val at = frame.project(point, size.width, size.height, padding)
                if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
            }
            drawPath(
                path,
                halo,
                style = Stroke(width + 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path,
                line,
                style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        val start = route.lines.first().first()
        val end = route.lines.last().last()
        frame.project(start, size.width, size.height, padding).let {
            drawCircle(halo, 8.dp.toPx(), it)
            drawCircle(line, 5.dp.toPx(), it)
        }
        frame.project(end, size.width, size.height, padding).let {
            drawCircle(line, 8.dp.toPx(), it)
            drawCircle(halo, 4.dp.toPx(), it)
        }
    }
}

/** The box of a route and how a place is put in a canvas: a plain projection, true to scale. */
internal class RouteFrame(route: RideRoute) {
    private val points = route.lines.flatten()
    private val minLat = points.minOf { it.latitude }
    private val maxLat = points.maxOf { it.latitude }
    private val minLon = points.minOf { it.longitude }
    private val maxLon = points.maxOf { it.longitude }
    private val eastWest = cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.01)

    fun project(point: GeoPoint, width: Float, height: Float, padding: Float): Offset {
        val spanX = ((maxLon - minLon) * eastWest).coerceAtLeast(MIN_SPAN)
        val spanY = (maxLat - minLat).coerceAtLeast(MIN_SPAN)
        val roomX = max(width - 2 * padding, 1f)
        val roomY = max(height - 2 * padding, 1f)
        val scale = min(roomX / spanX, roomY / spanY).toFloat()
        val offsetX = (width - (spanX * scale).toFloat()) / 2
        val offsetY = (height - (spanY * scale).toFloat()) / 2
        return Offset(
            offsetX + ((point.longitude - minLon) * eastWest * scale).toFloat(),
            offsetY + ((maxLat - point.latitude) * scale).toFloat(),
        )
    }

    private companion object {
        /** A route that is one point's worth still gets a box, not a division by zero. */
        const val MIN_SPAN = 1e-6
    }
}
