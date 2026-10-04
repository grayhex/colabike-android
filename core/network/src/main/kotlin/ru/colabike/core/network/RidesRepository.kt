package ru.colabike.core.network

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.PersonalApi
import ru.colabike.api.apis.RidesApi
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Page
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideSummary
import ru.colabike.core.model.RidesRepository
import ru.colabike.core.model.UpcomingRide

/**
 * Rides and plans. [api] and [personal] are the client with the Bearer interceptor: the public
 * lists work without a session, the personal ones need one.
 */
class NetworkRidesRepository(
    private val api: RidesApi,
    private val personal: PersonalApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RidesRepository {
    override suspend fun completed(query: String?, cursor: String?, limit: Int): Page<RideSummary> =
        apiCall(dispatcher) { api.listRides(limit, cursor, text(query)) }.toModel(media)

    override suspend fun upcoming(query: String?, cursor: String?, limit: Int): Page<RideSummary> =
        apiCall(dispatcher) { api.listUpcomingRides(limit, cursor, text(query)) }.toModel(media)

    override suspend fun ofBike(
        bike: BikeId,
        query: String?,
        cursor: String?,
        limit: Int,
    ): Page<RideSummary> {
        val id = uuidOrNotFound(bike.value)
        return apiCall(dispatcher) { api.listBikeRides(id, limit, cursor, text(query)) }
            .toModel(media)
    }

    override suspend fun ride(id: RideId): RideDetail {
        val uuid = uuidOrNotFound(id.value)
        return apiCall(dispatcher) { api.getRide(uuid) }.toModel(media)
    }

    override suspend fun mine(cursor: String?, limit: Int): Page<OwnRide> {
        val page = apiCall(dispatcher) { personal.listMyRides(limit, cursor) }
        return Page(page.items.map { it.toModel(media) }, page.nextCursor)
    }

    override suspend fun myUpcoming(): List<UpcomingRide> =
        apiCall(dispatcher) { personal.listMyUpcomingRides() }.items.map { it.toModel(media) }

    /** The API's own limit on `q`; a blank text is no search. */
    private fun text(query: String?): String? =
        query?.trim()?.take(MAX_QUERY)?.takeIf { it.isNotEmpty() }

    // A malformed id cannot name anything; the API would answer 404 as well.
    private fun uuidOrNotFound(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw DataError.NotFound()

    private companion object {
        const val MAX_QUERY = 150
    }
}
