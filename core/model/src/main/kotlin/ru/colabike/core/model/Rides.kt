package ru.colabike.core.model

import java.time.Instant

enum class RideStatus {
    Completed,
    Planned,

    /** Only in the owner's own lists and in one's upcoming plans; the public API has none. */
    Cancelled,

    /** A status this version of the app does not know yet. */
    Unknown,
}

enum class RideRecurrence {
    None,
    Weekly,

    /** A regularity this version does not know yet: shown as nothing, never guessed. */
    Unknown,
}

/** What a ride measured. Every number is optional: a plan without a track has none. */
data class RideMetrics(
    val distanceM: Int? = null,
    val elapsedTimeS: Int? = null,
    val movingTimeS: Int? = null,
    val avgSpeedMps: Double? = null,
    val elevationGainM: Double? = null,
)

/** How many people answered a plan, without names (the API gives none). */
data class RideParticipants(val going: Int, val maybe: Int)

/** A ride in a list: no geometry and no description, to keep the list light. */
data class RideSummary(
    val id: RideId,
    val title: String,
    val status: RideStatus,
    /**
     * When it happened (a completed ride) or the next date of the plan (of a weekly series, the
     * next one). Null for a track that came without a time.
     */
    val time: Instant?,
    val metrics: RideMetrics,
    val bike: BikeRef?,
    val author: Person,
    val recurrence: RideRecurrence = RideRecurrence.None,
    val hasTrack: Boolean = false,
    val likes: Int = 0,
    val comments: Int = 0,
    val liked: Boolean = false,
    val participants: RideParticipants? = null,
)

/** The owner's own ride in their list, in any state, public or not. */
data class OwnRide(
    val ride: RideSummary,
    val isPublic: Boolean,
    val privacyRadiusM: Int?,
    val pointCount: Int,
) {
    /** The ride's page answers 404 for a private or cancelled ride, even to its owner. */
    val hasPage: Boolean
        get() = isPublic && ride.status != RideStatus.Cancelled
}

/** What the viewer is in a plan. */
enum class RideRole {
    Organizer,
    Accepted,
    Maybe,
    Invited,
    Cancelled,
    Unknown,
}

/** A plan in the viewer's own upcoming list, with the viewer's part in it. */
data class UpcomingRide(
    val ride: RideSummary,
    val role: RideRole,
    /** One date of a series was cancelled; the series goes on. */
    val occurrenceCancelled: Boolean,
    /** The plan changed after the viewer answered: the answer has to be confirmed again. */
    val changedAfterAnswer: Boolean,
    val meetingPoint: String?,
    val meetingHidden: Boolean,
) {
    /** A cancelled plan has no page (404). */
    val hasPage: Boolean
        get() = ride.status != RideStatus.Cancelled
}

data class Range(val min: Double, val max: Double)

/**
 * A plan's rough area: the centre (to a hundredth of a degree, not a point) and the radius. It is
 * not printed: a place does not belong in a log, even a rough one.
 */
data class RideAreaPoint(val longitude: Double, val latitude: Double, val radiusM: Int) {
    override fun toString() = "RideAreaPoint(radiusM=$radiusM)"
}

/**
 * The organizer's expectations of a plan. Every field is optional; the words (purpose, pace,
 * surface, difficulty, regroup) are an open set: an unknown one is shown as it is.
 */
data class RidePassport(
    val areaLabel: String? = null,
    /** The rough area on the map, if the author gave one; kept so that an edit sends it back. */
    val area: RideAreaPoint? = null,
    val purpose: String? = null,
    val pace: String? = null,
    val surface: String? = null,
    val difficulty: String? = null,
    val regroupPolicy: String? = null,
    val distanceKm: Range? = null,
    val durationMinutes: Range? = null,
    val groupSize: Range? = null,
    val speedKmh: Range? = null,
    val beginnerFriendly: Boolean? = null,
)

/** The page of a ride or a plan, without the route (it comes with the map). */
data class RideDetail(
    val summary: RideSummary,
    val description: String,
    /** Words the author gave to the ride (an open set, shown as they are). */
    val features: List<String>,
    /** The meeting point, or null if there is none or it is hidden ([meetingHidden]). */
    val meetingPoint: String?,
    /** There is a meeting point, but only the organizer and those who answered see it. */
    val meetingHidden: Boolean,
    val expectedEndAt: Instant?,
    val recruitmentClosed: Boolean,
    val passport: RidePassport?,
    /** Sensor and device numbers the author allowed to show, by the server's own names. */
    val extraMetrics: Map<String, Double>,
    /** The route the server allows this viewer to see, or null if there is none. */
    val route: RideRoute?,
) {
    val hasPublicRoute: Boolean
        get() = route != null
}

/** A place in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * The public route as the server gave it: several lines, because the parts inside a privacy zone
 * are cut out. Whatever is between two lines is hidden on purpose and is never joined by a segment.
 */
data class RideRoute(val lines: List<List<GeoPoint>>) {
    val pointCount: Int
        get() = lines.sumOf { it.size }
}

/** A series of a ride's analysis. [bit] is its place in the server's mask of gaps. */
enum class AnalysisChannel(val bit: Int) {
    Elevation(1),
    Speed(2),
    Grade(4),
    HeartRate(8),
    Cadence(16),
    Power(32),
}

/**
 * One shown point of the analysis. There is no absolute time, only [elapsedS] along the way;
 * [values] holds the channels the server gave (a sensor the author did not open is absent), and
 * [gaps] says which channels lost values between the previous point and this one.
 */
data class AnalysisPoint(
    val position: GeoPoint?,
    val distanceM: Double?,
    val elapsedS: Double?,
    val values: Map<AnalysisChannel, Double>,
    val gaps: Int,
) {
    fun gapBefore(channel: AnalysisChannel): Boolean = gaps and channel.bit != 0
}

/**
 * The series of a public track for charts. [segments] are continuous parts with breaks between them
 * (privacy cuts); a line is never drawn across a break, nor across a gap of one channel.
 */
data class RideAnalysis(
    val pointCount: Int,
    val downsampled: Boolean,
    val segments: List<List<AnalysisPoint>>,
) {
    /** The channels that have at least two values somewhere: enough to draw a line. */
    val channels: List<AnalysisChannel> =
        AnalysisChannel.entries.filter { channel ->
            segments.sumOf { segment -> segment.count { channel in it.values } } >= 2
        }
}
