package ru.colabike.app.rides.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.ScreenPoint
import com.yandex.mapkit.ScreenRect
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.Map as YandexMap
import com.yandex.mapkit.map.MapLoadedListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay
import ru.colabike.app.R
import ru.colabike.core.model.RideRoute

/**
 * Why the Yandex map is not what the person sees: the reason the app went back to OpenStreetMap.
 */
internal enum class YandexFailure {
    /** The SDK would not start: its library, a class it needs, or the key was refused outright. */
    SdkFailed,

    /** It started but the map did not load in time: no network, a key the service does not know. */
    NotLoaded,
}

/**
 * The Yandex map: the same route, the same cuts, the same camera as the OpenStreetMap one, drawn by
 * Yandex's own SDK. It is the second door to a ride's map, and it never leaves the screen empty:
 * whatever goes wrong is reported through [onUnavailable] and the caller shows the other map.
 */
internal interface YandexMaps {
    @Composable
    fun Map(route: RideRoute, modifier: Modifier, onUnavailable: (YandexFailure) -> Unit)
}

/** The Yandex Maps MapKit, started with the key of the owner's mobile app ([apiKey]). */
internal class MapKitRouteMaps(private val apiKey: String) : YandexMaps {
    @Composable
    override fun Map(route: RideRoute, modifier: Modifier, onUnavailable: (YandexFailure) -> Unit) =
        MapKitRoute(route, apiKey, modifier, onUnavailable)
}

/** The SDK is started once per process, with the key it will have for the whole of it. */
private object MapKits {
    private var started = false

    @Synchronized
    fun start(context: Context, apiKey: String) {
        if (started) return
        MapKitFactory.setApiKey(apiKey)
        MapKitFactory.initialize(context)
        started = true
    }
}

private data class YandexLook(val line: Int, val halo: Int)

@Composable
internal fun MapKitRoute(
    route: RideRoute,
    apiKey: String,
    modifier: Modifier = Modifier,
    onUnavailable: (YandexFailure) -> Unit,
    probe: MapProbe? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < DARK_LUMINANCE
    val look = YandexLook(line = scheme.primary.toArgb(), halo = scheme.surfaceContainer.toArgb())
    val density = LocalDensity.current.density
    val description = stringResource(R.string.route_map_description)
    val report by rememberUpdatedState(onUnavailable)
    // Where the hand left the camera: latitude, longitude, zoom. Kept over a turn of the screen.
    var camera by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    // The SDK may be missing a library or a class (it is built for phones with Google services, the
    // app is not): a failure of any kind to start is the map being unavailable, not a crash.
    val mapView =
        remember(context, apiKey) {
            try {
                MapKits.start(context.applicationContext, apiKey)
                MapView(context)
            } catch (e: Exception) {
                probe?.failure = "the Yandex SDK did not start: $e"
                null
            } catch (e: LinkageError) {
                probe?.failure = "the Yandex SDK did not start: $e"
                null
            }
        }

    LaunchedEffect(mapView) {
        if (mapView == null) {
            if (!reported) report(YandexFailure.SdkFailed)
            reported = true
        } else {
            // A key the service refuses and a missing network look alike from here: nothing loads.
            delay(LOAD_TIMEOUT_MS)
            if (!loaded && !reported) {
                probe?.failure = "the Yandex map did not load in ${LOAD_TIMEOUT_MS / 1000} s"
                report(YandexFailure.NotLoaded)
                reported = true
            }
        }
    }
    if (mapView == null) return

    // The SDK holds its listeners weakly: these are the only strong references to them.
    val loadedListener = remember {
        MapLoadedListener {
            loaded = true
            probe?.mapLoaded = true
        }
    }
    val cameraListener = remember {
        CameraListener { _, position, _: CameraUpdateReason, finished ->
            if (finished) {
                val target = position.target
                camera = doubleArrayOf(target.latitude, target.longitude, position.zoom.toDouble())
            }
        }
    }

    DisposableEffect(mapView) {
        val map = mapView.mapWindow.map
        // North stays up and the picture flat, as on the other map.
        map.isRotateGesturesEnabled = false
        map.isTiltGesturesEnabled = false
        map.setMapLoadedListener(WeakReference(loadedListener))
        map.addCameraListener(WeakReference(cameraListener))
        onDispose {
            map.removeCameraListener(WeakReference(cameraListener))
            map.setMapLoadedListener(WeakReference<MapLoadedListener>(null))
        }
    }

    LaunchedEffect(mapView, dark) {
        // The SDK's own night scheme, not a recolouring of its tiles.
        mapView.mapWindow.map.isNightModeEnabled = dark
    }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    if (!started) {
                        MapKitFactory.getInstance().onStart()
                        mapView.onStart()
                        started = true
                    }
                Lifecycle.Event.ON_STOP ->
                    if (started) {
                        mapView.onStop()
                        MapKitFactory.getInstance().onStop()
                        started = false
                    }
                else -> Unit
            }
        }
        // A page that arrives after the activity has started is brought up to date by this call.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // The page leaves while the activity lives: the map must not go on in the background.
            if (started) {
                mapView.onStop()
                MapKitFactory.getInstance().onStop()
            }
        }
    }

    LaunchedEffect(mapView, route, look) {
        val map = mapView.mapWindow.map
        showRoute(map, route, look, density)
        mapView.doOnLayout {
            val saved = camera
            if (saved != null) {
                map.move(CameraPosition(Point(saved[0], saved[1]), saved[2].toFloat(), 0f, 0f))
            } else {
                fit(mapView, map, route, FIT_PADDING_DP * density)
            }
            probe?.routeShown = true
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
    )
}

