package ru.colabike.core.network

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.colabike.api.apis.PlanningApi
import ru.colabike.api.infrastructure.ClientError
import ru.colabike.api.infrastructure.ClientException
import ru.colabike.api.infrastructure.Serializer
import ru.colabike.api.models.RideParticipation as ParticipationDto
import ru.colabike.api.models.RideParticipationConflict
import ru.colabike.api.models.RideParticipationRequest
import ru.colabike.core.model.AgreementChange
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ParticipationOutcome
import ru.colabike.core.model.ParticipationRepository
import ru.colabike.core.model.ParticipationResponse
import ru.colabike.core.model.ParticipationState
import ru.colabike.core.model.RequestedDate
import ru.colabike.core.model.RequestedDateStatus
import ru.colabike.core.model.RideAgreement
import ru.colabike.core.model.RideParticipation
import ru.colabike.core.model.RideRecurrence
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.ViewerRole

/**
 * The person's part in a plan and one of its dates. An answer names the date and the edition of the
 * terms that were seen. A 409 is not a failure to retry but a state to decide again from: the
 * answer was not taken and the body carries the plan as it is now.
 */
class NetworkParticipationRepository(
    private val api: PlanningApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ParticipationRepository {
    private val mutableChanges = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val changes: SharedFlow<String> = mutableChanges.asSharedFlow()

    override suspend fun get(rideId: String, occurrenceAt: Instant?): RideParticipation {
        val id = uuidOrNotFound(rideId)
        val at = occurrenceAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        return apiCall(dispatcher) { api.getRideParticipation(id, at) }.toModel(media)
    }

    override suspend fun respond(
        rideId: String,
        response: ParticipationResponse,
        occurrenceAt: Instant,
        expectedRevision: Int?,
    ): ParticipationOutcome {
        val id = uuidOrNotFound(rideId)
        val request =
            RideParticipationRequest(
                response =
                    when (response) {
                        ParticipationResponse.Accepted -> RideParticipationRequest.Response.accepted
                        ParticipationResponse.Maybe -> RideParticipationRequest.Response.maybe
                        ParticipationResponse.Declined -> RideParticipationRequest.Response.declined
                    },
                occurrenceAt = OffsetDateTime.ofInstant(occurrenceAt, ZoneOffset.UTC),
                // "Not going" needs no edition; the server would ignore it, so none is sent.
                expectedAgreementRevision =
                    expectedRevision.takeIf { response != ParticipationResponse.Declined },
            )
        val outcome =
            apiCall(dispatcher) {
                try {
                    ParticipationOutcome.Saved(api.respondToRide(id, request).toModel(media))
                } catch (e: ClientException) {
                    if (e.statusCode == CONFLICT) ParticipationOutcome.Changed(current(e, media))
                    else throw e
                }
            }
        if (outcome is ParticipationOutcome.Saved) mutableChanges.tryEmit(rideId)
        return outcome
    }

    private fun uuidOrNotFound(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw DataError.NotFound()

    private companion object {
        const val CONFLICT = 409
    }
}

/** The plan as it is now, from the body of a 409; null if the body has none or the plan is gone. */
private fun current(error: ClientException, media: MediaUrls): RideParticipation? {
    val body = (error.response as? ClientError<*>)?.body as? String ?: return null
    return runCatching {
        Serializer.kotlinxSerializationJson
            .decodeFromString(RideParticipationConflict.serializer(), body)
            .current
            ?.toModel(media)
    }
        .getOrNull()
}

internal fun ParticipationDto.toModel(media: MediaUrls): RideParticipation =
    RideParticipation(
        rideId = rideId.toString(),
        title = title,
        status = rideStatus(status.value),
        description = description?.takeIf { it.isNotBlank() },
        features = features.filter { it.isNotBlank() },
        author = author.toModel(media),
        timeZone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.of("UTC")),
        recurrence = rideRecurrence(recurrence.value),
        scheduledAt = scheduledAt?.toInstant(),
        expectedEndAt = expectedEndAt?.toInstant(),
        requested =
            requested?.let {
                RequestedDate(
                    it.at.toInstant(),
                    when (it.status.value) {
                        "current" -> RequestedDateStatus.Current
                        "moved" -> RequestedDateStatus.Moved
                        "cancelled" -> RequestedDateStatus.Cancelled
                        "past" -> RequestedDateStatus.Past
                        else -> RequestedDateStatus.Unknown
                    },
                )
            },
        agreement =
            RideAgreement(
                revision = agreement.revision,
                changes =
                    agreement.changes
                        .mapNotNull {
                            when (it.value) {
                                "start" -> AgreementChange.Start
                                "place" -> AgreementChange.Place
                                "route" -> AgreementChange.Route
                                else -> null
                            }
                        }
                        .toSet(),
                changedAt = agreement.changedAt?.toInstant(),
            ),
        recruitmentClosed = recruitmentClosed,
        // A hidden point arrives as null with the flag; a blank one is no point.
        meetingPoint = if (meetingHidden) null else meetingPoint?.takeIf { it.isNotBlank() },
        meetingHidden = meetingHidden,
        passport = passport?.toModel(),
        going = participants.going.coerceAtLeast(0),
        maybe = participants.maybe.coerceAtLeast(0),
        role =
            when (viewer.role.value) {
                "organizer" -> ViewerRole.Organizer
                "invitee" -> ViewerRole.Invitee
                "visitor" -> ViewerRole.Visitor
                else -> ViewerRole.Unknown
            },
        state =
            when (viewer.participation.value) {
                "organizer" -> ParticipationState.Organizer
                "accepted" -> ParticipationState.Accepted
                "maybe" -> ParticipationState.Maybe
                "declined" -> ParticipationState.Declined
                "reconfirm" -> ParticipationState.Reconfirm
                "invited" -> ParticipationState.Invited
                "none" -> ParticipationState.None
                else -> ParticipationState.Unknown
            },
        response = viewer.response?.value?.let(::response),
        previousResponse = viewer.previousResponse?.value?.let(::response),
        changedAfterAnswer = viewer.changedAfterAnswer,
        // What the app does not know how to send is not offered.
        allowed = viewer.allowedResponses.mapNotNull { response(it.value) }.toSet(),
    )

private fun response(value: String): ParticipationResponse? =
    when (value) {
        "accepted" -> ParticipationResponse.Accepted
        "maybe" -> ParticipationResponse.Maybe
        "declined" -> ParticipationResponse.Declined
        else -> null
    }

private fun rideStatus(value: String) =
    when (value) {
        "planned" -> RideStatus.Planned
        "cancelled" -> RideStatus.Cancelled
        "completed" -> RideStatus.Completed
        else -> RideStatus.Unknown
    }

private fun rideRecurrence(value: String) =
    when (value) {
        "none" -> RideRecurrence.None
        "weekly" -> RideRecurrence.Weekly
        else -> RideRecurrence.Unknown
    }
