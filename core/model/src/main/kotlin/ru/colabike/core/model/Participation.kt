package ru.colabike.core.model

import java.time.Instant
import java.time.ZoneId

/** What a person says to one date of a plan. */
enum class ParticipationResponse {
    /** "Going". */
    Accepted,

    /** "Maybe". */
    Maybe,

    /** "Not going". */
    Declined,
}

/** The one state the server reduces a person's part in a date to. */
enum class ParticipationState {
    Organizer,
    Accepted,
    Maybe,
    Declined,

    /** Answered "going" or "maybe" to earlier terms: the new ones need a fresh answer. */
    Reconfirm,

    /** Invited and not answered. */
    Invited,
    None,

    /** A state this version of the app does not know yet. */
    Unknown,
}

enum class ViewerRole {
    Organizer,
    Invitee,
    Visitor,
    Unknown,
}

/** What changed in the last edition of a plan's terms. */
enum class AgreementChange {
    Start,
    Place,
    Route,
}

/**
 * The edition of a plan's terms: it grows whenever the start, the place or the route changes. An
 * answer of "going" or "maybe" names the edition it was given to, and the server accepts it only if
 * that is still the current one.
 */
data class RideAgreement(
    val revision: Int,
    val changes: Set<AgreementChange>,
    val changedAt: Instant?,
)

/** What became of the date a notification was about. */
enum class RequestedDateStatus {
    /** It is the nearest date. */
    Current,

    /** The date is another now: look at [RideParticipation.scheduledAt]. */
    Moved,

    /** This date, or the whole plan, is cancelled. */
    Cancelled,

    /** It has passed. */
    Past,
    Unknown,
}

data class RequestedDate(val at: Instant, val status: RequestedDateStatus)

/**
 * A person's part in a plan and one of its dates, as the server reads it for them now: the terms,
 * what changed since they answered, the meeting point they may see, and what the server will accept
 * ([allowed]). Nothing here names other participants, only how many answered.
 */
data class RideParticipation(
    val rideId: String,
    val title: String,
    val status: RideStatus,
    val description: String?,
    val features: List<String>,
    val author: Person,
    val timeZone: ZoneId,
    val recurrence: RideRecurrence,
    /** The date to answer for (what the answer is sent for); null when there is none. */
    val scheduledAt: Instant?,
    val expectedEndAt: Instant?,
    /** The date that was asked about, and what became of it. */
    val requested: RequestedDate?,
    val agreement: RideAgreement,
    val recruitmentClosed: Boolean,
    val meetingPoint: String?,
    /** There is a meeting point, shown only to the organizer and those who are going. */
    val meetingHidden: Boolean,
    val passport: RidePassport?,
    val going: Int,
    val maybe: Int,
    val role: ViewerRole,
    val state: ParticipationState,
    /** The answer to the current terms; null if none, or if it was to earlier terms. */
    val response: ParticipationResponse?,
    /** The earlier answer, while [state] is [ParticipationState.Reconfirm]. */
    val previousResponse: ParticipationResponse?,
    val changedAfterAnswer: Boolean,
    val allowed: Set<ParticipationResponse>,
) {
    /** The whole plan is cancelled. */
    val planCancelled: Boolean
        get() = status == RideStatus.Cancelled

    /** This date is cancelled but the plan goes on (a weekly series). */
    val dateCancelled: Boolean
        get() = !planCancelled && requested?.status == RequestedDateStatus.Cancelled
}

/** The answer to a response: taken, or refused because the date or the terms had changed. */
sealed interface ParticipationOutcome {
    data class Saved(val participation: RideParticipation) : ParticipationOutcome

    /**
     * Not taken: the date or the terms are not the ones that were seen, enrolment closed, or the
     * organizer answered for their own plan. [current] is the state as it is now, to decide again
     * from; null if the plan is no longer the person's to see.
     */
    data class Changed(val current: RideParticipation?) : ParticipationOutcome
}

/**
 * The part of a person in a plan (cola#343). A "going" or "maybe" is sent for the date and the
 * edition of terms that were seen; the server never confirms new terms silently.
 */
interface ParticipationRepository {
    /** The state for [occurrenceAt] (a date from a notification), or for the nearest date. */
    suspend fun get(rideId: String, occurrenceAt: Instant?): RideParticipation

    /**
     * Answers for [occurrenceAt]; "going" and "maybe" name [expectedRevision], "not going" needs
     * none. A repeat of the same answer changes nothing.
     */
    suspend fun respond(
        rideId: String,
        response: ParticipationResponse,
        occurrenceAt: Instant,
        expectedRevision: Int?,
    ): ParticipationOutcome

    /** The plans whose state changed after an answer taken here: lists refresh from this. */
    val changes: kotlinx.coroutines.flow.SharedFlow<String>
}
