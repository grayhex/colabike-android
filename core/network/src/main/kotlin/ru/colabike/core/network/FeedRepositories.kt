package ru.colabike.core.network

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.colabike.api.apis.JournalApi
import ru.colabike.api.apis.PersonalApi
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.FeedRepository
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.Page
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
) : JournalRepository {
    private val changes = MutableSharedFlow<SavedChange>(extraBufferCapacity = 16)
    override val savedChanges: SharedFlow<SavedChange> = changes.asSharedFlow()

    /** What this session has seen of the saved state; the API gives no flag on an entry. */
    private val known = ConcurrentHashMap<String, Boolean>()

    override suspend fun ofBike(bike: BikeId, cursor: String?, limit: Int): Page<JournalSummary> {
        val uuid = uuidOrNotFound(bike.value)
        return apiCall(dispatcher) { api.listBikeJournal(uuid, limit = limit, cursor = cursor) }
            .toModel(media)
    }

    override suspend fun entry(id: JournalId): JournalEntry {
        val uuid = uuidOrNotFound(id.value)
        return apiCall(dispatcher) { api.getJournalEntry(uuid) }.toModel(media)
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
        changes.tryEmit(SavedChange(id, answer.saved))
        return answer.saved
    }

    // A malformed id cannot name an entry; the API would answer 404 as well.
    private fun uuidOrNotFound(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw DataError.NotFound()
}
