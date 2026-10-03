package ru.colabike.core.model

import java.time.Instant

data class RideSummary(
    val id: RideId,
    val title: String,
    val status: RideStatus,
    /** When it happened (a completed ride) or is planned to start. */
    val time: Instant?,
    /** Null for a plan without a track. */
    val distanceMeters: Int?,
    val movingTimeSeconds: Int?,
    val bikeName: String?,
    val author: Person,
)

enum class RideStatus {
    Completed,
    Planned,
    /** A status this version of the app does not know yet. */
    Unknown,
}
