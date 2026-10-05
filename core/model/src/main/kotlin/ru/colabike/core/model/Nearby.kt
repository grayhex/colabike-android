package ru.colabike.core.model

import java.time.Instant
import kotlin.math.floor
import kotlin.math.roundToLong

/** Where the area in force came from: chosen by hand (on the site) or confirmed by this phone. */
enum class NearbySource {
    Manual,
    Device,
}

/**
 * The person's private area of "rides near me" as the server keeps it: the centre of a grid cell
 * and a radius, never a point they stood at. The coordinates are not printed: they are not for
 * logs.
 */
data class NearbyArea(
    /** The name a person gave a hand-picked district; a phone's area has none. */
    val label: String?,
    val longitude: Double,
    val latitude: Double,
    val radiusM: Int,
) {
    override fun toString() = "NearbyArea(radiusM=$radiusM)"
}

/** The kinds of ride the person wants to hear about; an empty set means any. */
data class NearbyPreferences(
    val purposes: Set<String> = emptySet(),
    val paces: Set<String> = emptySet(),
    val surfaces: Set<String> = emptySet(),
)

/** The size of a grid cell in degrees: what a phone rounds its place to before it says anything. */
data class NearbyGrid(val latStep: Double, val lngStep: Double) {
    /**
     * The centre of the cell that holds the point: the only place that is sent, and the same
     * arithmetic as the server's (`snapToCell`), so that the server accepts it as a cell centre.
     */
    fun centerOf(longitude: Double, latitude: Double): Pair<Double, Double> =
        cell(longitude, lngStep) to cell(latitude, latStep)

    private fun cell(value: Double, step: Double): Double {
        val centre = (floor(value / step + EPSILON) + 0.5) * step
        // Five decimals, as the server keeps them.
        return (centre * SCALE).roundToLong() / SCALE
    }

    private companion object {
        const val EPSILON = 1e-9
        const val SCALE = 100_000.0
    }
}

/** The server's bounds for the area and what a phone's area lives. */
data class NearbyLimits(
    val minRadiusM: Int,
    val maxRadiusM: Int,
    val radiusStepM: Int,
    val deviceTtlHours: Int,
    val grid: NearbyGrid,
)

/**
 * The radii offered, in metres: inside the server's bounds and a whole number of its steps, never a
 * finer figure. Never empty: the lower bound is always one.
 */
fun NearbyLimits.radiusChoices(): List<Int> =
    NearbyChoices.radiiKm
        .map { it * 1000 }
        .filter { it in minRadiusM..maxRadiusM && it % radiusStepM == 0 }
        .ifEmpty { listOf(minRadiusM) }

/** The radius to start from: the one the person has, else 10 km, moved onto what is offered. */
fun NearbyLimits.startRadius(current: Int?): Int {
    val choices = radiusChoices()
    val wanted = current ?: 10_000
    return choices.minByOrNull { kotlin.math.abs(it - wanted) } ?: minRadiusM
}

/**
 * What a person can choose to hear about: the words of the site's planner, which the server accepts
 * and nothing else. The labels are the app's.
 */
object NearbyChoices {
    val purposes = listOf("leisure", "social", "training", "exploration", "adventure")
    val paces = listOf("relaxed", "moderate", "sporty")
    val surfaces = listOf("asphalt", "gravel", "trail", "mixed")

    /** How far ahead to look, in days; the server takes 1 to 30. */
    val horizonsDays = listOf(3, 7, 14, 30)

    /** The radii on offer in kilometres, before the server's bounds are applied. */
    val radiiKm = listOf(5, 10, 15, 20, 30, 50, 75, 100)
}

/**
 * The area and the settings of "rides near me". Turning it on is a consent of its own: it is not
 * the consent to publish an intention, to receive suggestions or to be pushed. [version] is the
 * server's tag of what was read; a change names it, so that two phones do not overwrite each other.
 */
data class NearbySettings(
    /** The operator's switch: false means nothing is read or kept, and only turning off is left. */
    val available: Boolean,
    val enabled: Boolean,
    val source: NearbySource?,
    val area: NearbyArea?,
    val observedAt: Instant?,
    val expiresAt: Instant?,
    /** A phone's area whose term has passed: kept for nothing, used for nothing. */
    val expired: Boolean,
    val horizonDays: Int,
    val preferences: NearbyPreferences,
    val limits: NearbyLimits,
    val version: String?,
)

/** Only what is named changes. */
data class NearbyChange(
    val enabled: Boolean? = null,
    val horizonDays: Int? = null,
    val purposes: Set<String>? = null,
    val paces: Set<String>? = null,
    val surfaces: Set<String>? = null,
) {
    val isEmpty: Boolean
        get() =
            enabled == null &&
                horizonDays == null &&
                purposes == null &&
                paces == null &&
                surfaces == null
}

/** Why a ride is offered; it names no place and no distance. */
enum class NearbyReason {
    /** In the area the person chose. */
    Nearby,

    /** On a date inside a window of the person's own intention. */
    Intent,
}

/** One ride on now in the person's area. */
data class NearbyOffer(val ride: RideSummary, val reasons: Set<NearbyReason>)

/** Why the list is what it is: ready, or what the person has to do (or cannot). */
enum class NearbyOffersState {
    Ready,
    Off,
    NoArea,
    Expired,
    Unavailable,
}

data class NearbyOffers(val state: NearbyOffersState, val items: List<NearbyOffer>)

/**
 * The private area of "rides near me" (cola#343). A phone's place is rounded to a grid cell before
 * it is sent ([NearbyGrid.centerOf]); the server refuses a finer point. Nothing here keeps a
 * history of places.
 */
interface NearbyRepository {
    /** The area and the settings as they are now, with the version a change must name. */
    suspend fun settings(): NearbySettings

    /** Turns it on or off, sets the horizon and the kinds; the answer is the state afterwards. */
    suspend fun change(change: NearbyChange, version: String?): NearbySettings

    /**
     * Confirms the area from this phone: the centre of the cell it is in and the radius.
     * [replaceManual] is the person's explicit yes to replacing an area chosen by hand.
     */
    suspend fun confirmDeviceArea(
        longitude: Double,
        latitude: Double,
        radiusM: Int,
        replaceManual: Boolean,
        version: String?,
    ): NearbySettings

    /** Removes the area and nothing else: the switch and the kinds stay. */
    suspend fun removeArea(version: String?): NearbySettings

    /** Opts out: the area, the switch and the kinds, all of it. */
    suspend fun forget()

    /** The rides on now in the area, on request: nothing is sent and nothing is recorded. */
    suspend fun offers(limit: Int = 10): NearbyOffers
}
