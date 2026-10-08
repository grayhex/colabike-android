package ru.colabike.app.rides.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** Native sources preserve each public segment. No line is drawn across an omitted section. */
internal fun snapshotStyle(
    geometry: SnapshotGeometry,
    uri: String?,
    look: SnapshotLook,
): Style.Builder {
    val builder =
        if (uri != null) Style.Builder().fromUri(uri)
        else {
            // MapLibre 13.6.1 loads inline JSON synchronously in the JNI constructor. Its
            // builder callback would add sources before the snapshotter's native peer exists.
            // A local asset loads asynchronously, with no network or route data written to disk.
            Style.Builder()
                .fromUri("asset://map-preview.json")
                .withLayer(
                    BackgroundLayer("preview-background")
                        .withProperties(PropertyFactory.backgroundColor(look.background))
                )
        }
    return builder
        .withSource(GeoJsonSource("preview-route", geometry.lineJson))
        .withSource(GeoJsonSource("preview-start", geometry.startJson))
        .withSource(GeoJsonSource("preview-end", geometry.endJson))
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
