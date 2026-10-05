package ru.colabike.core.network

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.PlanningApi
import ru.colabike.api.models.RideIntent as IntentDto
import ru.colabike.api.models.RideIntentRequest
import ru.colabike.api.models.RideIntentRequestPassport
import ru.colabike.api.models.RideIntentRequestPassportArea
import ru.colabike.api.models.RideIntentRequestPassportDistanceKm
import ru.colabike.api.models.RideIntentRequestPassportDurationMinutes
import ru.colabike.api.models.RideIntentRequestPassportGroupSize
import ru.colabike.api.models.RideIntentRequestPassportSpeedKmh
import ru.colabike.api.models.RideIntentRequestWindowsInner
import ru.colabike.core.model.DataError
import ru.colabike.core.model.IntentDraft
import ru.colabike.core.model.IntentFold
import ru.colabike.core.model.IntentReadiness
import ru.colabike.core.model.IntentStatus
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.IntentWindow
import ru.colabike.core.model.IntentsRepository
import ru.colabike.core.model.Page
import ru.colabike.core.model.RideIntent

/**
 * Intentions to ride (cola#343). A create is sent with an `Idempotency-Key`: the key is the
 * intention's identity, so a repeat after a lost answer returns the same intention and not a second
 * one. A change names the version that was read (`If-Match`), and a change another device got in
 * first is a 412 the screen reads again, never an overwrite nobody saw.
 */
