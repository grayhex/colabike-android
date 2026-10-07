package ru.colabike.app.rides.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import ru.colabike.app.R
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

/**
 * The real map: MapLibre Native without Google Play Services, drawn on a texture so it follows the
 * page's transitions. It asks for no location and starts no service; its tile requests are its own
 * and carry nothing of the ColaBike session.
 *
 * The basemap is OpenStreetMap by OpenFreeMap, built in, so that a build without any property shows
 * a real map (ADR 0024). [styleOverride] is the style the owner put in `colabike.mapStyleUrl`
 * (`https` only); a missing or wrong one is the built-in style.
 */
class MapLibreRouteMaps(private val styleOverride: String?) : RouteMaps {
    override val hasBasemap: Boolean = true

    @Composable
    override fun Map(route: RideRoute, modifier: Modifier) {
        val dark = MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE
        MapLibreRoute(route, BasemapStyle.choose(styleOverride, dark), modifier)
    }
}

/** What an instrumented test can look at: the style loaded and the route is on it. */
class MapProbe {
    @Volatile var routeShown: Boolean = false
    @Volatile var mapLoaded: Boolean = false
    @Volatile var failure: String? = null
}

private data class MapLook(val background: Int, val halo: Int, val line: Int)

@Composable
internal fun MapLibreRoute(
    route: RideRoute,
    style: BasemapStyle?,
    modifier: Modifier = Modifier,
    probe: MapProbe? = null,
) {
    val styleUrl = style?.url
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scheme = MaterialTheme.colorScheme
    val look =
        MapLook(
            background = scheme.surfaceContainerLow.toArgb(),
            halo = scheme.surfaceContainer.toArgb(),
            line = scheme.primary.toArgb(),
        )
    val density = LocalDensity.current.density
    val description = stringResource(R.string.route_map_description)
    // Where the hand left the camera, kept over a turn of the screen; null until it is moved.
    var camera by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var remoteFailed by remember(styleUrl) { mutableStateOf(false) }
    val currentStyleUrl by rememberUpdatedState(styleUrl)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
    }

    DisposableEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { message ->
            // A style that cannot be had must not take the route with it: draw the route alone.
            if (currentStyleUrl != null) remoteFailed = true
            probe?.failure = message
        }
        mapView.getMapAsync { ready ->
            ready.uiSettings.apply {
                // North stays up and the picture flat: a route is read, not flown over.
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                // The library's logo is not asked for by its licence; the sources of the map are
                // named by the caption of the built-in style or by the button of an override.
                isLogoEnabled = false
                val margin = (ATTRIBUTION_MARGIN_DP * density).toInt()
                setAttributionMargins(margin, 0, 0, margin)
            }
            ready.setMinZoomPreference(MIN_ZOOM)
            ready.setMaxZoomPreference(MAX_ZOOM)
            ready.addOnCameraIdleListener {
                val position = ready.cameraPosition
                val target = position.target
                if (target != null) {
                    camera = doubleArrayOf(target.latitude, target.longitude, position.zoom)
                }
            }
            map = ready
        }
        onDispose {}
    }

    DisposableEffect(lifecycle, mapView) {
        var created = false
        var started = false
        var resumed = false
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    mapView.onCreate(null)
                    created = true
                }
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE ->
                    if (resumed) {
                        mapView.onPause()
                        resumed = false
                    }
                Lifecycle.Event.ON_STOP ->
                    if (started) {
                        mapView.onStop()
                        started = false
                    }
                Lifecycle.Event.ON_DESTROY ->
                    if (created && !destroyed) {
                        mapView.onDestroy()
                        destroyed = true
                    }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // The page leaves while the activity lives: the map must not go on in the background.
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            if (created && !destroyed) mapView.onDestroy()
        }
    }

    DisposableEffect(mapView, context) {
        val callbacks =
            object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                @Deprecated("Replaced by onTrimMemory in the platform; kept for older callers")
                override fun onLowMemory() = mapView.onLowMemory()

                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                        mapView.onLowMemory()
                    }
                }
            }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }

    val ready = map
    LaunchedEffect(ready, route, style, look, remoteFailed) {
        if (ready == null) return@LaunchedEffect
        val remote = styleUrl?.takeIf { !remoteFailed }
        // An override names its sources through the library's button; the built-in style has the
        // caption below, which is on the page whether or not anything is tapped.
        ready.uiSettings.isAttributionEnabled = remote != null && style?.builtIn != true
        val builder =
            if (remote != null) Style.Builder().fromUri(remote)
            else Style.Builder().fromJson(blankStyle(look.background))
        ready.setStyle(builder) { style ->
            showRoute(style, route, look)
            val saved = camera
            if (saved != null) {
                ready.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(saved[0], saved[1]), saved[2])
                )
            } else {
                fit(ready, route, (FIT_PADDING_DP * density).toInt())
            }
            probe?.routeShown = style.getLayer(LINE_LAYER) != null
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
        )
        BasemapCaption(
            attributionShown = style?.builtIn == true && !remoteFailed,
            failed = remoteFailed,
            onRetry = { remoteFailed = false },
            modifier = Modifier.align(Alignment.BottomStart).padding(CAPTION_PADDING_DP.dp),
        )
    }
}

