package ru.colabike.core.network

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.AccountApi
import ru.colabike.api.apis.BikesApi
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page

class NetworkBikesRepository(
    private val api: BikesApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BikesRepository {
    override suspend fun bikes(scope: BikeScope, cursor: String?, limit: Int): Page<BikeSummary> =
        apiCall(dispatcher) {
                api.listBikes(
                    scope =
                        when (scope) {
                            BikeScope.Public -> BikesApi.ScopeListBikes.`public`
                            BikeScope.Mine -> BikesApi.ScopeListBikes.mine
                        },
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
}

class NetworkAccountRepository(
    private val api: AccountApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountRepository {
    override suspend fun me(): Account = apiCall(dispatcher) { api.getMe() }.toAccount(media)
}
