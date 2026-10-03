package ru.colabike.core.model

/** Bikes as screens need them. Implementations throw [DataError] on failure. */
interface BikesRepository {
    suspend fun bikes(scope: BikeScope, cursor: String? = null, limit: Int = 24): Page<BikeSummary>

    suspend fun bike(id: BikeId): BikeDetail
}

/** The signed-in person. Implementations throw [DataError] on failure. */
interface AccountRepository {
    suspend fun me(): Account
}