class NetworkIntentsRepository(
    private val api: PlanningApi,
    private val keyed: (key: String) -> PlanningApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IntentsRepository {
    override suspend fun own(cursor: String?, limit: Int): Page<RideIntent> =
        apiCall(dispatcher) { api.listOwnRideIntents(limit, cursor) }
            .let { page -> Page(page.items.map { it.toModel(media, null) }, page.nextCursor) }

    override suspend fun community(cursor: String?, limit: Int): Page<RideIntent> =
        apiCall(dispatcher) { api.listCommunityRideIntents(limit, cursor) }
            .let { page -> Page(page.items.map { it.toModel(media, null) }, page.nextCursor) }

    override suspend fun get(id: String): RideIntent {
        val uuid = uuidOrNotFound(id)
        return apiCall(dispatcher) { api.getRideIntentWithHttpInfo(uuid).valueAndTag() }
            .let { (dto, tag) -> dto.toModel(media, tag) }
    }

    override suspend fun create(draft: IntentDraft, key: String): RideIntent {
        // The key is a UUID by contract; a bad one is a bug here, not a request to send.
        require(runCatching { UUID.fromString(key) }.isSuccess) { "Idempotency-Key must be a UUID" }
        val request = draft.toRequest()
        return apiCall(dispatcher) {
                keyed(key).createRideIntentWithHttpInfo(request).valueAndTag()
            }
            .let { (dto, tag) -> dto.toModel(media, tag) }
    }

    override suspend fun replace(id: String, draft: IntentDraft, version: String?): RideIntent {
        val uuid = uuidOrNotFound(id)
        val request = draft.toRequest()
        return apiCall(dispatcher) {
                api.replaceRideIntentWithHttpInfo(uuid, request, version).valueAndTag()
            }
            .let { (dto, tag) -> dto.toModel(media, tag) }
    }

    override suspend fun cancel(id: String): RideIntent {
        val uuid = uuidOrNotFound(id)
        return apiCall(dispatcher) { api.cancelRideIntentWithHttpInfo(uuid).valueAndTag() }
            .let { (dto, tag) -> dto.toModel(media, tag) }
    }

    override suspend fun delete(id: String) {
        val uuid = uuidOrNotFound(id)
        apiCall(dispatcher) { api.deleteRideIntent(uuid) }
    }

    private fun uuidOrNotFound(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw DataError.NotFound()
}

internal fun IntentDto.toModel(media: MediaUrls, version: String?): RideIntent =
    RideIntent(
        id = id.toString(),
        own = own,
        readiness =
            when (readiness) {
                IntentDto.Readiness.ready -> IntentReadiness.Ready
                else -> IntentReadiness.Considering
            },
        // A zone this phone does not know cannot be shown in local time; UTC is the honest default.
        timeZone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.of("UTC")),
        passport = passport.toModel(),
        windows = windows.map { IntentWindow(it.startsAt.toInstant(), it.endsAt.toInstant()) },
        meetNewPeople = meetNewPeople,
        visibility =
            when (visibility) {
                IntentDto.Visibility.`private` -> IntentVisibility.Private
                else -> IntentVisibility.Community
            },
        status =
            when (status) {
                IntentDto.Status.active -> IntentStatus.Active
                IntentDto.Status.cancelled -> IntentStatus.Cancelled
                IntentDto.Status.expired -> IntentStatus.Expired
                else -> IntentStatus.Unknown
            },
        allowSuggestions = allowSuggestions,
        author = author.toModel(media),
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        version = version,
    )

private val LocalMinutes: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/** The whole intention as the server takes it: local times with no offset, the zone by name. */
internal fun IntentDraft.toRequest(): RideIntentRequest =
    RideIntentRequest(
        readiness =
            when (readiness) {
                IntentReadiness.Ready -> RideIntentRequest.Readiness.ready
                IntentReadiness.Considering -> RideIntentRequest.Readiness.considering
            },
        timeZone = timeZone.id,
        windows =
            windows.map {
                RideIntentRequestWindowsInner(
                    startLocal = it.start.format(LocalMinutes),
                    endLocal = it.end.format(LocalMinutes),
                    startFold =
                        it.startFold?.let { fold ->
                            if (fold == IntentFold.Earlier)
                                RideIntentRequestWindowsInner.StartFold.earlier
                            else RideIntentRequestWindowsInner.StartFold.later
                        },
                    endFold =
                        it.endFold?.let { fold ->
                            if (fold == IntentFold.Earlier)
                                RideIntentRequestWindowsInner.EndFold.earlier
                            else RideIntentRequestWindowsInner.EndFold.later
                        },
                )
            },
        passport = passport.toRequest(),
        visibility =
            when (visibility) {
                IntentVisibility.Private -> RideIntentRequest.Visibility.`private`
                IntentVisibility.Community -> RideIntentRequest.Visibility.community
            },
        allowSuggestions = allowSuggestions,
        meetNewPeople = meetNewPeople,
    )

private fun ru.colabike.core.model.RidePassport.toRequest(): RideIntentRequestPassport =
    RideIntentRequestPassport(
        area =
            RideIntentRequestPassportArea(
                label = areaLabel.orEmpty(),
                center = area?.let { listOf(it.longitude, it.latitude) },
                radiusM = area?.radiusM,
            ),
        // The planner's words; a value the request does not know is left out rather than guessed.
        purpose =
            RideIntentRequestPassport.Purpose.entries.firstOrNull { it.value == purpose }
                ?: RideIntentRequestPassport.Purpose.leisure,
        pace = RideIntentRequestPassport.Pace.entries.firstOrNull { it.value == pace },
        surface = RideIntentRequestPassport.Surface.entries.firstOrNull { it.value == surface },
        difficulty =
            RideIntentRequestPassport.Difficulty.entries.firstOrNull { it.value == difficulty },
        regroupPolicy =
            RideIntentRequestPassport.RegroupPolicy.entries.firstOrNull {
                it.value == regroupPolicy
            },
        distanceKm = distanceKm?.let { RideIntentRequestPassportDistanceKm(it.min, it.max) },
        durationMinutes =
            durationMinutes?.let {
                RideIntentRequestPassportDurationMinutes(it.min.toInt(), it.max.toInt())
            },
        groupSize =
            groupSize?.let { RideIntentRequestPassportGroupSize(it.min.toInt(), it.max.toInt()) },
        speedKmh = speedKmh?.let { RideIntentRequestPassportSpeedKmh(it.min, it.max) },
        beginnerFriendly = beginnerFriendly,
    )
