package ru.colabike.core.model

import kotlinx.coroutines.flow.SharedFlow

/** Bikes as screens need them. Implementations throw [DataError] on failure. */
interface BikesRepository {
    suspend fun bikes(query: BikeQuery, cursor: String? = null, limit: Int = 24): Page<BikeSummary>

    suspend fun bike(id: BikeId): BikeDetail

    /**
     * Sets the like and returns the state the server ended with (idempotent: asking twice is the
     * same as once). Needs an account; one's own and private bikes cannot be liked.
     */
    suspend fun setLiked(id: BikeId, liked: Boolean): LikeState

    /** Emitted after every like that went through, so lists and details agree without a reload. */
    val likeChanges: SharedFlow<LikeChange>
}

/** The signed-in person. Implementations throw [DataError] on failure. */
interface AccountRepository {
    suspend fun me(): Account
}
