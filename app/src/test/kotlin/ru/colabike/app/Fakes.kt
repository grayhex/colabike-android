package ru.colabike.app

import android.content.Context
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.auth.YandexFailure
import ru.colabike.app.auth.YandexReauth
import ru.colabike.app.comments.InMemoryCommentDrafts
import ru.colabike.app.config.AppConfigSource
import ru.colabike.app.config.AppConfigState
import ru.colabike.app.links.PendingNavigation
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.messages.ChatGateway
import ru.colabike.app.messages.ChatScreens
import ru.colabike.app.messages.ChatSession
import ru.colabike.app.navigation.Destination
import ru.colabike.app.nearby.CoarseFix
import ru.colabike.app.nearby.CoarseLocation
import ru.colabike.app.nearby.CoarseResult
import ru.colabike.app.notifications.settings.DeviceNotifications
import ru.colabike.app.notifications.settings.DeviceNotificationsState
import ru.colabike.app.notifications.settings.OsPermission
import ru.colabike.app.push.PushAvailability
import ru.colabike.app.push.PushChannel
import ru.colabike.app.push.PushSync
import ru.colabike.app.push.VisibleConversation
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.rides.map.SketchRouteMaps
import ru.colabike.app.settings.AppSettings
import ru.colabike.app.settings.ThemeMode
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountChannels
import ru.colabike.core.model.AccountDeletion
import ru.colabike.core.model.AccountDeletionRepository
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.AgreementChange
import ru.colabike.core.model.AnalysisChannel
import ru.colabike.core.model.AnalysisPoint
import ru.colabike.core.model.AppConfig
import ru.colabike.core.model.AppNotice
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.BikeSearch
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.BlockChange
import ru.colabike.core.model.CategorySetting
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChannelFlag
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.ChatCredentials
import ru.colabike.core.model.ChatPeople
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.ChatUser
import ru.colabike.core.model.CircleMode
import ru.colabike.core.model.Comment
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.CommentThread
import ru.colabike.core.model.CommentThreads
import ru.colabike.core.model.CommentsRepository
import ru.colabike.core.model.Compatibility
import ru.colabike.core.model.ComponentFilters
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.ComponentModel
import ru.colabike.core.model.ComponentPhoto
import ru.colabike.core.model.ComponentQuery
import ru.colabike.core.model.ComponentsRepository
import ru.colabike.core.model.ConfigAssets
import ru.colabike.core.model.DataError
import ru.colabike.core.model.DeletionProof
import ru.colabike.core.model.Feature
import ru.colabike.core.model.FeatureAvailability
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.FeedRepository
import ru.colabike.core.model.FollowChange
import ru.colabike.core.model.FollowState
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.IntentDraft
import ru.colabike.core.model.IntentReadiness
import ru.colabike.core.model.IntentStatus
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.IntentWindow
import ru.colabike.core.model.IntentsRepository
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.LaunchConfig
import ru.colabike.core.model.LikeChange
import ru.colabike.core.model.LikeState
import ru.colabike.core.model.Listing
import ru.colabike.core.model.ListingBikeLink
import ru.colabike.core.model.ListingBrief
import ru.colabike.core.model.ListingCatalogLink
import ru.colabike.core.model.ListingCondition
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.ListingState
import ru.colabike.core.model.ListingStatus
import ru.colabike.core.model.MarketQuery
import ru.colabike.core.model.MarketRepository
import ru.colabike.core.model.MuteKind
import ru.colabike.core.model.NearbyArea
import ru.colabike.core.model.NearbyChange
import ru.colabike.core.model.NearbyGrid
import ru.colabike.core.model.NearbyLimits
import ru.colabike.core.model.NearbyOffers
import ru.colabike.core.model.NearbyOffersState
import ru.colabike.core.model.NearbyPreferences
import ru.colabike.core.model.NearbyRepository
import ru.colabike.core.model.NearbySettings
import ru.colabike.core.model.NearbySource
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationFilter
import ru.colabike.core.model.NotificationMute
import ru.colabike.core.model.NotificationPage
import ru.colabike.core.model.NotificationReadResult
import ru.colabike.core.model.NotificationReason
import ru.colabike.core.model.NotificationSettings
import ru.colabike.core.model.NotificationSettingsChange
import ru.colabike.core.model.NotificationSettingsRepository
import ru.colabike.core.model.NotificationTarget
import ru.colabike.core.model.NotificationsRepository
import ru.colabike.core.model.OnboardingConfig
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Page
import ru.colabike.core.model.ParticipationOutcome
import ru.colabike.core.model.ParticipationRepository
import ru.colabike.core.model.ParticipationResponse
import ru.colabike.core.model.ParticipationState
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.Person
import ru.colabike.core.model.PersonSummary
import ru.colabike.core.model.Photo
import ru.colabike.core.model.PhotoSource
import ru.colabike.core.model.Profile
import ru.colabike.core.model.ProfileCounts
import ru.colabike.core.model.QuietHours
import ru.colabike.core.model.Range
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.ReportReason
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.RequestedDate
import ru.colabike.core.model.RequestedDateStatus
import ru.colabike.core.model.RideAgreement
import ru.colabike.core.model.RideAnalysis
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideIntent
import ru.colabike.core.model.RideParticipation
import ru.colabike.core.model.RidePassport
import ru.colabike.core.model.RideRecurrence
import ru.colabike.core.model.RideRole
import ru.colabike.core.model.RideRoute
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RideSummary
import ru.colabike.core.model.RidesRepository
import ru.colabike.core.model.SafetyRepository
import ru.colabike.core.model.SavedChange
import ru.colabike.core.model.SavedListingChange
import ru.colabike.core.model.SellerListings
import ru.colabike.core.model.ServiceLinks
import ru.colabike.core.model.SessionKind
import ru.colabike.core.model.SessionPlatform
import ru.colabike.core.model.UpcomingRide
import ru.colabike.core.model.UserId
import ru.colabike.core.model.ViewerRole

val account =
    Account(
        UserId("u1"),
        "test-rider",
        "Тестовый Райдер",
        null,
        "Катаюсь круглый год.",
        "Москва",
        emailVerified = false,
    )

val thisDevice =
    AccountSession(
        id = "7c1d2e3f-4a5b-4c6d-8e7f-a1b2c3d4e5f6",
        kind = SessionKind.Device,
        deviceName = "Google Pixel 9",
        platform = SessionPlatform.Android,
        appVersion = "0.2.0",
        userAgent = "ColaBike-Android/0.2.0",
        createdAt = Instant.parse("2026-10-01T08:30:00Z"),
        lastSeenAt = Instant.parse("2026-10-03T19:10:00Z"),
        isCurrent = true,
    )

val browser =
    AccountSession(
        id = "0a9b8c7d-6e5f-4a3b-9c2d-1e0f9a8b7c6d",
        kind = SessionKind.Browser,
        deviceName = null,
        platform = SessionPlatform.Unknown,
        appVersion = null,
        userAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36",
        createdAt = Instant.parse("2026-09-20T10:00:00Z"),
        lastSeenAt = Instant.parse("2026-10-02T21:45:00Z"),
        isCurrent = false,
    )

fun bikes(from: Int, count: Int): List<BikeSummary> =
    (from until from + count).map {
        PreviewData.bike.copy(id = BikeId("b$it"), name = "Велосипед $it", liked = it % 2 == 0)
    }

