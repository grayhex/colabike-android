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

    /**
     * Makes a bike of the signed-in person. [key] (a UUID) is the identity of the intention: the
     * same key after a lost answer is the same bike, never a second one. Publishing
     * ([BikeDraft.isPublic]) needs a confirmed address: [DataError.Rejected] with
     * `email_verification_required`.
     */
    suspend fun create(draft: BikeDraft, key: String): BikeDetail

    /**
     * Changes the named fields of one's own bike. [version] is the one that was read
     * ([BikeDetail.version]): a bike another device changed first is [DataError.Rejected] with
     * status 412, never an overwrite. An empty [patch] sends nothing.
     */
    suspend fun update(id: BikeId, patch: BikePatch, version: String?): BikeDetail

    /** Deletes one's own bike; one with rides is [DataError.Rejected] with status 409. */
    suspend fun delete(id: BikeId)

    /**
     * Adds a part to the build of one's own bike. [key] (a UUID) is the identity of the intention:
     * the same key after a lost answer is the same part. A part of a public bike needs a confirmed
     * address ([DataError.Rejected] with `email_verification_required`).
     */
    suspend fun addComponent(bike: BikeId, draft: ComponentDraft, key: String): BikeComponent

    /**
     * Changes the named fields of a part. [version] is optional: with it a part another device
     * changed first is [DataError.Rejected] with status 412; without it the change is applied.
     */
    suspend fun updateComponent(
        bike: BikeId,
        id: String,
        patch: ComponentPatch,
        version: String?,
    ): BikeComponent

    /** Removes a part; one that is already gone is removed all the same. */
    suspend fun removeComponent(bike: BikeId, id: String)

    /** The order in which the owner shows the groups of the build: all keys, replacing the old. */
    suspend fun setGroupOrder(bike: BikeId, groups: List<String>): BikeDetail

    /**
     * Sends one picture of one's own bike: the bytes of [file] as they are (the server checks them,
     * rotates, converts and sizes them), [onProgress] from 0 to 1 as they leave. [key] (a UUID) is
     * the identity of the file: the same key and the same bytes after a lost answer give the same
     * photo, never a second one. Cancelling the calling coroutine stops the transfer. The first
     * photo becomes the cover.
     */
    suspend fun uploadPhoto(
        bike: BikeId,
        file: java.io.File,
        key: String,
        onProgress: (Float) -> Unit = {},
    ): Photo

    /** Makes a photo the cover of the bike; the bike comes back with its photos in order. */
    suspend fun setCover(bike: BikeId, photoId: String): BikeDetail

    /** Removes a photo; one that is already gone is removed all the same. */
    suspend fun deletePhoto(bike: BikeId, photoId: String)

    /** Every bike saved or deleted through this repository, for lists and pages to agree. */
    val changes: SharedFlow<BikeChange>
}

/** The signed-in person. Implementations throw [DataError] on failure. */
interface AccountRepository {
    suspend fun me(): Account
}

/**
 * Reporting and blocking (cola #354). Implementations throw [DataError]. A report of the viewer's
 * own content or of something that is no longer there is [DataError.Rejected] /
 * [DataError.NotFound].
 */
interface SafetyRepository {
    /** True when the report is new; sending it again is not an error, only `false`. */
    suspend fun report(target: ReportTarget, reason: ReportReason): Boolean

    /** Idempotent; the answer is the state to show. One cannot block oneself. */
    suspend fun setBlocked(id: UserId, blocked: Boolean): Boolean

    /** The people the viewer has blocked, newest block first. */
    suspend fun blocked(cursor: String? = null, limit: Int = 24): Page<PersonSummary>

    /** Every block or unblock made through this repository, for the screens that show people. */
    val blockChanges: SharedFlow<BlockChange>
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
