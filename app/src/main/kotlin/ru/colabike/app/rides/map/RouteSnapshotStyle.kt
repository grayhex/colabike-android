package ru.colabike.app.rides.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

/** Native sources preserve each public segment. No line is drawn across an omitted section. */
internal fun snapshotStyle(route: RideRoute, uri: String?, look: SnapshotLook): Style.Builder {
    val geojson =
        route.lines
            .filter { it.size >= 2 }
            .joinToString(
                separator = ",",
                prefix = """{"type":"MultiLineString","coordinates":[""",
                postfix = "]}",
            ) { line ->
                line.joinToString(",", "[", "]") { "[${it.longitude},${it.latitude}]" }
            }
    fun point(at: GeoPoint) = """{"type":"Point","coordinates":[${at.longitude},${at.latitude}]}"""
    val builder =
        if (uri != null) Style.Builder().fromUri(uri)
        else Style.Builder().fromJson(blankStyle(look.background))
    return builder
        .withSource(GeoJsonSource("preview-route", geojson))
        .withSource(GeoJsonSource("preview-start", point(route.lines.first().first())))
        .withSource(GeoJsonSource("preview-end", point(route.lines.last().last())))
        .withLayer(
            LineLayer("preview-halo", "preview-route")
                .withProperties(
                    PropertyFactory.lineColor(look.halo),
                    PropertyFactory.lineWidth(8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                )
        )
        .withLayer(
            LineLayer("preview-line", "preview-route")
                .withProperties(
                    PropertyFactory.lineColor(look.line),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                )
        )
        .withLayer(
            CircleLayer("preview-start-dot", "preview-start")
                .withProperties(
                    PropertyFactory.circleColor(look.line),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(look.halo),
                    PropertyFactory.circleStrokeWidth(3f),
                )
        )
        .withLayer(
            CircleLayer("preview-end-dot", "preview-end")
                .withProperties(
                    PropertyFactory.circleColor(look.halo),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(look.line),
                    PropertyFactory.circleStrokeWidth(3f),
                )
        )
}