/** Pages keyed by cursor (null = first); a queued error is thrown by the next call. */
class FakeBikes(
    var pages: Map<String?, Page<BikeSummary>> = mapOf(null to Page(bikes(0, 3), null))
) : BikesRepository {
    val calls = mutableListOf<Pair<BikeQuery, String?>>()
    var nextError: DataError? = null

    /** Answers every page request by its own rule (a search, a held answer), instead of [pages]. */
    var answer: (suspend (BikeQuery, String?) -> Page<BikeSummary>)? = null

    /** How many times a bike's details were requested: a ViewModel that survived asks once. */
    var detailCalls = 0

    /** What the server says to the next like; thrown if it is an error. */
    var likeError: DataError? = null
    val likes = mutableListOf<Pair<BikeId, Boolean>>()
    private val changes = MutableSharedFlow<LikeChange>(extraBufferCapacity = 16)
    override val likeChanges: SharedFlow<LikeChange> = changes

    /** Details by id; a bike without an entry gets a plain page made from its summary. */
    var details: Map<String, BikeDetail> = emptyMap()

    override suspend fun bikes(query: BikeQuery, cursor: String?, limit: Int): Page<BikeSummary> {
        calls += query to cursor
        nextError?.let {
            nextError = null
            throw it
        }
        answer?.let {
            return it(query, cursor)
        }
        return pages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun bike(id: BikeId): BikeDetail {
        detailCalls++
        nextError?.let {
            nextError = null
            throw it
        }
        details[id.value]?.let {
            return it
        }
        val summary =
            pages.values.flatMap { it.items }.firstOrNull { it.id == id }
                ?: throw DataError.NotFound()
        return PreviewData.bikeDetail.copy(summary = summary)
    }

    val searches = mutableListOf<Pair<BikeSearch, String?>>()

    /** What a search finds; by default the first page of [pages]. */
    var searchAnswer: (suspend (BikeSearch, String?) -> Page<BikeSummary>)? = null

    override suspend fun search(
        search: BikeSearch,
        cursor: String?,
        limit: Int,
    ): Page<BikeSummary> {
        searches += search to cursor
        nextError?.let {
            nextError = null
            throw it
        }
        searchAnswer?.let {
            return it(search, cursor)
        }
        return pages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun setLiked(id: BikeId, liked: Boolean): LikeState {
        likes += id to liked
        likeError?.let {
            likeError = null
            throw it
        }
        val current = pages.values.flatMap { it.items }.firstOrNull { it.id == id }?.likes ?: 0
        return LikeState(liked, (current + if (liked) 1 else -1).coerceAtLeast(0)).also {
            changes.tryEmit(LikeChange(id, it))
        }
    }
}

val rider = Person(UserId("u-rider"), "test-rider", "Тестовый Райдер", null)

fun profileOf(
    person: Person = rider,
    relationship: Relationship? =
        Relationship(isSelf = false, following = false, followedBy = false, friends = false),
    followers: Int = 12,
) =
    Profile(
        person = person,
        bio = "Катаюсь круглый год.",
        location = "Москва",
        joined = Instant.parse("2026-09-01T10:00:00Z"),
        counts = ProfileCounts(bikes = 2, followers = followers, following = 7),
        relationship = relationship,
    )

fun people(from: Int, count: Int): List<PersonSummary> =
    (from until from + count).map {
        PersonSummary(Person(UserId("u$it"), "rider-$it", "Райдер $it", null), null)
    }

/** People by ref (UUID or username); a ref without an entry is not found. */
class FakePeople(
    var profiles: Map<String, Profile> =
        mapOf(
            rider.id.value to profileOf(),
            rider.username to profileOf(),
            PreviewData.rider.id.value to profileOf(PreviewData.rider),
        ),
    var bikesOfPerson: Page<BikeSummary> = Page(bikes(0, 2), null),
    var followersPage: Page<PersonSummary> = Page(people(0, 3), null),
    var followingPage: Page<PersonSummary> = Page(people(10, 2), null),
) : PeopleRepository {
    val profileCalls = mutableListOf<String>()
    val follows = mutableListOf<Pair<UserId, Boolean>>()
    val searches = mutableListOf<Pair<String, String?>>()
    var nextError: DataError? = null
    var followError: DataError? = null
    var searchAnswer: (suspend (String, String?) -> Page<PersonSummary>)? = null
    private val changes = MutableSharedFlow<FollowChange>(extraBufferCapacity = 16)
    override val followChanges: SharedFlow<FollowChange> = changes

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    override suspend fun profile(ref: String): Profile {
        profileCalls += ref
        fail()
        return profiles[ref] ?: throw DataError.NotFound()
    }

    override suspend fun bikesOf(ref: String, cursor: String?, limit: Int): Page<BikeSummary> {
        fail()
        return bikesOfPerson
    }

    override suspend fun followers(ref: String, cursor: String?, limit: Int): Page<PersonSummary> {
        fail()
        return followersPage
    }

    override suspend fun following(ref: String, cursor: String?, limit: Int): Page<PersonSummary> {
        fail()
        return followingPage
    }

    override suspend fun search(text: String, cursor: String?, limit: Int): Page<PersonSummary> {
        searches += text to cursor
        fail()
        return searchAnswer?.invoke(text, cursor) ?: Page(emptyList(), null)
    }

    override suspend fun setFollowing(id: UserId, following: Boolean): FollowState {
        follows += id to following
        followError?.let {
            followError = null
            throw it
        }
        val current = profiles.values.firstOrNull { it.person.id == id }
        val followers = (current?.counts?.followers ?: 0) + if (following) 1 else -1
        return FollowState(
                Relationship(
                    isSelf = false,
                    following = following,
                    followedBy = false,
                    friends = false,
                ),
                followers.coerceAtLeast(0),
            )
            .also { changes.tryEmit(FollowChange(id, it)) }
    }
}

/** A journal entry the way a list shows it; `n` makes the title and the id. */
fun journalSummary(n: Int, bike: BikeRef = BikeRef(BikeId("b1"), "Городской Трэвел")) =
    PreviewData.journal.copy(
        id = JournalId("j$n"),
        title = "Запись $n",
        bike = bike,
        excerpt = "Начало записи $n.",
    )

fun journals(from: Int, count: Int): List<JournalSummary> =
    (from until from + count).map { journalSummary(it) }

fun journalEntry(
    n: Int,
    body: String =
        "## Что сделал\n\nЗаменил **цепь** и кассету, смазал _всё_ остальное. Подробности: " +
            "[инструкция](https://example.test/chain).\n\n- цепь KMC\n- кассета Deore\n\n" +
            "> Цепь меняют вместе с кассетой.",
    components: List<BikeComponent> =
        listOf(
            BikeComponent(
                "jc1",
                "build",
                "Трансмиссия",
                "KMC X10",
                "114 звеньев",
                priceRub = 1800.0,
            )
        ),
) =
    JournalEntry(
        summary = journalSummary(n).copy(excerpt = ""),
        body = body,
        components = components,
        photos = emptyList(),
    )

/** Feed pages by cursor (null = first); a queued error is thrown by the next call. */
class FakeFeed(var pages: Map<String?, Page<FeedItem>> = mapOf(null to Page(emptyList(), null))) :
    FeedRepository {
    val calls = mutableListOf<Pair<FeedFilter, String?>>()
    var nextError: DataError? = null
    var answer: (suspend (FeedFilter, String?) -> Page<FeedItem>)? = null

    override suspend fun feed(filter: FeedFilter, cursor: String?, limit: Int): Page<FeedItem> {
        calls += filter to cursor
        nextError?.let {
            nextError = null
            throw it
        }
        answer?.let {
            return it(filter, cursor)
        }
        return pages[cursor] ?: Page(emptyList(), null)
    }
}

fun feedBike(n: Int) =
    FeedItem.Bike(bikes(n, 1).single(), Instant.parse("2026-09-21T09:00:00Z").minusSeconds(n * 60L))

fun feedJournal(n: Int) =
    FeedItem.Journal(journalSummary(n), Instant.parse("2026-09-21T09:00:00Z").minusSeconds(n * 60L))

/** The journals of bikes, the entries and the saved list, with the saved state the API lacks. */
class FakeJournal(
    var pages: Map<String?, Page<JournalSummary>> = mapOf(null to Page(journals(0, 3), null)),
    var savedPages: Map<String?, Page<JournalSummary>> = mapOf(null to Page(emptyList(), null)),
    var entries: Map<String, JournalEntry> = (0..9).associate { "j$it" to journalEntry(it) },
) : JournalRepository {
    val bikeCalls = mutableListOf<Pair<BikeId, String?>>()
    val savedCalls = mutableListOf<String?>()
    val saves = mutableListOf<Pair<JournalId, Boolean>>()
    var entryCalls = 0
    var nextError: DataError? = null
    var saveError: DataError? = null
    private val known = mutableMapOf<String, Boolean>()
    private val changes = MutableSharedFlow<SavedChange>(extraBufferCapacity = 16)
    override val savedChanges: SharedFlow<SavedChange> = changes

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    fun know(id: String, saved: Boolean) {
        known[id] = saved
    }

    override suspend fun ofBike(bike: BikeId, cursor: String?, limit: Int): Page<JournalSummary> {
        bikeCalls += bike to cursor
        fail()
        return pages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun entry(id: JournalId): JournalEntry {
        entryCalls++
        fail()
        return entries[id.value] ?: throw DataError.NotFound()
    }

    override suspend fun saved(cursor: String?, limit: Int): Page<JournalSummary> {
        savedCalls += cursor
        fail()
        return (savedPages[cursor] ?: Page(emptyList(), null)).also { page ->
            page.items.forEach { known[it.id.value] = true }
        }
    }

    override fun isSaved(id: JournalId): Boolean? = known[id.value]

    override suspend fun setSaved(id: JournalId, saved: Boolean): Boolean {
        saves += id to saved
        saveError?.let {
            saveError = null
            throw it
        }
        known[id.value] = saved
        changes.tryEmit(SavedChange(id, saved))
        return saved
    }
}

/** A person who is not the signed-in one (`test-rider`, id `u1`). */
val neighbour = Person(UserId("u2"), "neighbour", "Сосед", null)

fun commentOf(
    id: String,
    body: String? = "Комментарий $id",
    author: Person? = neighbour,
    parentId: String? = null,
    replyCount: Int = 0,
    deleted: Boolean = false,
    editedAt: Instant? = null,
) =
    Comment(
        id = id,
        parentId = parentId,
        author = if (deleted) null else author,
        body = if (deleted) null else body,
        createdAt = Instant.parse("2026-09-20T10:00:00Z"),
        editedAt = editedAt,
        deleted = deleted,
        replyCount = replyCount,
    )

/** The discussion of any object in memory, with the idempotency the server promises. */
class FakeComments(
    var threads: List<CommentThread> = emptyList(),
    /** The rest of the replies of a root, by root id (what `/replies` gives). */
    var moreReplies: Map<String, List<Comment>> = emptyMap(),
    var nextPage: Map<String?, CommentThreads> = emptyMap(),
    private val me: Person = PreviewData.rider,
) : CommentsRepository {
    data class Posted(
        val target: CommentTarget,
        val body: String,
        val parentId: String?,
        val key: String,
    )

    val threadCalls = mutableListOf<Triple<CommentTarget, String?, String?>>()
    val replyCalls = mutableListOf<Pair<String, String?>>()
    val posted = mutableListOf<Posted>()
    val edits = mutableListOf<Pair<String, String>>()
    val deletes = mutableListOf<String>()
    var nextError: DataError? = null
    var postError: DataError? = null
    var editError: DataError? = null
    var deleteError: DataError? = null

    /** The next post is made on the server and the answer is lost on the way. */
    var loseNextAnswer = false
    private val created = mutableMapOf<String, Comment>()
    private val changes = MutableSharedFlow<CommentCountChange>(extraBufferCapacity = 16)
    override val countChanges: SharedFlow<CommentCountChange> = changes

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    override suspend fun threads(
        target: CommentTarget,
        cursor: String?,
        limit: Int,
        focus: String?,
    ): CommentThreads {
        threadCalls += Triple(target, cursor, focus)
        fail()
        if (focus != null) {
            val thread =
                threads.firstOrNull { t ->
                    t.root.id == focus || t.replies.any { it.id == focus }
                }
                    ?: created[focus]?.let { CommentThread(it, emptyList()) }
                    ?: throw DataError.NotFound()
            return CommentThreads(
                listOf(thread),
                null,
                listOf(thread.root) + thread.replies.filter { it.id == focus },
            )
        }
        return nextPage[cursor]
            ?: CommentThreads(if (cursor == null) threads else emptyList(), null, emptyList())
    }

    override suspend fun replies(
        target: CommentTarget,
        commentId: String,
        cursor: String?,
        limit: Int,
    ): Page<Comment> {
        replyCalls += commentId to cursor
        fail()
        return Page(moreReplies[commentId].orEmpty(), null)
    }

    override suspend fun post(
        target: CommentTarget,
        body: String,
        parentId: String?,
        key: String,
    ): Comment {
        posted += Posted(target, body, parentId, key)
        // The same key again is the same comment, not a second one.
        created[key]?.let {
            return it
        }
        postError?.let {
            postError = null
            throw it
        }
        val comment =
            commentOf("new-${created.size + 1}", body.trim(), me, parentId).also {
                created[key] = it
            }
        changes.tryEmit(CommentCountChange(target, +1))
        if (loseNextAnswer) {
            loseNextAnswer = false
            throw DataError.Offline(java.io.IOException("answer lost"))
        }
        return comment
    }

    override suspend fun edit(target: CommentTarget, commentId: String, body: String): Comment {
        edits += commentId to body
        editError?.let {
            editError = null
            throw it
        }
        val old =
            (threads.map { it.root } + threads.flatMap { it.replies }).firstOrNull {
                it.id == commentId
            } ?: created.values.firstOrNull { it.id == commentId }
        return (old ?: commentOf(commentId, author = me)).copy(
            body = body.trim(),
            author = me,
            editedAt = Instant.parse("2026-09-21T10:00:00Z"),
        )
    }

    override suspend fun delete(target: CommentTarget, commentId: String) {
        deletes += commentId
        deleteError?.let {
            deleteError = null
            throw it
        }
        changes.tryEmit(CommentCountChange(target, -1))
    }
}

/** Roots with replies, a tombstone with an answer under it, own comments; the rest of `r1`. */
fun sampleDiscussion(): FakeComments {
    val me = PreviewData.rider
    return FakeComments(
        threads =
            listOf(
                CommentThread(
                    commentOf("r1", body = "Красивая рама", replyCount = 3),
                    listOf(
                        commentOf("x1", body = "Согласен", parentId = "r1"),
                        commentOf("x2", body = "Мой ответ", parentId = "r1", author = me),
                    ),
                ),
                CommentThread(
                    commentOf("r2", body = "Мой вопрос о раме", author = me),
                    emptyList(),
                ),
                CommentThread(
                    commentOf("t1", deleted = true, replyCount = 1),
                    listOf(commentOf("y1", body = "Ответ под удалённым", parentId = "t1")),
                ),
            ),
        moreReplies =
            mapOf(
                "r1" to
                    listOf(
                        commentOf("x1", body = "Согласен", parentId = "r1"),
                        commentOf("x2", body = "Мой ответ", parentId = "r1", author = me),
                        commentOf("x3", body = "Третий ответ", parentId = "r1"),
                    )
            ),
    )
}

/** A ride the way a list shows it; `n` makes the id and the title. */
fun rideSummary(
    n: Int,
    status: RideStatus = RideStatus.Completed,
    time: Instant? = Instant.parse("2026-09-20T16:00:00Z"),
) =
    PreviewData.ride.copy(
        id = RideId("ride-$n"),
        title = "Покатушка $n",
        status = status,
        time = time,
    )

fun rides(from: Int, count: Int): List<RideSummary> =
    (from until from + count).map { rideSummary(it) }

fun plan(n: Int, role: RideRole = RideRole.Accepted, changed: Boolean = false) =
    UpcomingRide(
        ride =
            PreviewData.plannedRide.copy(
                id = RideId("plan-$n"),
                title = "План $n",
                time = Instant.parse("2026-10-11T07:00:00Z"),
            ),
        role = role,
        occurrenceCancelled = false,
        changedAfterAnswer = changed,
        meetingPoint = null,
        meetingHidden = false,
    )

fun rideDetail(n: Int, planned: Boolean = false) =
    RideDetail(
        summary =
            if (planned) PreviewData.plannedRide.copy(id = RideId("plan-$n"), title = "План $n")
            else rideSummary(n),
        description = "Спокойно, без гонки.",
        features = listOf("Кофе-пауза"),
        meetingPoint = if (planned) null else null,
        meetingHidden = planned,
        expectedEndAt = if (planned) Instant.parse("2026-10-11T11:00:00Z") else null,
        recruitmentClosed = false,
        passport =
            if (planned)
                RidePassport(
                    areaLabel = "Измайловский парк",
                    pace = "calm",
                    distanceKm = Range(25.0, 40.0),
                    durationMinutes = Range(120.0, 180.0),
                    groupSize = Range(3.0, 12.0),
                    beginnerFriendly = true,
                )
            else null,
        extraMetrics = if (planned) emptyMap() else mapOf("avgHeartRate" to 142.0),
        route = if (planned) null else sampleRoute,
    )

/** A loop through a park with a privacy cut in the middle: two lines, never joined. */
val sampleRoute =
    RideRoute(
        listOf(
            (0..11).map { i ->
                val a = i / 11.0 * Math.PI
                GeoPoint(55.7600 + 0.010 * Math.sin(a), 37.6100 + 0.020 * (1 - Math.cos(a)) / 2)
            },
            (0..9).map { i ->
                val a = Math.PI + i / 9.0 * Math.PI
                GeoPoint(55.7600 + 0.010 * Math.sin(a), 37.6100 + 0.020 * (1 - Math.cos(a)) / 2)
            },
        )
    )

/** A ride's charts: two continuous parts, heart rate with a gap, no cadence or power. */
fun sampleAnalysis(): RideAnalysis {
    fun point(i: Int, gaps: Int = 0, heart: Boolean = true) =
        AnalysisPoint(
            position = sampleRoute.lines[0][i.coerceAtMost(11)],
            distanceM = i * 1_500.0,
            elapsedS = i * 300.0,
            values =
                buildMap {
                    put(AnalysisChannel.Elevation, 120.0 + 18 * Math.sin(i / 3.0) + i)
                    put(AnalysisChannel.Speed, 5.0 + Math.cos(i / 2.0))
                    put(AnalysisChannel.Grade, 2.0 * Math.cos(i / 3.0))
                    if (heart) put(AnalysisChannel.HeartRate, 125.0 + 3 * i)
                },
            gaps = gaps,
        )
    return RideAnalysis(
        pointCount = 20,
        downsampled = true,
        segments =
            listOf(
                (0..11).map { point(it, heart = it !in 5..6, gaps = if (it == 7) 8 else 0) },
                (12..19).map { point(it) },
            ),
    )
}

/** Rides in memory: public lists by cursor, the viewer's own, and the details by id. */
class FakeRides(
    var upcomingPages: Map<String?, Page<RideSummary>> =
        mapOf(null to Page(listOf(PreviewData.plannedRide), null)),
    var completedPages: Map<String?, Page<RideSummary>> = mapOf(null to Page(rides(0, 3), null)),
    var bikePages: Map<String?, Page<RideSummary>> = mapOf(null to Page(rides(10, 2), null)),
    var minePages: Map<String?, Page<OwnRide>> = mapOf(null to Page(emptyList(), null)),
    var myPlans: List<UpcomingRide> = emptyList(),
    /** The analysis by ride id; a ride that is not here has none (the server answers 404). */
    var analyses: Map<String, RideAnalysis> = mapOf("ride-0" to sampleAnalysis()),
    var details: Map<String, RideDetail> =
        (0..11).associate { "ride-$it" to rideDetail(it) } +
            (0..9).associate { "plan-$it" to rideDetail(it, planned = true) } +
            ("r2" to rideDetail(2, planned = true).copy(summary = PreviewData.plannedRide)) +
            ("r1" to rideDetail(1).copy(summary = PreviewData.ride)),
) : RidesRepository {
    val upcomingCalls = mutableListOf<Pair<String?, String?>>()
    val completedCalls = mutableListOf<Pair<String?, String?>>()
    val bikeCalls = mutableListOf<Triple<BikeId, String?, String?>>()
    val mineCalls = mutableListOf<String?>()
    var myUpcomingCalls = 0
    val analysisCalls = mutableListOf<String>()

    /** The next request for series fails with no network; the one after it succeeds. */
    var failAnalysisOnce = false
    var rideCalls = 0
    var nextError: DataError? = null
    var answer: (suspend (String?, String?) -> Page<RideSummary>)? = null

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    override suspend fun completed(query: String?, cursor: String?, limit: Int): Page<RideSummary> {
        completedCalls += query to cursor
        fail()
        answer?.let {
            return it(query, cursor)
        }
        return completedPages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun upcoming(query: String?, cursor: String?, limit: Int): Page<RideSummary> {
        upcomingCalls += query to cursor
        fail()
        return upcomingPages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun ofBike(
        bike: BikeId,
        query: String?,
        cursor: String?,
        limit: Int,
    ): Page<RideSummary> {
        bikeCalls += Triple(bike, query, cursor)
        fail()
        return bikePages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun ride(id: RideId): RideDetail {
        rideCalls++
        fail()
        return details[id.value] ?: throw DataError.NotFound()
    }

    override suspend fun analysis(id: RideId): RideAnalysis? {
        analysisCalls += id.value
        if (failAnalysisOnce) {
            failAnalysisOnce = false
            throw DataError.Offline(java.io.IOException())
        }
        fail()
        return analyses[id.value]
    }

    override suspend fun mine(cursor: String?, limit: Int): Page<OwnRide> {
        mineCalls += cursor
        fail()
        return minePages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun myUpcoming(): List<UpcomingRide> {
        myUpcomingCalls++
        fail()
        return myPlans
    }
}

/** The inbox in memory: pages by cursor, and a count the test sets. */
class FakeNotifications(
    var pages: Map<String?, Page<AppNotification>> = mapOf(null to Page(notifications(0, 3), null)),
    var unread: NotificationCount = NotificationCount(unread = 2, capped = false),
    var watermark: String? = "mark-1",
) : NotificationsRepository {
    val pageCalls = mutableListOf<String?>()
    val filters = mutableListOf<NotificationFilter>()
    var countCalls = 0
    var nextError: DataError? = null

    /** Every mark asked for, in order: ("one", ids), ("all", watermark + category). */
    val marks = mutableListOf<Pair<String, List<String>>>()

    /** What the server says is left after a mark. */
    var unreadAfterMark: NotificationCount? = null
    private val read = mutableSetOf<String>()

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    override suspend fun page(
        cursor: String?,
        limit: Int,
        filter: NotificationFilter,
    ): NotificationPage {
        pageCalls += cursor
        filters += filter
        fail()
        val page = pages[cursor] ?: Page(emptyList(), null)
        val items =
            page.items
                .map { if (it.id in read) it.copy(read = true) else it }
                .filter { filter.category == null || it.category == filter.category }
                .filter { !filter.unreadOnly || !it.read }
        return NotificationPage(items, page.nextCursor, watermark)
    }

    override suspend fun count(): NotificationCount {
        countCalls++
        fail()
        return unread
    }

    private fun afterMark(marked: Int): NotificationReadResult {
        val left =
            unreadAfterMark ?: unread.copy(unread = (unread.unread - marked).coerceAtLeast(0))
        unread = left
        return NotificationReadResult(marked, left)
    }

    override suspend fun markRead(id: String): NotificationReadResult {
        marks += "one" to listOf(id)
        fail()
        return afterMark(if (read.add(id)) 1 else 0)
    }

    override suspend fun markRead(ids: List<String>): NotificationReadResult {
        marks += "selection" to ids
        fail()
        return afterMark(ids.count { read.add(it) })
    }

    override suspend fun markAllRead(
        watermark: String,
        category: NotificationCategory?,
    ): NotificationReadResult {
        marks += "all" to listOfNotNull(watermark, category?.key)
        fail()
        val ids =
            pages.values
                .flatMap { it.items }
                .filter { !it.read && (category == null || it.category == category) }
                .map { it.id }
        return afterMark(ids.count { read.add(it) })
    }
}

fun notification(
    n: Int,
    kind: String = "comment",
    type: String = "bike",
    read: Boolean = false,
    path: String = "/b/6e7f8091-a2b3-4c4d-9e5f-60718293a4b5",
    actor: Person? = PreviewData.rider,
    category: NotificationCategory = categoryOf(kind),
    reasons: Set<NotificationReason> = emptySet(),
) =
    AppNotification(
        id = "n$n",
        kind = kind,
        category = category,
        createdAt = Instant.parse("2026-10-03T18:30:00Z"),
        read = read,
        actor = actor,
        target =
            NotificationTarget(
                type = type,
                id = "6e7f8091-a2b3-4c4d-9e5f-60718293a4b5",
                name = "Городской Трэвел",
                path = path,
            ),
        reasons = reasons,
    )

/** The category the server files a kind under. */
fun categoryOf(kind: String): NotificationCategory =
    when {
        kind.startsWith("ride_") &&
            kind != "ride_like" &&
            !kind.endsWith("_comment") &&
            !kind.endsWith("_reply") -> NotificationCategory.Rides
        kind == "comment" ||
            kind == "reply" ||
            kind.endsWith("_comment") ||
            kind.endsWith("_reply") -> NotificationCategory.Discussions
        kind == "follow" || kind == "like" || kind.endsWith("_like") ->
            NotificationCategory.Reactions
        kind == "market_expiring" -> NotificationCategory.Market
        kind == "plan_published" -> NotificationCategory.Plans
        kind == "intent_published" -> NotificationCategory.Intents
        kind == "plan_nearby" -> NotificationCategory.Nearby
        kind == "session_reuse" || kind == "bike_week" -> NotificationCategory.Site
        else -> NotificationCategory.Other
    }

/** One of each look the inbox has: a person's, the site's own, a listing, and a kind to come. */
fun sampleInbox(): List<AppNotification> =
    listOf(
        notification(1, kind = "comment", read = false),
        notification(2, kind = "follow", type = "profile", read = false, path = "/@test-rider"),
        notification(3, kind = "ride_like", type = "ride", read = true, path = "/r/x"),
        notification(4, kind = "reply", read = true),
        notification(5, kind = "session_reuse", type = "account", read = false, actor = null).let {
            it.copy(target = it.target.copy(name = ""))
        },
        notification(6, kind = "market_expiring", type = "market", read = false, actor = null).let {
            it.copy(
                target =
                    it.target.copy(
                        name = "Втулка Shimano Deore",
                        expiresAt = Instant.parse("2026-10-09T09:00:00Z"),
                        state = ListingState.Expiring,
                    )
            )
        },
        notification(7, kind = "something_new", type = "galaxy", read = true, actor = null),
    )

fun notifications(from: Int, count: Int): List<AppNotification> =
    (from until from + count).map { notification(it, read = it % 2 == 1) }

class FakeAccount(var result: () -> Account = { account }) : AccountRepository {
    /** How many times the account was asked for: a guest asks nothing. */
    var calls = 0

    override suspend fun me(): Account {
        calls++
        return result()
    }
}

class FakeSafety(var blockedPeople: List<PersonSummary> = emptyList()) : SafetyRepository {
    val reports = mutableListOf<Pair<ReportTarget, ReportReason>>()
    val blocks = mutableListOf<Pair<UserId, Boolean>>()
    var nextError: DataError? = null

    /** Fails the next read of the list only, not the block or report made before it. */
    var listError: DataError? = null
    var created = true
    private val changes = MutableSharedFlow<BlockChange>(extraBufferCapacity = 8)
    override val blockChanges: SharedFlow<BlockChange> = changes

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    override suspend fun report(target: ReportTarget, reason: ReportReason): Boolean {
        fail()
        reports += target to reason
        return created
    }

    override suspend fun setBlocked(id: UserId, blocked: Boolean): Boolean {
        fail()
        blocks += id to blocked
        changes.tryEmit(BlockChange(id, blocked))
        return blocked
    }

    override suspend fun blocked(cursor: String?, limit: Int): Page<PersonSummary> {
        fail()
        listError?.let {
            listError = null
            throw it
        }
        return Page(
            blockedPeople.filterNot { p -> blocks.any { it.first == p.person.id && !it.second } },
            null,
        )
    }
}

class FakeAccountDeletion(
    var terms: AccountDeletion = AccountDeletion(AccountDeletion.Method.Password, true, null)
) : AccountDeletionRepository {
    var nextError: DataError? = null
    val proofs = mutableListOf<DeletionProof>()
    var termsCalls = 0

    override suspend fun deletion(): AccountDeletion {
        termsCalls++
        nextError?.let {
            nextError = null
            throw it
        }
        return terms
    }

    override suspend fun delete(proof: DeletionProof) {
        proofs += proof
        nextError?.let {
            nextError = null
            throw it
        }
    }
}

class FakeAuth(
    initial: AuthState = AuthState.SignedIn(account),
    override val yandexEnabled: Boolean = true,
) : AuthActions {
    override val state = MutableStateFlow(initial)
    val failures = MutableSharedFlow<YandexFailure>(extraBufferCapacity = 1)
    override val yandexFailures: Flow<YandexFailure> = failures
    var signInError: DataError? = null
    val signIns = mutableListOf<Pair<String, String>>()
    var signOuts = 0

    override suspend fun signIn(email: String, password: String) {
        signIns += email to password
        signInError?.let { throw it }
        state.value = AuthState.SignedIn(account)
    }

    override fun startYandex(context: Context) = Unit

    var reauthStarts = 0
    val reauths = MutableSharedFlow<YandexReauth>(extraBufferCapacity = 4)
    override val yandexReauth: Flow<YandexReauth> = reauths

    override fun startYandexReauth(context: Context) {
        reauthStarts++
    }

    override suspend fun signOut() {
        signOuts++
        state.value = AuthState.SignedOut
    }

    var deletedAccounts = 0

    override suspend fun accountDeleted() {
        deletedAccounts++
        state.value = AuthState.SignedOut
    }
}

class FakeSessions(var list: List<AccountSession> = listOf(thisDevice, browser)) :
    AccountSessionsRepository {
    val revoked = mutableListOf<String>()
    var nextError: DataError? = null

    override suspend fun sessions(): List<AccountSession> {
        nextError?.let {
            nextError = null
            throw it
        }
        return list
    }

    override suspend fun revoke(id: String) {
        nextError?.let {
            nextError = null
            throw it
        }
        revoked += id
        list = list.filterNot { it.id == id }
    }
}

class FakeSettings(theme: ThemeMode = ThemeMode.System, guest: Boolean = false) : AppSettings {
    override val themeMode = MutableStateFlow(theme)
    override val browsingAsGuest = MutableStateFlow(guest)

    override fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override fun setBrowsingAsGuest(value: Boolean) {
        browsingAsGuest.value = value
    }

    override val onboardingSeen = MutableStateFlow<Int?>(null)
    override val noticeClosed = MutableStateFlow<Int?>(null)
    override val updateOfferClosed = MutableStateFlow<Int?>(null)

    override fun setOnboardingSeen(revision: Int) {
        onboardingSeen.value = revision
    }

    override fun setNoticeClosed(revision: Int) {
        noticeClosed.value = revision
    }

    override fun setUpdateOfferClosed(versionCode: Int) {
        updateOfferClosed.value = versionCode
    }
}

class FakePending : PendingNavigation {
    override val destination = MutableStateFlow<Destination?>(null)

    override fun offer(destination: Destination) {
        this.destination.value = destination
    }

    override fun clear() {
        destination.value = null
    }
}

/** The chat's bridge in memory: the people to write to, what was opened, queued failures. */
class FakeChat(
    var people: ChatPeople = ChatPeople(people(0, 3).map { it.person }, searching = false),
    var unread: Int = 0,
) : ChatRepository {
    data class Opened(
        val kind: ChatChannelKind,
        val members: List<UserId>,
        val name: String?,
        val key: String?,
    )

    var credentialCalls = 0
    val opened = mutableListOf<Opened>()
    val queries = mutableListOf<String?>()
    var credentialsError: DataError? = null
    var peopleError: DataError? = null
    var openError: DataError? = null
    var peopleAnswer: (suspend (String?) -> ChatPeople)? = null
    var cid = ChannelCid("messaging:dm-1")

    override suspend fun credentials(): ChatCredentials {
        credentialCalls++
        credentialsError?.let {
            credentialsError = null
            throw it
        }
        return ChatCredentials(
            apiKey = "key",
            user = ChatUser("u1", "Тестовый Райдер", null),
            token = "token-$credentialCalls",
            expiresAt = Instant.parse("2026-10-03T20:05:00Z"),
            channelType = "colabike",
        )
    }

    override suspend fun open(
        kind: ChatChannelKind,
        members: List<UserId>,
        name: String?,
        key: String?,
    ): ChannelCid {
        opened += Opened(kind, members, name, key)
        openError?.let {
            openError = null
            throw it
        }
        return cid
    }

    override suspend fun people(query: String?): ChatPeople {
        queries += query
        peopleError?.let {
            peopleError = null
            throw it
        }
        return peopleAnswer?.invoke(query) ?: people
    }

    override suspend fun unread(): Int = unread
}

/** The provider's SDK in memory: what it was asked to connect, and a failure or a wait to give. */
class FakeChatGateway : ChatGateway {
    val connected = mutableListOf<ChatCredentials>()
    val disconnects = mutableListOf<Boolean>()
    var failure: Throwable? = null

    /** When set, [connect] waits for it before it answers. */
    var hold: CompletableDeferred<Unit>? = null
    var refreshed: ChatCredentials? = null

    override suspend fun connect(
        credentials: ChatCredentials,
        refresh: suspend () -> ChatCredentials,
    ) {
        hold?.await()
        failure?.let {
            failure = null
            throw it
        }
        connected += credentials
        // What the SDK does when the first token has expired.
        refreshed = refresh()
    }

    override suspend fun disconnect(forget: Boolean) {
        disconnects += forget
    }
}

/** A model of the catalog; `n` makes the id and the name. */
fun componentModel(
    n: Int,
    brand: String = "Shimano",
    category: String = "Трансмиссия",
    archived: Boolean = false,
    coverUrl: String? = null,
) =
    ComponentModel(
        id = ComponentId("c$n"),
        category = category,
        brand = brand,
        name = "Кассета $n",
        description = "Кассета $n для горного велосипеда.",
        path = "/components/c$n",
        builds = n * 3,
        firstPublicAt = Instant.parse("2026-08-01T10:00:00Z"),
        coverUrl = coverUrl,
        archived = archived,
    )

fun componentModels(from: Int, count: Int): List<ComponentModel> =
    (from until from + count).map { componentModel(it) }

fun componentPhoto(n: Int, caption: String = "", source: PhotoSource? = null) =
    ComponentPhoto(
        id = "p$n",
        url = "https://colabike.test/media/components/$n.jpg",
        width = 1600,
        height = 1200,
        caption = caption,
        source = source,
        author = rider,
        isCover = n == 0,
    )

/**
 * The catalog in memory: pages by cursor, models by id (with canonical aliases), queued failures.
 */
class FakeComponents(
    var pages: Map<String?, Page<ComponentModel>> =
        mapOf(null to Page(componentModels(1, 4), null)),
    var filters: ComponentFilters =
        ComponentFilters(listOf("Трансмиссия", "Тормоза"), listOf("Shimano", "SRAM")),
    var models: Map<String, ComponentModel> = componentModels(1, 4).associateBy { it.id.value },
    /** A merged model's id → the canonical one it answers with. */
    var aliases: Map<String, String> = emptyMap(),
    var photos: Map<String, List<ComponentPhoto>> =
        mapOf(
            "c1" to
                listOf(
                    componentPhoto(
                        0,
                        caption = "Кассета в сборе",
                        source =
                            PhotoSource(
                                provider = "Wikimedia Commons",
                                url = "https://commons.wikimedia.org/wiki/File:Deore.jpg",
                                title = "File:Deore.jpg",
                                creator = "Иван Фотограф",
                                credit = "Иван Фотограф / Wikimedia Commons",
                                license = "CC BY-SA 4.0",
                                licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
                            ),
                    ),
                    componentPhoto(1),
                )
        ),
) : ComponentsRepository {
    val queries = mutableListOf<Pair<ComponentQuery, String?>>()
    val modelCalls = mutableListOf<String>()
    val photoCalls = mutableListOf<String>()
    var filterCalls = 0
    var nextError: DataError? = null
    var filtersError: DataError? = null
    var photosError: DataError? = null

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    override suspend fun page(
        query: ComponentQuery,
        cursor: String?,
        limit: Int,
    ): Page<ComponentModel> {
        queries += query to cursor
        fail()
        return pages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun filters(): ComponentFilters {
        filterCalls++
        filtersError?.let {
            filtersError = null
            throw it
        }
        return filters
    }

    override suspend fun model(id: ComponentId): ComponentModel {
        modelCalls += id.value
        fail()
        return models[aliases[id.value] ?: id.value] ?: throw DataError.NotFound()
    }

    override suspend fun photos(id: ComponentId): List<ComponentPhoto> {
        photoCalls += id.value
        photosError?.let {
            photosError = null
            throw it
        }
        return photos[id.value].orEmpty()
    }
}

fun listingBrief(
    n: Int,
    title: String = "Втулка $n",
    price: Double? = 2_500.0 + n,
    type: String = "sale",
    category: String = "components",
    location: String = "Москва",
    author: Person = rider,
) =
    ListingBrief(
        id = "l$n",
        title = title,
        price = price,
        currency = "RUB",
        category = category,
        type = type,
        location = location,
        cover = null,
        author = author,
    )

fun listingBriefs(from: Int, count: Int): List<ListingBrief> =
    (from until from + count).map { listingBrief(it) }

fun listingModel(
    n: Int,
    status: ListingStatus = ListingStatus.Active,
    expired: Boolean = false,
    hasContact: Boolean = true,
    isOwner: Boolean = false,
    saved: Boolean = false,
    condition: ListingCondition? = ListingCondition.Used,
    componentModel: ListingCatalogLink? = null,
    bikeModel: ListingCatalogLink? = null,
    linkedBike: ListingBikeLink? = null,
    brief: ListingBrief = listingBrief(n),
) =
    Listing(
        brief = brief,
        description = "Описание объявления $n.",
        condition = condition,
        status = status,
        expired = expired,
        hasContact = hasContact,
        publishedAt = Instant.parse("2026-09-21T10:00:00Z"),
        path = "/market/l$n",
        photos = listOf(Photo("lp$n", "https://colabike.test/api/market/media/lp$n")),
        isOwner = isOwner,
        componentModel = componentModel,
        bikeModel = bikeModel,
        linkedBike = linkedBike,
        saved = saved,
    )

/**
 * The market in memory: pages by cursor, listings by id, what is saved, a contact per listing and
 * queued failures. Every contact request is logged, so a test can say that none was made.
 */
class FakeMarket(
    var pages: Map<String?, Page<ListingBrief>> = mapOf(null to Page(listingBriefs(1, 4), null)),
    var listings: Map<String, Listing> = (1..4).associate { "l$it" to listingModel(it) },
    var others: Map<String, SellerListings> =
        mapOf("l1" to SellerListings(listingBriefs(11, 2), total = 6)),
    var savedPages: Map<String?, Page<ListingBrief>> =
        mapOf(null to Page(listingBriefs(1, 2), null)),
    var contacts: Map<String, String> = mapOf("l1" to "+7 900 111-22-33, Telegram @seller"),
) : MarketRepository {
    val queries = mutableListOf<Pair<MarketQuery, String?>>()
    val listingCalls = mutableListOf<String>()
    val saveCalls = mutableListOf<Pair<String, Boolean>>()
    val contactCalls = mutableListOf<String>()
    var nextError: DataError? = null
    var listingError: DataError? = null
    var saveError: DataError? = null
    var contactError: DataError? = null
    var othersError: DataError? = null

    private val changes = MutableSharedFlow<SavedListingChange>(extraBufferCapacity = 16)
    override val savedChanges: SharedFlow<SavedListingChange> = changes.asSharedFlow()

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    override suspend fun page(query: MarketQuery, cursor: String?, limit: Int): Page<ListingBrief> {
        queries += query to cursor
        fail()
        return pages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun listing(id: ListingId): Listing {
        listingCalls += id.value
        listingError?.let {
            listingError = null
            throw it
        }
        return listings[id.value] ?: throw DataError.NotFound()
    }

    override suspend fun sellerOthers(id: ListingId): SellerListings {
        othersError?.let {
            othersError = null
            throw it
        }
        return others[id.value] ?: SellerListings(emptyList(), 0)
    }

    override suspend fun saved(cursor: String?, limit: Int): Page<ListingBrief> {
        fail()
        return savedPages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun setSaved(id: ListingId, saved: Boolean): Boolean {
        saveCalls += id.value to saved
        saveError?.let {
            saveError = null
            throw it
        }
        changes.tryEmit(SavedListingChange(id, saved))
        return saved
    }

    override suspend fun contact(id: ListingId): String {
        contactCalls += id.value
        contactError?.let {
            contactError = null
            throw it
        }
        return contacts[id.value] ?: throw DataError.NotFound()
    }
}

/** The config in memory: a state to set, and a count of the times the app asked to look again. */
class FakeAppConfig(initial: AppConfigState = AppConfigState(loaded = true)) : AppConfigSource {
    override val state = MutableStateFlow(initial)
    var refreshes = 0

    override fun refreshIfStale() {
        refreshes++
    }

    var forced = 0

    override fun refreshNow() {
        forced++
    }

    /** The app as the server configured it. */
    fun set(
        features: FeatureAvailability = FeatureAvailability.AllOn,
        links: ServiceLinks = ServiceLinks.None,
        compatibility: Compatibility = Compatibility.None,
        notice: AppNotice? = null,
        launch: LaunchConfig = LaunchConfig.Off,
        onboarding: OnboardingConfig = OnboardingConfig.Off,
        validatedAt: Instant = Instant.parse("2026-10-03T20:00:00Z"),
    ) {
        state.value =
            AppConfigState(
                config =
                    AppConfig.Builtin.copy(
                        revision = 1,
                        features = features,
                        links = links,
                        compatibility = compatibility,
                        notice = notice,
                        launch = launch,
                        onboarding = onboarding,
                    ),
                loaded = true,
                validatedAt = validatedAt,
            )
    }
}

/** The kept pictures of the config, as a set of addresses with a file each. */
class FakeConfigAssets(val kept: MutableMap<String, java.io.File> = mutableMapOf()) : ConfigAssets {
    override suspend fun prefetch(urls: List<String>) = true

    override suspend fun retainOnly(urls: List<String>) = Unit

    override fun fileOf(url: String): java.io.File? = kept[url]
}

/** A switch for [FeatureAvailability]: everything on, except the named ones. */
fun featuresOff(vararg off: Feature) = FeatureAvailability(off.associate { it.key to false })

/** An account that has chosen nothing yet and a server that can carry no push (the state today). */
val defaultNotificationSettings =
    NotificationSettings(
        channels =
            AccountChannels(
                emailAvailable = true,
                emailVerified = true,
                emailEnabled = false,
                pushAvailable = false,
                pushEnabled = false,
            ),
        categories =
            listOf(
                CategorySetting(
                    NotificationCategory.Rides,
                    "rides",
                    "Покатушки и приглашения",
                    ChannelFlag(supported = true, enabled = false),
                    ChannelFlag(supported = true, enabled = true),
                ),
                CategorySetting(
                    NotificationCategory.Discussions,
                    "discussions",
                    "Комментарии и ответы",
                    ChannelFlag(supported = true, enabled = false),
                    ChannelFlag(supported = true, enabled = true),
                ),
                CategorySetting(
                    NotificationCategory.Market,
                    "market",
                    "Окончание срока объявлений",
                    ChannelFlag(supported = true, enabled = false),
                    ChannelFlag(supported = false, enabled = false),
                ),
                CategorySetting(
                    NotificationCategory.Plans,
                    "plans",
                    "Новые планы друзей",
                    ChannelFlag(supported = false, enabled = false),
                    ChannelFlag(supported = true, enabled = true),
                ),
                CategorySetting(
                    NotificationCategory.Intents,
                    "intents",
                    "Намерения друзей",
                    ChannelFlag(supported = false, enabled = false),
                    ChannelFlag(supported = true, enabled = true),
                ),
            ),
        reminders = true,
        timeZone = null,
        quietHours = QuietHours(false, LocalTime.of(22, 0), LocalTime.of(7, 0), false),
        pausedUntil = null,
        circleMode = CircleMode.Friends,
        circleMembers = emptyList(),
        considering = false,
        mutes = emptyList(),
        updatedAt = null,
    )

/**
 * The settings of the account as a server would keep them: a change is applied to what is held and
 * the result returned. [failChange] makes the next change fail, [gate] holds it on its way.
 */
class FakeNotificationSettings(
    var current: NotificationSettings = defaultNotificationSettings,
    var loadError: DataError? = null,
) : NotificationSettingsRepository {
    val changes = mutableListOf<NotificationSettingsChange>()
    var failChange: DataError? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun settings(): NotificationSettings {
        loadError?.let { throw it }
        return current
    }

    override suspend fun change(change: NotificationSettingsChange): NotificationSettings {
        changes += change
        gate?.await()
        failChange?.let {
            failChange = null
            throw it
        }
        val c = current
        current =
            c.copy(
                channels =
                    c.channels.copy(
                        emailEnabled = change.emailEnabled ?: c.channels.emailEnabled,
                        pushEnabled = change.pushEnabled ?: c.channels.pushEnabled,
                    ),
                categories =
                    c.categories.map { cat ->
                        cat.copy(
                            push =
                                cat.push.copy(
                                    enabled = change.categoryPush[cat.key] ?: cat.push.enabled
                                ),
                            email =
                                cat.email.copy(
                                    enabled = change.categoryEmail[cat.key] ?: cat.email.enabled
                                ),
                        )
                    },
                reminders = change.reminders ?: c.reminders,
                timeZone = change.timeZone ?: c.timeZone,
                quietHours =
                    c.quietHours.copy(
                        enabled = change.quietEnabled ?: c.quietHours.enabled,
                        from = change.quietFrom ?: c.quietHours.from,
                        to = change.quietTo ?: c.quietHours.to,
                        allowCancellations =
                            change.quietAllowCancellations ?: c.quietHours.allowCancellations,
                    ),
                pausedUntil = if (change.resume) null else change.pauseUntil ?: c.pausedUntil,
                circleMode = change.circleMode ?: c.circleMode,
                circleMembers =
                    c.circleMembers.filterNot { it.id.value in change.circleRemove } +
                        change.circleAdd.map { id ->
                            Person(UserId(id), "added-$id", "Добавленный", null)
                        },
                considering = change.considering ?: c.considering,
                mutes = c.mutes.filterNot { m -> change.muteRemove.any { it.id == m.id } },
                updatedAt = Instant.parse("2026-10-03T20:00:00Z"),
            )
        return current
    }
}

/** A phone that has been asked for nothing: the state of a fresh install on Android 13+. */
val freshPhone =
    DeviceNotificationsState(
        permission = OsPermission.NotAsked,
        appEnabled = false,
        channelsOff = emptyList(),
        provider = PushAvailability.NotConfigured,
    )

/** A phone whose person refused the system's question. */
val deniedPhone =
    DeviceNotificationsState(
        permission = OsPermission.Denied,
        appEnabled = false,
        channelsOff = emptyList(),
        provider = PushAvailability.NoDistributor,
    )

/** A phone that shows the app's notifications. */
val readyPhone =
    DeviceNotificationsState(
        permission = OsPermission.Granted,
        appEnabled = true,
        channelsOff = emptyList(),
        provider = PushAvailability.Available,
    )

/** An account that has set most things: a chosen circle, quiet hours, a pause, mutes. */
val busyNotificationSettings =
    defaultNotificationSettings.copy(
        channels = defaultNotificationSettings.channels.copy(emailEnabled = true),
        timeZone = ZoneId.of("Europe/Moscow"),
        quietHours = QuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0), true),
        pausedUntil = Instant.parse("2026-10-04T08:00:00Z"),
        circleMode = CircleMode.Selected,
        circleMembers = listOf(rider),
        considering = true,
        mutes =
            listOf(
                NotificationMute(MuteKind.Ride, "ride-1", "Воскресный выезд за город"),
                NotificationMute(MuteKind.Author, "u-quiet", null),
            ),
    )

class FakeDeviceNotifications(var state: DeviceNotificationsState = freshPhone) :
    DeviceNotifications {
    var asked = 0

    override suspend fun state(): DeviceNotificationsState = state

    override fun markAsked() {
        asked++
    }

    override fun settingsIntent(): android.content.Intent = android.content.Intent("test.SETTINGS")

    override fun channelIntent(channel: PushChannel): android.content.Intent =
        android.content.Intent("test.CHANNEL")
}

/** The grid of the site: a cell of 0.03° by 0.05°, the same as the server's. */
val testNearbyLimits =
    NearbyLimits(
        minRadiusM = 5_000,
        maxRadiusM = 50_000,
        radiusStepM = 1_000,
        deviceTtlHours = 24,
        grid = NearbyGrid(latStep = 0.03, lngStep = 0.05),
    )

/** Off, with no area: the state of a person who has never opened the screen. */
val defaultNearbySettings =
    NearbySettings(
        available = true,
        enabled = false,
        source = null,
        area = null,
        observedAt = null,
        expiresAt = null,
        expired = false,
        horizonDays = 7,
        preferences = NearbyPreferences(),
        limits = testNearbyLimits,
        version = "\"nearby-1\"",
    )

/** On, with an area from the site and a kind chosen. */
val activeNearbySettings =
    defaultNearbySettings.copy(
        enabled = true,
        source = NearbySource.Manual,
        area = NearbyArea(label = "Центр", longitude = 37.625, latitude = 55.755, radiusM = 10_000),
        preferences = NearbyPreferences(purposes = setOf("leisure")),
        version = "\"nearby-2\"",
    )

/**
 * The area as a server keeps it: a change is applied to what is held, the version moves on, and
 * [failNext] makes the next write fail. Nothing here knows a place but the one it was given.
 */
class FakeNearby(
    var current: NearbySettings = defaultNearbySettings,
    var loadError: DataError? = null,
    var offers: NearbyOffers = NearbyOffers(NearbyOffersState.Ready, emptyList()),
) : NearbyRepository {
    val changes = mutableListOf<NearbyChange>()

    /** The areas a phone confirmed: radius and the centre it sent (to check the rounding). */
    val confirmed = mutableListOf<Triple<Double, Double, Int>>()
    var replaced: Boolean? = null
    var removed = 0
    var forgotten = 0
    var offerCalls = 0
    var failNext: DataError? = null
    var offersError: DataError? = null

    private fun fail() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    private var revision = 0

    private fun bump(next: NearbySettings): NearbySettings {
        current = next.copy(version = "\"nearby-${++revision}\"")
        return current
    }

    override suspend fun settings(): NearbySettings {
        loadError?.let { throw it }
        return current
    }

    override suspend fun change(change: NearbyChange, version: String?): NearbySettings {
        changes += change
        fail()
        val c = current
        return bump(
            c.copy(
                enabled = change.enabled ?: c.enabled,
                horizonDays = change.horizonDays ?: c.horizonDays,
                preferences =
                    c.preferences.copy(
                        purposes = change.purposes ?: c.preferences.purposes,
                        paces = change.paces ?: c.preferences.paces,
                        surfaces = change.surfaces ?: c.preferences.surfaces,
                    ),
            )
        )
    }

    override suspend fun confirmDeviceArea(
        longitude: Double,
        latitude: Double,
        radiusM: Int,
        replaceManual: Boolean,
        version: String?,
    ): NearbySettings {
        confirmed += Triple(longitude, latitude, radiusM)
        replaced = replaceManual
        fail()
        if (current.source == NearbySource.Manual && !replaceManual) {
            throw DataError.Rejected(409, "nearby_area_source", "Действует район с сайта")
        }
        return bump(
            current.copy(
                source = NearbySource.Device,
                area = NearbyArea(null, longitude, latitude, radiusM),
                observedAt = Instant.parse("2026-10-03T20:00:00Z"),
                expiresAt = Instant.parse("2026-10-04T20:00:00Z"),
                expired = false,
            )
        )
    }

    override suspend fun removeArea(version: String?): NearbySettings {
        removed++
        fail()
        return bump(current.copy(source = null, area = null, observedAt = null, expiresAt = null))
    }

    override suspend fun forget() {
        forgotten++
        fail()
        current = defaultNearbySettings.copy(version = "\"nearby-forgotten\"")
    }

    override suspend fun offers(limit: Int): NearbyOffers {
        offerCalls++
        offersError?.let { throw it }
        return offers
    }
}

/** The phone's approximate place, as a test says it is. */
class FakeCoarseLocation(
    var granted: Boolean = true,
    var result: CoarseResult = CoarseResult.Located(CoarseFix(37.6173, 55.7558)),
) : CoarseLocation {
    var reads = 0

    override fun granted() = granted

    override suspend fun current(): CoarseResult {
        reads++
        return result
    }
}

/** A window ahead of the fixed test clock (2026-10-03), in Moscow's zone. */
fun sampleIntent(
    n: Int,
    own: Boolean = false,
    status: IntentStatus = IntentStatus.Active,
    visibility: IntentVisibility = IntentVisibility.Community,
    readiness: IntentReadiness = IntentReadiness.Ready,
    area: String = "Парк Горького",
    version: String? = "\"v$n\"",
) =
    RideIntent(
        id = "b3000000-0000-4000-8000-00000000000$n",
        own = own,
        readiness = readiness,
        timeZone = java.time.ZoneId.of("Europe/Moscow"),
        passport =
            RidePassport(areaLabel = area, purpose = "leisure", pace = "relaxed", surface = null),
        windows =
            listOf(
                IntentWindow(
                    Instant.parse("2026-10-10T07:00:00Z"),
                    Instant.parse("2026-10-10T10:00:00Z"),
                )
            ),
        meetNewPeople = true,
        visibility = visibility,
        status = status,
        allowSuggestions = if (own) true else null,
        author = if (own) PreviewData.rider.copy(name = "Вы") else PreviewData.rider,
        createdAt = Instant.parse("2026-10-03T08:00:00Z"),
        updatedAt = Instant.parse("2026-10-03T08:30:00Z"),
        version = version,
    )

/**
 * Intentions as a server would keep them: the lists by segment, an intention by id, and a create
 * that is the same intention for the same key. [failNext] makes the next write fail.
 */
class FakeIntents(
    var community: List<RideIntent> =
        listOf(sampleIntent(1), sampleIntent(2, area = "Лосиный остров")),
    var mine: List<RideIntent> = emptyList(),
    var loadError: DataError? = null,
) : IntentsRepository {
    val created = mutableListOf<Pair<IntentDraft, String>>()
    val replaced = mutableListOf<Triple<String, IntentDraft, String?>>()
    val cancelled = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var getCalls = 0
    var failNext: DataError? = null
    private val byKey = mutableMapOf<String, RideIntent>()
    private var counter = 50

    private fun fail() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun own(cursor: String?, limit: Int): Page<RideIntent> {
        loadError?.let { throw it }
        return Page(mine, null)
    }

    override suspend fun community(cursor: String?, limit: Int): Page<RideIntent> {
        loadError?.let { throw it }
        return Page(community, null)
    }

    override suspend fun get(id: String): RideIntent {
        getCalls++
        loadError?.let { throw it }
        return (mine + community).firstOrNull { it.id == id } ?: throw DataError.NotFound()
    }

    override suspend fun create(draft: IntentDraft, key: String): RideIntent {
        created += draft to key
        fail()
        // The same key is the same intention.
        byKey[key]?.let {
            return it
        }
        counter++
        val made =
            sampleIntent(
                    counter % 10,
                    own = true,
                    visibility = draft.visibility,
                    version = "\"n$counter\"",
                )
                .copy(
                    id = "b3000000-0000-4000-8000-0000000000$counter",
                    readiness = draft.readiness,
                    passport = draft.passport,
                    timeZone = draft.timeZone,
                )
        byKey[key] = made
        mine = listOf(made) + mine
        return made
    }

    override suspend fun replace(id: String, draft: IntentDraft, version: String?): RideIntent {
        replaced += Triple(id, draft, version)
        fail()
        val current = mine.firstOrNull { it.id == id } ?: throw DataError.NotFound()
        val next =
            current.copy(
                readiness = draft.readiness,
                passport = draft.passport,
                visibility = draft.visibility,
                version = "\"${current.version}+\"",
            )
        mine = mine.map { if (it.id == id) next else it }
        return next
    }

    override suspend fun cancel(id: String): RideIntent {
        cancelled += id
        fail()
        val current = mine.firstOrNull { it.id == id } ?: throw DataError.NotFound()
        val next = current.copy(status = IntentStatus.Cancelled)
        mine = mine.map { if (it.id == id) next else it }
        return next
    }

    override suspend fun delete(id: String) {
        deleted += id
        fail()
        mine = mine.filterNot { it.id == id }
    }
}

/** A plan's date as the server reads it for an invited person, ahead of the fixed test clock. */
fun sampleParticipation(
    state: ParticipationState = ParticipationState.Invited,
    response: ParticipationResponse? = null,
    previous: ParticipationResponse? = null,
    changed: Boolean = false,
    allowed: Set<ParticipationResponse> =
        setOf(
            ParticipationResponse.Accepted,
            ParticipationResponse.Maybe,
            ParticipationResponse.Declined,
        ),
    status: RideStatus = RideStatus.Planned,
    requested: RequestedDateStatus? = RequestedDateStatus.Current,
    revision: Int = 3,
    changes: Set<AgreementChange> = emptySet(),
    role: ViewerRole = ViewerRole.Invitee,
    meetingHidden: Boolean = false,
    closed: Boolean = false,
    scheduledAt: Instant? = Instant.parse("2026-10-10T07:00:00Z"),
) =
    RideParticipation(
        rideId = "b2000000-0000-4000-8000-0000000000b2",
        title = "Воскресный выезд за город",
        status = status,
        description = "Спокойно, без гонки.",
        features = emptyList(),
        author = PreviewData.rider,
        timeZone = java.time.ZoneId.of("Europe/Moscow"),
        recurrence = RideRecurrence.Weekly,
        scheduledAt = scheduledAt,
        expectedEndAt = Instant.parse("2026-10-10T10:00:00Z"),
        requested = requested?.let { RequestedDate(Instant.parse("2026-10-10T07:00:00Z"), it) },
        agreement = RideAgreement(revision, changes, Instant.parse("2026-10-05T08:00:00Z")),
        recruitmentClosed = closed,
        meetingPoint = if (meetingHidden) null else "У входа в парк",
        meetingHidden = meetingHidden,
        passport = RidePassport(areaLabel = "Парк Горького"),
        going = 4,
        maybe = 2,
        role = role,
        state = state,
        response = response,
        previousResponse = previous,
        changedAfterAnswer = changed,
        allowed = allowed,
    )

/**
 * The part of a person in a plan as a server keeps it: an answer changes the state, "going" and
 * "maybe" are taken only for the edition of terms in force, and [conflictWith] makes the next
 * answer a refusal that carries the plan as it is then.
 */
class FakeParticipation(
    var current: RideParticipation? = sampleParticipation(),
    var loadError: DataError? = null,
) : ParticipationRepository {
    val asked = mutableListOf<Instant?>()
    val answers = mutableListOf<Triple<ParticipationResponse, Instant, Int?>>()
    var conflictWith: RideParticipation? = null
    var conflictToGone = false
    var failNext: DataError? = null
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private val mutableChanges = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val changes: SharedFlow<String> = mutableChanges

    override suspend fun get(rideId: String, occurrenceAt: Instant?): RideParticipation {
        asked += occurrenceAt
        loadError?.let { throw it }
        return current ?: throw DataError.NotFound()
    }

    override suspend fun respond(
        rideId: String,
        response: ParticipationResponse,
        occurrenceAt: Instant,
        expectedRevision: Int?,
    ): ParticipationOutcome {
        answers += Triple(response, occurrenceAt, expectedRevision)
        gate?.await()
        failNext?.let {
            failNext = null
            throw it
        }
        if (conflictToGone) {
            conflictToGone = false
            current = null
            return ParticipationOutcome.Changed(null)
        }
        conflictWith?.let {
            conflictWith = null
            current = it
            return ParticipationOutcome.Changed(it)
        }
        val base = current ?: throw DataError.NotFound()
        val next =
            base.copy(
                state =
                    when (response) {
                        ParticipationResponse.Accepted -> ParticipationState.Accepted
                        ParticipationResponse.Maybe -> ParticipationState.Maybe
                        ParticipationResponse.Declined -> ParticipationState.Declined
                    },
                response = response,
                previousResponse = null,
                changedAfterAnswer = false,
            )
        current = next
        mutableChanges.tryEmit(rideId)
        return ParticipationOutcome.Saved(next)
    }
}

class FakeDependencies(
    override val bikes: FakeBikes = FakeBikes(),
    override val account: FakeAccount = FakeAccount(),
    override val accountDeletion: FakeAccountDeletion = FakeAccountDeletion(),
    override val safety: FakeSafety = FakeSafety(),
    override val people: FakePeople = FakePeople(),
    override val feed: FakeFeed = FakeFeed(),
    override val journal: FakeJournal = FakeJournal(),
    override val comments: FakeComments = FakeComments(),
    override val rides: FakeRides = FakeRides(),
    override val notifications: FakeNotifications = FakeNotifications(),
    override val notificationSettings: FakeNotificationSettings = FakeNotificationSettings(),
    override val deviceNotifications: FakeDeviceNotifications = FakeDeviceNotifications(),
    override val participation: FakeParticipation = FakeParticipation(),
    override val intents: FakeIntents = FakeIntents(),
    override val nearby: FakeNearby = FakeNearby(),
    override val coarseLocation: FakeCoarseLocation = FakeCoarseLocation(),
    override val components: FakeComponents = FakeComponents(),
    override val market: FakeMarket = FakeMarket(),
    override val appConfig: FakeAppConfig = FakeAppConfig(),
    override val versionCode: Int = 1,
    override val configAssets: ConfigAssets = FakeConfigAssets(),
    override val chat: FakeChat = FakeChat(),
    val chatGateway: FakeChatGateway = FakeChatGateway(),
    override val chatSession: ChatSession =
        ChatSession(chat, chatGateway, CoroutineScope(Dispatchers.Unconfined)),
    override val chatScreens: ChatScreens = FakeChatScreens(),
    override val drafts: InMemoryCommentDrafts = InMemoryCommentDrafts(),
    override val sessions: FakeSessions = FakeSessions(),
    override val auth: FakeAuth = FakeAuth(),
    override val settings: FakeSettings = FakeSettings(),
    override val links: SiteLinks = SiteLinks("https://colabike.test"),
    override val pending: FakePending = FakePending(),
    override val clock: Clock = Clock.fixed(Instant.parse("2026-10-03T20:00:00Z"), ZoneOffset.UTC),
    override val maps: RouteMaps = SketchRouteMaps,
    override val pushSync: FakePushSync = FakePushSync(),
    override val visibleConversation: VisibleConversation = VisibleConversation(),
) : AppDependencies

class FakePushSync : PushSync {
    val requests = mutableListOf<Boolean>()

    override fun request(force: Boolean) {
        requests += force
    }
}
