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
 * Deleting one's own account (cola #354). Implementations throw [DataError]; a proof the server
 * does not accept is [DataError.Rejected] with the code `invalid_credentials`.
 */
interface AccountDeletionRepository {
    suspend fun deletion(): AccountDeletion

    /**
     * Irreversible. On success the server has ended every session of the account, this one too: the
     * next call is signed out.
     */
    suspend fun delete(proof: DeletionProof)
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

/**
 * The feed of the signed-in person: what the people they follow and the bikes they watch published,
 * newest first. A guest has none (the API answers 401). Implementations throw [DataError].
 */
interface FeedRepository {
    suspend fun feed(filter: FeedFilter, cursor: String? = null, limit: Int = 24): Page<FeedItem>
}

/** Bike journals and the entries a person saved. Implementations throw [DataError] on failure. */
interface JournalRepository {
    /** The owner sees drafts too; everyone else only what is published and public. */
    suspend fun ofBike(bike: BikeId, cursor: String? = null, limit: Int = 24): Page<JournalSummary>

    suspend fun entry(id: JournalId): JournalEntry

    /** The signed-in person's saved entries, newest saves first. Hidden entries are not listed. */
    suspend fun saved(cursor: String? = null, limit: Int = 24): Page<JournalSummary>

    /**
     * Whether an entry is saved, as far as this session knows: from the saved list pages and from
     * earlier answers. The API has no such flag on an entry, so null means "not known".
     */
    fun isSaved(id: JournalId): Boolean?

    /** Idempotent; the answer is the state to show. Only public published entries can be saved. */
    suspend fun setSaved(id: JournalId, saved: Boolean): Boolean

    /** Emitted after every change that went through. */
    val savedChanges: SharedFlow<SavedChange>
}

/**
 * Rides and plans. The three public lists are kept apart on purpose (each is paged by its own
 * order); the personal upcoming list has no cursor. Implementations throw [DataError].
 */
interface RidesRepository {
    /**
     * Completed public rides, newest first; [query] looks in titles, descriptions, authors, bikes.
     */
    suspend fun completed(
        query: String? = null,
        cursor: String? = null,
        limit: Int = 24,
    ): Page<RideSummary>

    /** Public plans still ahead, nearest first; a weekly series is one card on its next date. */
    suspend fun upcoming(
        query: String? = null,
        cursor: String? = null,
        limit: Int = 24,
    ): Page<RideSummary>

    /** Completed public rides of one bike. */
    suspend fun ofBike(
        bike: BikeId,
        query: String? = null,
        cursor: String? = null,
        limit: Int = 24,
    ): Page<RideSummary>

    suspend fun ride(id: RideId): RideDetail

    /**
     * The charts' series of a ride's public track, or null if there is none (a ride without a track
     * or without a finished analysis: the server answers 404 for both). A separate request, to keep
     * the card and the lists light.
     */
    suspend fun analysis(id: RideId): RideAnalysis?

    /** The signed-in person's rides in any state, newest first. */
    suspend fun mine(cursor: String? = null, limit: Int = 24): Page<OwnRide>

    /** The signed-in person's plans and answers: at most 20, nearest first, no further pages. */
    suspend fun myUpcoming(): List<UpcomingRide>
}

interface NotificationsRepository {
    /**
     * The inbox, newest first, page by page, narrowed by [filter]. Reading it marks nothing: a
     * notification is read only when the person opens it or says so.
     */
    suspend fun page(
        cursor: String? = null,
        limit: Int = 24,
        filter: NotificationFilter = NotificationFilter(),
    ): NotificationPage

    /** The unread count: a separate number from the unread messages of the chat. */
    suspend fun count(): NotificationCount

    /**
     * Marks one notification read. Repeating it is fine (it marks none); not yours is not found.
     */
    suspend fun markRead(id: String): NotificationReadResult

    /** Marks up to 100 notifications read; ones that are not the person's are not counted. */
    suspend fun markRead(ids: List<String>): NotificationReadResult

    /**
     * Marks read everything visible up to [watermark] (one [category] if given). A server call
     * marks at most [READ_ALL_LIMIT]; the repository asks again while that many were marked.
     */
    suspend fun markAllRead(
        watermark: String,
        category: NotificationCategory? = null,
    ): NotificationReadResult

    companion object {
        /** The most the server marks in one "read all" request. */
        const val READ_ALL_LIMIT = 10_000
    }
}

/** The settings of the signed-in person's notifications, one object for the site and the app. */
interface NotificationSettingsRepository {
    /** The settings as the server has them now. */
    suspend fun settings(): NotificationSettings

    /**
     * Applies [change] and returns the settings as they are afterwards. A refusal (no connected
     * channel, a mail that is not verified, a quiet window without a time zone) is a
     * [DataError.Rejected] with the server's code; nothing is changed then.
     */
    suspend fun change(change: NotificationSettingsChange): NotificationSettings
}
