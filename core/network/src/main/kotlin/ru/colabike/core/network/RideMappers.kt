package ru.colabike.core.network

import java.time.OffsetDateTime
import ru.colabike.api.models.MyUpcomingRide as MyUpcomingRideDto
import ru.colabike.api.models.OwnRideSummary as OwnRideSummaryDto
import ru.colabike.api.models.Ride as RideDto
import ru.colabike.api.models.RideAnalysis as RideAnalysisDto
import ru.colabike.api.models.RideAnalysisPoint as RideAnalysisPointDto
import ru.colabike.api.models.RideGeometry as RideGeometryDto
import ru.colabike.api.models.RideMetrics as RideMetricsDto
import ru.colabike.api.models.RidePage as RidePageDto
import ru.colabike.api.models.RideParticipants as RideParticipantsDto
import ru.colabike.api.models.RidePassport as RidePassportDto
import ru.colabike.api.models.RideRange as RideRangeDto
import ru.colabike.api.models.RideSummary as RideSummaryDto
import ru.colabike.core.model.AnalysisChannel
import ru.colabike.core.model.AnalysisPoint
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Page
import ru.colabike.core.model.Range
import ru.colabike.core.model.RideAnalysis
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideMetrics
import ru.colabike.core.model.RideParticipants
import ru.colabike.core.model.RidePassport
import ru.colabike.core.model.RideRecurrence
import ru.colabike.core.model.RideRole
import ru.colabike.core.model.RideRoute
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RideSummary
import ru.colabike.core.model.UpcomingRide

// The ride DTOs are four near copies of one shape (public summary, owner's summary, a plan of the
// viewer's, the page); each has its own enums, so the words are read by their wire value.

private fun status(value: String) =
    when (value) {
        "completed" -> RideStatus.Completed
        "planned" -> RideStatus.Planned
        "cancelled" -> RideStatus.Cancelled
        else -> RideStatus.Unknown
    }

private fun recurrence(value: String) =
    when (value) {
        "none" -> RideRecurrence.None
        "weekly" -> RideRecurrence.Weekly
        else -> RideRecurrence.Unknown
    }

private fun role(value: String) =
    when (value) {
        "organizer" -> RideRole.Organizer
        "accepted" -> RideRole.Accepted
        "maybe" -> RideRole.Maybe
        "invited" -> RideRole.Invited
        "cancelled" -> RideRole.Cancelled
        else -> RideRole.Unknown
    }

private fun RideMetricsDto.toModel() =
    RideMetrics(
        distanceM = distanceM,
        elapsedTimeS = elapsedTimeS,
        movingTimeS = movingTimeS,
        avgSpeedMps = avgSpeedMps,
        elevationGainM = elevationGainM,
    )

private fun RideParticipantsDto.toModel() = RideParticipants(going, maybe)

/** The moment a ride is "at": its start, or for a plan its next date. */
private fun at(startedAt: OffsetDateTime?, scheduledAt: OffsetDateTime?) =
    (startedAt ?: scheduledAt)?.toInstant()

internal fun RidePageDto.toModel(media: MediaUrls): Page<RideSummary> =
    Page(items.map { it.toModel(media) }, nextCursor)

internal fun RideSummaryDto.toModel(media: MediaUrls): RideSummary =
    RideSummary(
        id = RideId(id.toString()),
        title = title,
        status = status(status.value),
        time = at(startedAt, scheduledAt),
        metrics = metrics.toModel(),
        bike = bike.toModel(),
        author = author.toModel(media),
        recurrence = recurrence(recurrence.value),
        hasTrack = hasTrack,
        likes = likes,
        comments = comments,
        liked = liked,
        participants = participants?.toModel(),
    )

internal fun OwnRideSummaryDto.toModel(media: MediaUrls): OwnRide =
    OwnRide(
        ride =
            RideSummary(
                id = RideId(id.toString()),
                title = title,
                status = status(status.value),
                time = at(startedAt, scheduledAt),
                metrics = metrics.toModel(),
                bike = bike.toModel(),
                author = author.toModel(media),
                recurrence = recurrence(recurrence.value),
                hasTrack = hasTrack,
                likes = likes,
                comments = comments,
                liked = liked,
                participants = participants?.toModel(),
            ),
        isPublic = isPublic,
        privacyRadiusM = privacyRadiusM.takeIf { privacyEnabled },
        pointCount = pointCount,
    )

internal fun MyUpcomingRideDto.toModel(media: MediaUrls): UpcomingRide =
    UpcomingRide(
        ride =
            RideSummary(
                id = RideId(id.toString()),
                title = title,
                status = status(status.value),
                time = at(startedAt, scheduledAt),
                metrics = metrics.toModel(),
                bike = bike.toModel(),
                author = author.toModel(media),
                recurrence = recurrence(recurrence.value),
                hasTrack = hasTrack,
                likes = likes,
                comments = comments,
                liked = liked,
                participants = participants?.toModel(),
            ),
        role = role(role.value),
        occurrenceCancelled = occurrenceCancelled,
        changedAfterAnswer = changedAfterAnswer,
        meetingPoint = meetingPoint?.takeIf { it.isNotBlank() },
        meetingHidden = meetingHidden,
    )

