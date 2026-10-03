package ru.colabike.core.model

import kotlinx.coroutines.flow.SharedFlow

/** Bikes as screens need them. Implementations throw [DataError] on failure. */
interface BikesRepository {
    suspend fun bikes(query: BikeQuery, cursor: String? = null, limit: Int = 24): Page<BikeSummary>

    suspend fun bike(id: BikeId): BikeDetail

    /** Public bikes by text and facets over builds; more than `/bikes` can filter. */
    suspend fun search(
        search: BikeSearch,
        cursor: String? = null,
        limit: Int = 24,
    ): Page<BikeSummary>

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

/**
 * People and their public bikes. [ref] is a person's UUID or current username; screens keep the
 * UUID. Implementations throw [DataError] on failure.
 */
interface PeopleRepository {
    suspend fun profile(ref: String): Profile

    /** Public bikes only, even for the owner: one's own, private included, are `scope=mine`. */
    suspend fun bikesOf(ref: String, cursor: String? = null, limit: Int = 24): Page<BikeSummary>

    suspend fun followers(ref: String, cursor: String? = null, limit: Int = 24): Page<PersonSummary>

    suspend fun following(ref: String, cursor: String? = null, limit: Int = 24): Page<PersonSummary>

    /** People whose name or username contains [text]; the text is required (it is a search). */
    suspend fun search(text: String, cursor: String? = null, limit: Int = 24): Page<PersonSummary>

    /**
     * Idempotent; the answer is the state to show. One cannot follow oneself or a blocked person.
     */
    suspend fun setFollowing(id: UserId, following: Boolean): FollowState

    /** Emitted after every subscription that went through. */
    val followChanges: SharedFlow<FollowChange>
}
