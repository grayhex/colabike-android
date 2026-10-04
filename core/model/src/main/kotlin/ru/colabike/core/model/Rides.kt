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
 * The organizer's expectations of a plan. Every field is optional; the words (purpose, pace,
 * surface, difficulty, regroup) are an open set: an unknown one is shown as it is.
 */
data class RidePassport(
    val areaLabel: String? = null,
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
    /** The ride has a route the viewer may see. */
    val hasPublicRoute: Boolean,
)
