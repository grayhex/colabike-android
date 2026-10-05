package ru.colabike.app.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A point from the phone's approximate location. It lives in memory only for as long as it takes to
 * round it to a grid cell ([ru.colabike.core.model.NearbyGrid.centerOf]) and is never printed: not
 * in a log, not in a message, not in a state that is saved.
 */
class CoarseFix(val longitude: Double, val latitude: Double) {
    override fun toString() = "CoarseFix"
}

/** What reading the approximate place came to. */
sealed interface CoarseResult {
    data class Located(val fix: CoarseFix) : CoarseResult

    /** The person did not give (or took back) the approximate location. */
    data object NoPermission : CoarseResult

    /** The phone's location switch is off. */
    data object ServiceOff : CoarseResult

    /** Nothing to read: no network provider on the phone, or it did not answer in time. */
    data object Unavailable : CoarseResult
}

/**
 * The phone's approximate place, read once and on request: the app never follows the phone, never
 * asks for the precise location and never asks for the background one (docs/adr/0018).
 */
interface CoarseLocation {
    /** Whether the approximate location is allowed now. */
    fun granted(): Boolean

    /** The place now, once. No updates are kept open after the answer. */
    suspend fun current(): CoarseResult
}

/** A phone without the permission or the provider: the area is then chosen on the site. */
object NoCoarseLocation : CoarseLocation {
    override fun granted() = false

    override suspend fun current(): CoarseResult = CoarseResult.Unavailable
}

/**
 * The platform's own [LocationManager], with no Google Play services: with only the approximate
 * permission the network provider is what the system lets the app use. A fix older than
 * [maxAgeMinutes] is not taken for the place now.
 */
class AndroidCoarseLocation(
    private val context: Context,
    private val timeoutSeconds: Long = 20,
    private val maxAgeMinutes: Long = 30,
) : CoarseLocation {
    override fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun current(): CoarseResult {
        if (!granted()) return CoarseResult.NoPermission
        val manager =
            context.getSystemService(LocationManager::class.java) ?: return CoarseResult.Unavailable
        if (!manager.isLocationEnabled) return CoarseResult.ServiceOff
        val provider = LocationManager.NETWORK_PROVIDER
        return try {
            if (!manager.isProviderEnabled(provider)) return CoarseResult.Unavailable
            val fresh =
                withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val signal = CancellationSignal()
                        continuation.invokeOnCancellation { signal.cancel() }
                        manager.getCurrentLocation(
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context),
                        ) {
                            continuation.resume(it)
                        }
                    }
                }
            val location = fresh ?: manager.getLastKnownLocation(provider)?.takeIf { it.isRecent() }
            if (location == null) CoarseResult.Unavailable
            else CoarseResult.Located(CoarseFix(location.longitude, location.latitude))
        } catch (_: SecurityException) {
            // The permission was taken back between the check and the read.
            CoarseResult.NoPermission
        } catch (_: IllegalArgumentException) {
            // A phone without the provider.
            CoarseResult.Unavailable
        }
    }

    private fun Location.isRecent(): Boolean =
        SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos <=
            TimeUnit.MINUTES.toNanos(maxAgeMinutes)
}
