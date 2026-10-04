package ru.colabike.core.network

import java.time.OffsetDateTime
import ru.colabike.api.models.MyUpcomingRide as MyUpcomingRideDto
import ru.colabike.api.models.OwnRideSummary as OwnRideSummaryDto
import ru.colabike.api.models.Ride as RideDto
import ru.colabike.api.models.RideMetrics as RideMetricsDto
import ru.colabike.api.models.RidePage as RidePageDto
import ru.colabike.api.models.RideParticipants as RideParticipantsDto
import ru.colabike.api.models.RidePassport as RidePassportDto
import ru.colabike.api.models.RideRange as RideRangeDto
import ru.colabike.api.models.RideSummary as RideSummaryDto
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Page
import ru.colabike.core.model.Range
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideMetrics
import ru.colabike.core.model.RideParticipants
import ru.colabike.core.model.RidePassport
import ru.colabike.core.model.RideRecurrence
import ru.colabike.core.model.RideRole
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
        hasPublicRoute = geometry?.coordinates?.any { it.isNotEmpty() } == true,
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