private fun showRoute(map: YandexMap, route: RideRoute, look: YandexLook, density: Float) {
    val objects = map.mapObjects
    objects.clear()
    // One polyline per line the server gave: the cuts stay cuts, nothing joins them.
    route.lines.forEach { line ->
        val drawn = objects.addPolyline(Polyline(line.map { Point(it.latitude, it.longitude) }))
        drawn.setStrokeColor(look.line)
        drawn.strokeWidth = LINE_WIDTH_DP
        drawn.outlineColor = look.halo
        drawn.outlineWidth = HALO_WIDTH_DP
    }
    val start = route.lines.first().first()
    val end = route.lines.last().last()
    objects.addPlacemark(
        Point(start.latitude, start.longitude),
        ImageProvider.fromBitmap(dot(look.line, look.halo, density)),
    )
    objects.addPlacemark(
        Point(end.latitude, end.longitude),
        ImageProvider.fromBitmap(dot(look.halo, look.line, density)),
    )
}

/** A round mark of the start or the end of the route: [fill] inside, [ring] around. */
private fun dot(fill: Int, ring: Int, density: Float): Bitmap {
    val radius = END_RADIUS_DP * density
    val stroke = END_STROKE_DP * density
    val size = ((radius + stroke) * 2).toInt() + 2
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = ring
    canvas.drawCircle(center, center, radius + stroke, paint)
    paint.color = fill
    canvas.drawCircle(center, center, radius, paint)
    return bitmap
}

private fun fit(view: MapView, map: YandexMap, route: RideRoute, paddingPx: Float) {
    val places = route.lines.flatten()
    val south = places.minOf { it.latitude }
    val north = places.maxOf { it.latitude }
    val west = places.minOf { it.longitude }
    val east = places.maxOf { it.longitude }
    if (north - south < TINY_SPAN && east - west < TINY_SPAN) {
        // All the points are one place: a box without size cannot be fitted.
        map.move(
            CameraPosition(Point((south + north) / 2, (west + east) / 2), SINGLE_PLACE_ZOOM, 0f, 0f)
        )
        return
    }
    val width = view.width.toFloat()
    val height = view.height.toFloat()
    val room =
        if (width > 2 * paddingPx && height > 2 * paddingPx) {
            ScreenRect(
                ScreenPoint(paddingPx, paddingPx),
                ScreenPoint(width - paddingPx, height - paddingPx),
            )
        } else {
            ScreenRect(ScreenPoint(0f, 0f), ScreenPoint(width, height))
        }
    val box = BoundingBox(Point(south, west), Point(north, east))
    map.move(map.cameraPosition(Geometry.fromBoundingBox(box), room))
}

private const val LINE_WIDTH_DP = 4f
private const val HALO_WIDTH_DP = 2f
private const val END_RADIUS_DP = 6f
private const val END_STROKE_DP = 3f
private const val TINY_SPAN = 1e-5
private const val SINGLE_PLACE_ZOOM = 16f
private const val FIT_PADDING_DP = 40
private const val LOAD_TIMEOUT_MS = 25_000L
private const val DARK_LUMINANCE = 0.5f
