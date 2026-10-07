package ru.colabike.core.network

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Call
import ru.colabike.api.apis.JournalApi
import ru.colabike.api.apis.PersonalApi
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.FeedRepository
import ru.colabike.core.model.JournalChange
import ru.colabike.core.model.JournalDraft
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalPatch
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.Page
import ru.colabike.core.model.Photo
import ru.colabike.core.model.SavedChange

/** `/me/feed`: personal, so [api] is the client with the Bearer interceptor. */
class NetworkFeedRepository(
    private val api: PersonalApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FeedRepository {
    override suspend fun feed(filter: FeedFilter, cursor: String?, limit: Int): Page<FeedItem> =
        apiCall(dispatcher) {
                api.getFeed(
                    type =
                        when (filter) {
                            FeedFilter.All -> PersonalApi.TypeGetFeed.all
                            FeedFilter.Rides -> PersonalApi.TypeGetFeed.rides
                            FeedFilter.Journal -> PersonalApi.TypeGetFeed.journal
                        },
                    limit = limit,
                    cursor = cursor,
                )
            }
            .toModel(media)
}

class NetworkJournalRepository(
    private val api: JournalApi,
    private val personal: PersonalApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * The journal for a `PATCH` that names these fields as `null` (a date, a mileage taken away).
     */
    private val clearing: (Set<String>) -> JournalApi = { api },
    /** The journal for sending a picture: with its progress told and its call handed over. */
    private val uploading: (onProgress: (Float) -> Unit, onCall: (Call) -> Unit) -> JournalApi =
        { _, _ ->
            api
        },
) : JournalRepository {
    private val savedEmitter = MutableSharedFlow<SavedChange>(extraBufferCapacity = 16)
    override val savedChanges: SharedFlow<SavedChange> = savedEmitter.asSharedFlow()

    private val written = MutableSharedFlow<JournalChange>(extraBufferCapacity = 16)
    override val changes: SharedFlow<JournalChange> = written.asSharedFlow()

    /** What this session has seen of the saved state; the API gives no flag on an entry. */
    private val known = ConcurrentHashMap<String, Boolean>()

    override suspend fun ofBike(bike: BikeId, cursor: String?, limit: Int): Page<JournalSummary> {
        val uuid = uuidOrNotFound(bike.value)
        return apiCall(dispatcher) { api.listBikeJournal(uuid, limit = limit, cursor = cursor) }
            .toModel(media)
    }

    override suspend fun entry(id: JournalId): JournalEntry {
        val uuid = uuidOrNotFound(id.value)
        // The author's answer carries the version a change names; a reader's does not.
        return apiCall(dispatcher) { api.getJournalEntryWithHttpInfo(uuid).valueAndTag() }
            .let { (dto, tag) -> dto.toModel(media, tag) }
    }

    override suspend fun create(bike: BikeId, draft: JournalDraft, key: String): JournalEntry {
        // The key is a UUID by contract; a bad one is a bug here, not a request to send.
        require(runCatching { UUID.fromString(key) }.isSuccess) { "Idempotency-Key must be a UUID" }
        val uuid = uuidOrNotFound(bike.value)
        val request = draft.toRequest(BikeId(uuid.toString()))
        return apiCall(dispatcher) {
                api.createJournalEntryWithHttpInfo(UUID.fromString(key), request).valueAndTag()
            }
            .let { (dto, tag) -> dto.toModel(media, tag) }
            .also { written.tryEmit(JournalChange.Saved(it)) }
    }

    override suspend fun update(
        id: JournalId,
        patch: JournalPatch,
        version: String?,
    ): JournalEntry {
        val uuid = uuidOrNotFound(id.value)
        // An edit applies to the version that was read; without one the server would answer 428.
        val tag = version ?: throw DataError.Rejected(428, "precondition_required", "")
        if (patch.isEmpty) return entry(id)
        val nulls = buildSet {
            if (patch.clearEventDate) add("eventDate")
            if (patch.clearMileage) add("mileage")
            if (patch.clearInstallation) add("installationResult")
        }
        val request = patch.toRequest()
        return apiCall(dispatcher) {
                clearing(nulls).updateJournalEntryWithHttpInfo(uuid, tag, request).valueAndTag()
            }
            .let { (dto, newTag) -> dto.toModel(media, newTag) }
            .also { written.tryEmit(JournalChange.Saved(it)) }
    }

    override suspend fun delete(id: JournalId) {
        val uuid = uuidOrNotFound(id.value)
        try {
            apiCall(dispatcher) { api.deleteJournalEntry(uuid) }
        } catch (_: DataError.NotFound) {
            // Deleted elsewhere first: what was asked for has happened, and the lists that still
            // show the entry are told as well.
        }
        written.tryEmit(JournalChange.Removed(id))
    }

    override suspend fun saved(cursor: String?, limit: Int): Page<JournalSummary> =
        apiCall(dispatcher) { personal.listSavedJournal(limit = limit, cursor = cursor) }
            .toModel(media)
            .also { page -> page.items.forEach { known[it.id.value] = true } }

    override fun isSaved(id: JournalId): Boolean? = known[id.value]

    /** Forgets what was learned about saved entries: the next person must not inherit it. */
    fun forget() = known.clear()

    override suspend fun setSaved(id: JournalId, saved: Boolean): Boolean {
        val uuid = uuidOrNotFound(id.value)
        val answer =
            apiCall(dispatcher) {
                if (saved) api.saveJournalEntry(uuid) else api.unsaveJournalEntry(uuid)
            }
        known[id.value] = answer.saved
        savedEmitter.tryEmit(SavedChange(id, answer.saved))
        return answer.saved
    }

    override suspend fun uploadPhoto(
        id: JournalId,
        file: File,
        key: String,
        onProgress: (Float) -> Unit,
    ): Photo {
        require(runCatching { UUID.fromString(key) }.isSuccess) { "Idempotency-Key must be a UUID" }
        val uuid = uuidOrNotFound(id.value)
        val dto =
            cancellableUpload(
                dispatcher,
                api = { onCall -> uploading(onProgress, onCall) },
                send = { it.uploadJournalPhoto(uuid, UUID.fromString(key), file) },
            )
        val photo =
            media.resolve(dto.url)?.let { Photo(dto.id.toString(), it) }
                ?: throw DataError.Unexpected(null)
        written.tryEmit(JournalChange.Photos(id))
        return photo
    }

    override suspend fun deletePhoto(id: JournalId, photoId: String) {
        val entryUuid = uuidOrNotFound(id.value)
        val photoUuid = uuidOrNotFound(photoId)
        // The server answers 204 to a repeat as well: gone already is as good as removed now.
        apiCall(dispatcher) { api.deleteJournalPhoto(entryUuid, photoUuid) }
        written.tryEmit(JournalChange.Photos(id))
    }

    // A malformed id cannot name an entry; the API would answer 404 as well.
    private fun uuidOrNotFound(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw DataError.NotFound()
}
