package ru.colabike.core.network

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.colabike.api.apis.AccountApi
import ru.colabike.api.apis.BikesApi
import ru.colabike.api.apis.SessionsApi
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.LikeChange
import ru.colabike.core.model.LikeState
import ru.colabike.core.model.Page

class NetworkBikesRepository(
    private val api: BikesApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BikesRepository {
    private val changes = MutableSharedFlow<LikeChange>(extraBufferCapacity = 16)
    override val likeChanges: SharedFlow<LikeChange> = changes.asSharedFlow()

    override suspend fun bikes(query: BikeQuery, cursor: String?, limit: Int): Page<BikeSummary> =
        apiCall(dispatcher) {
                api.listBikes(
                    scope =
                        when (query.scope) {
                            BikeScope.Public -> BikesApi.ScopeListBikes.`public`
                            BikeScope.Mine -> BikesApi.ScopeListBikes.mine
                        },
                    // Categories are OR-ed by the server; the order is fixed for stable requests.
                    category =
                        query.categories.sorted().joinToString(",").takeIf { it.isNotEmpty() },
                    q = query.text.trim().take(MAX_QUERY).takeIf { it.isNotEmpty() },
                    limit = limit,
                    cursor = cursor,
                )
            }
            .toModel(media)

    override suspend fun bike(id: BikeId): BikeDetail {
        // A malformed id cannot name a bike; the API would answer 404 as well.
        val uuid =
            runCatching { UUID.fromString(id.value) }.getOrNull() ?: throw DataError.NotFound()
        return apiCall(dispatcher) { api.getBike(uuid) }.toModel(media)
    }

    override suspend fun setLiked(id: BikeId, liked: Boolean): LikeState {
        val uuid =
            runCatching { UUID.fromString(id.value) }.getOrNull() ?: throw DataError.NotFound()
        val answer = apiCall(dispatcher) { if (liked) api.likeBike(uuid) else api.unlikeBike(uuid) }
        return LikeState(liked = answer.liked, likes = answer.likes).also {
            changes.tryEmit(LikeChange(id, it))
        }
    }

    private companion object {
        /** The API's own limit on `q`. */
        const val MAX_QUERY = 150
    }
}

class NetworkAccountRepository(
    private val api: AccountApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountRepository {
    override suspend fun me(): Account = apiCall(dispatcher) { api.getMe() }.toAccount(media)
}

/**
 * The account's sessions. [api] must be the client with the Bearer interceptor: these calls are
 * made as the signed-in person.
 */
class NetworkAccountSessionsRepository(
    private val api: SessionsApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountSessionsRepository {
    override suspend fun sessions(): List<AccountSession> =
        apiCall(dispatcher) { api.listSessions() }.items.map { it.toModel() }

    override suspend fun revoke(id: String) {
        // A malformed id cannot name a session; the API would answer 404 as well.
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: throw DataError.NotFound()
        apiCall(dispatcher) { api.revokeSession(uuid) }
    }
}