/**
 * What the map owes a reader under it: where its data comes from (OpenStreetMap contributors, by
 * licence; the tile provider by its terms), or that the tiles did not come and the route stands
 * alone, with a way to try again.
 */
@Composable
internal fun BasemapCaption(
    attributionShown: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!attributionShown && !failed) return
    val opener = LocalLinkOpener.current
    val action = stringResource(R.string.map_attribution_action)
    val color = MaterialTheme.colorScheme.surface.copy(alpha = CAPTION_ALPHA)
    if (failed) {
        Row(
            modifier.background(color, MaterialTheme.shapes.small).testTag("map:failed"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.map_basemap_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.s),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.map_basemap_retry)) }
        }
    } else {
        // The picture is small, the thing to press is not: a whole touch target around the words.
        Box(
            modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    onClickLabel = action,
                    role = Role.Button,
                    onClick = { opener.open(BasemapStyle.COPYRIGHT_URL) },
                )
                .testTag("map:attribution"),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.background(color, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = Spacing.xs, vertical = 2.dp),
            )
        }
    }
}

/** The style of a map with nothing on it, but the colour of the page. */
private fun blankStyle(background: Int): String {
    val hex = "#%06X".format(background and 0xFFFFFF)
    return """{"version":8,"sources":{},"layers":""" +
        """[{"id":"background","type":"background","paint":{"background-color":"$hex"}}]}"""
}

private fun showRoute(style: Style, route: RideRoute, look: MapLook) {
    // One source with all the lines of the route: the cuts stay cuts, nothing joins them.
    style.addSource(GeoJsonSource(ROUTE_SOURCE, multiLineString(route)))
    style.addLayer(
        LineLayer(HALO_LAYER, ROUTE_SOURCE)
            .withProperties(
                PropertyFactory.lineColor(look.halo),
                PropertyFactory.lineWidth(HALO_WIDTH),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
    )
    style.addLayer(
        LineLayer(LINE_LAYER, ROUTE_SOURCE)
            .withProperties(
                PropertyFactory.lineColor(look.line),
                PropertyFactory.lineWidth(LINE_WIDTH),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
    )
    val start = route.lines.first().first()
    val end = route.lines.last().last()
    style.addSource(GeoJsonSource(START_SOURCE, point(start)))
    style.addSource(GeoJsonSource(END_SOURCE, point(end)))
    style.addLayer(
        CircleLayer(START_LAYER, START_SOURCE)
            .withProperties(
                PropertyFactory.circleColor(look.line),
                PropertyFactory.circleRadius(END_RADIUS),
                PropertyFactory.circleStrokeColor(look.halo),
                PropertyFactory.circleStrokeWidth(END_STROKE),
            )
    )
    style.addLayer(
        CircleLayer(END_LAYER, END_SOURCE)
            .withProperties(
                PropertyFactory.circleColor(look.halo),
                PropertyFactory.circleRadius(END_RADIUS),
                PropertyFactory.circleStrokeColor(look.line),
                PropertyFactory.circleStrokeWidth(END_STROKE),
            )
    )
}

/** GeoJSON is longitude first. Numbers are written the same way in every language. */
private fun multiLineString(route: RideRoute): String =
    route.lines.joinToString(
        separator = ",",
        prefix =
            """{"type":"Feature","properties":{},"geometry":{"type":"MultiLineString","coordinates":[""",
        postfix = "]}}",
    ) { line ->
        line.joinToString(separator = ",", prefix = "[", postfix = "]") {
            "[${it.longitude},${it.latitude}]"
        }
    }

private fun point(point: GeoPoint): String =
    """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":""" +
        "[${point.longitude},${point.latitude}]}}"

private fun fit(map: MapLibreMap, route: RideRoute, padding: Int) {
    val places = route.lines.flatten().map { LatLng(it.latitude, it.longitude) }
    val bounds = LatLngBounds.Builder().includes(places).build()
    if (bounds.latitudeSpan < TINY_SPAN && bounds.longitudeSpan < TINY_SPAN) {
        // All the points are one place: a box without size cannot be fitted.
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.center, SINGLE_PLACE_ZOOM))
    } else {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
    }
}

private const val ROUTE_SOURCE = "route"
private const val START_SOURCE = "route-start"
private const val END_SOURCE = "route-end"
private const val HALO_LAYER = "route-halo"
private const val LINE_LAYER = "route-line"
private const val START_LAYER = "route-start-dot"
private const val END_LAYER = "route-end-dot"
private const val LINE_WIDTH = 4f
private const val HALO_WIDTH = 8f
private const val END_RADIUS = 6f
private const val END_STROKE = 3f
private const val MIN_ZOOM = 2.0
private const val MAX_ZOOM = 19.0
private const val TINY_SPAN = 1e-5
private const val SINGLE_PLACE_ZOOM = 16.0
private const val FIT_PADDING_DP = 40
private const val ATTRIBUTION_MARGIN_DP = 8
private const val CAPTION_PADDING_DP = 4
private const val CAPTION_ALPHA = 0.85f
private const val DARK_LUMINANCE = 0.5f