internal fun RideDto.toModel(media: MediaUrls): RideDetail =
    RideDetail(
        summary =
            RideSummary(
                id = RideId(id.toString()),
                title = title,
                status = status(status.value),
                time = at(startedAt, scheduledAt),
                metrics = metrics.toModel(),
                bike = bike.toModel(),
                author = author.toModel(media),
                recurrence = recurrence(recurrence.value),
                hasTrack = hasTrack,
                likes = likes,
                comments = comments,
                liked = liked,
                participants = participants?.toModel(),
            ),
        description = description,
        features = features.filter { it.isNotBlank() },
        // A hidden point arrives as null with the flag; a blank one is no point.
        meetingPoint = if (meetingHidden) null else meetingPoint?.takeIf { it.isNotBlank() },
        meetingHidden = meetingHidden,
        expectedEndAt = expectedEndAt?.toInstant(),
        recruitmentClosed = recruitmentClosed,
        passport = passport?.toModel(),
        extraMetrics = extraMetrics.filterKeys { it.isNotBlank() }.filterValues { it.isFinite() },
        route = geometry?.toRoute(),
    )

private fun RideRangeDto.toModel() = Range(min, max)

private fun RidePassportDto.toModel(): RidePassport =
    RidePassport(
        areaLabel = area?.label?.takeIf { it.isNotBlank() },
        purpose = purpose?.takeIf { it.isNotBlank() },
        pace = pace?.takeIf { it.isNotBlank() },
        surface = surface?.takeIf { it.isNotBlank() },
        difficulty = difficulty?.takeIf { it.isNotBlank() },
        regroupPolicy = regroupPolicy?.takeIf { it.isNotBlank() },
        distanceKm = distanceKm?.toModel(),
        durationMinutes = durationMinutes?.toModel(),
        groupSize = groupSize?.toModel(),
        speedKmh = speedKmh?.toModel(),
        beginnerFriendly = beginnerFriendly,
    )

// --- the route and its analysis ---------------------------------------------------------------

/** More points than a phone can draw smoothly add nothing the eye can see. */
private const val MAX_ROUTE_POINTS = 20_000

/** A line of fewer than two points has no direction and is no route. */
private const val MIN_LINE_POINTS = 2

/**
 * GeoJSON `[longitude, latitude(, altitude)]` into places. A point that is not finite or not on
 * Earth ends the line it was in (the next line starts after it): nothing is invented to bridge it.
 * The lines are the server's; the cuts between them are privacy zones and stay cuts.
 */
internal fun RideGeometryDto.toRoute(): RideRoute? {
    val lines = buildList {
        for (line in coordinates) {
            var current = mutableListOf<GeoPoint>()
            for (position in line) {
                val point = position.toPoint()
                if (point != null) {
                    current += point
                } else {
                    if (current.size >= MIN_LINE_POINTS) add(current)
                    current = mutableListOf()
                }
            }
            if (current.size >= MIN_LINE_POINTS) add(current)
        }
    }
    if (lines.isEmpty()) return null
    return RideRoute(lines.thinned(MAX_ROUTE_POINTS))
}

private fun List<Double>.toPoint(): GeoPoint? {
    if (size < 2) return null
    val longitude = this[0]
    val latitude = this[1]
    val onEarth =
        longitude.isFinite() &&
            latitude.isFinite() &&
            longitude in -180.0..180.0 &&
            latitude in -90.0..90.0
    return if (onEarth) GeoPoint(latitude, longitude) else null
}

/** Keeps every n-th point of each line, and always its ends, when the whole is too long. */
private fun List<List<GeoPoint>>.thinned(limit: Int): List<List<GeoPoint>> {
    val total = sumOf { it.size }
    if (total <= limit) return this
    val step = (total + limit - 1) / limit
    return map { line ->
        buildList {
            line.forEachIndexed { index, point ->
                if (index % step == 0 || index == line.lastIndex) add(point)
            }
        }
    }
}

internal fun RideAnalysisDto.toModel(): RideAnalysis =
    RideAnalysis(
        pointCount = pointCount.coerceAtLeast(0),
        downsampled = downsampled,
        segments =
            segments.map { segment -> segment.map { it.toModel() } }.filter { it.isNotEmpty() },
    )

private fun RideAnalysisPointDto.toModel(): AnalysisPoint {
    val position = coord.toPoint()
    val values = buildMap {
        fun give(channel: AnalysisChannel, value: Double?) {
            if (value != null && value.isFinite()) put(channel, value)
        }
        give(AnalysisChannel.Elevation, elevationM)
        give(AnalysisChannel.Speed, speedMps)
        give(AnalysisChannel.Grade, gradePct)
        give(AnalysisChannel.HeartRate, hrBpm)
        give(AnalysisChannel.Cadence, cadenceRpm)
        give(AnalysisChannel.Power, powerW)
    }
    return AnalysisPoint(
        position = position,
        distanceM = distanceM?.takeIf { it.isFinite() },
        elapsedS = elapsedS?.takeIf { it.isFinite() },
        values = values,
        gaps = gaps,
    )
}
