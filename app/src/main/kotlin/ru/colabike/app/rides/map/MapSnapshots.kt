package ru.colabike.app.rides.map

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.lifecycle.ViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.snapshotter.MapSnapshotter
import ru.colabike.core.model.RideRoute

internal data class SnapshotLook(val line: Int, val halo: Int, val background: Int)

internal data class SnapshotKey(
    val route: RideRoute,
    val style: BasemapStyle?,
    val look: SnapshotLook,
    val width: Int,
    val height: Int,
    val density: Float,
)

/**
 * Session-owned, memory-only previews. At most two native snapshotters and 8 MiB of finished
 * images. There is no live MapView in a scrolling card and no route bitmap written to disk.
 */
internal class MapSnapshots : ViewModel() {
    private val permits = Semaphore(2)
    private val active = mutableSetOf<Job>()
    private val bitmaps =
        object : LruCache<SnapshotKey, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: SnapshotKey, value: Bitmap): Int = value.allocationByteCount
        }

    suspend fun image(context: Context, key: SnapshotKey): Bitmap? =
        withContext(Dispatchers.Main.immediate) {
            bitmaps[key]?.let {
                return@withContext it
            }
            val job = currentCoroutineContext()[Job]!!
            active += job
            try {
                permits.withPermit {
                    bitmaps[key]
                        ?: withTimeoutOrNull(10_000) { snapshot(context, key) }
                            ?.also {
                                bitmaps.put(key, it)
                            }
                }
            } finally {
                active -= job
            }
        }

    override fun onCleared() = clear()

    /** Called when the account/owner leaves; old requests cannot populate the next session. */
    fun clear() {
        active.toList().asReversed().forEach { it.cancel() }
        active.clear()
        bitmaps.evictAll()
    }
}

/** Cancellation releases a queued or running native renderer. */
private suspend fun snapshot(context: Context, key: SnapshotKey): Bitmap? =
    suspendCancellableCoroutine { continuation ->
        MapLibre.getInstance(context.applicationContext)
        val route = key.route
        val points = route.lines.flatten().map { LatLng(it.latitude, it.longitude) }
        val bounds = LatLngBounds.Builder().includes(points).include(points.first()).build()
        val style = snapshotStyle(route, key.style?.url, key.look)
        val padding = (24 * key.density).toInt()
        val options =
            MapSnapshotter.Options(
                    (key.width / key.density).toInt().coerceAtLeast(1),
                    (key.height / key.density).toInt().coerceAtLeast(1),
                )
                .withStyleBuilder(style)
                .withPadding(padding, padding, padding, (40 * key.density).toInt())
                .withPixelRatio(key.density)
                .withLogo(false)
                .withAttribution(true)
        if (bounds.latitudeSpan < 1e-5 && bounds.longitudeSpan < 1e-5) {
            options.withCameraPosition(
                CameraPosition.Builder().target(bounds.center).zoom(16.0).build()
            )
        } else {
            options.withRegion(bounds)
        }
        val snapshotter = MapSnapshotter(context.applicationContext, options)
        continuation.invokeOnCancellation {
            if (Looper.myLooper() == Looper.getMainLooper()) snapshotter.cancel()
            else Handler(Looper.getMainLooper()).post { snapshotter.cancel() }
        }
        snapshotter.start(
            { result ->
                if (continuation.isActive) continuation.resume(result.bitmap)
            },
            { _ ->
                if (continuation.isActive) continuation.resume(null)
            },
        )
    }
