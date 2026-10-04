package ru.colabike.app

import android.content.Context
import java.time.Clock
import java.time.Instant
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
import ru.colabike.app.comments.InMemoryCommentDrafts
import ru.colabike.app.config.AppConfigSource
import ru.colabike.app.config.AppConfigState
import ru.colabike.app.links.PendingNavigation
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.messages.ChatGateway
import ru.colabike.app.messages.ChatScreens
import ru.colabike.app.messages.ChatSession
import ru.colabike.app.navigation.Destination
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.rides.map.SketchRouteMaps
import ru.colabike.app.settings.AppSettings
import ru.colabike.app.settings.ThemeMode
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.AnalysisChannel
import ru.colabike.core.model.AnalysisPoint
import ru.colabike.core.model.AppConfig
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.BikeSearch
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.ChatCredentials
import ru.colabike.core.model.ChatPeople
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.ChatUser
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
import ru.colabike.core.model.Feature
import ru.colabike.core.model.FeatureAvailability
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.FeedRepository
import ru.colabike.core.model.FollowChange
import ru.colabike.core.model.FollowState
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalSummary
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
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationTarget
import ru.colabike.core.model.NotificationsRepository
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Page
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.Person
import ru.colabike.core.model.PersonSummary
import ru.colabike.core.model.Photo
import ru.colabike.core.model.PhotoSource
import ru.colabike.core.model.Profile
import ru.colabike.core.model.ProfileCounts
import ru.colabike.core.model.Range
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.RideAnalysis
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RidePassport
import ru.colabike.core.model.RideRole
import ru.colabike.core.model.RideRoute
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RideSummary
import ru.colabike.core.model.RidesRepository
import ru.colabike.core.model.SavedChange
import ru.colabike.core.model.SavedListingChange
import ru.colabike.core.model.SellerListings
import ru.colabike.core.model.ServiceLinks
import ru.colabike.core.model.SessionKind
import ru.colabike.core.model.SessionPlatform
import ru.colabike.core.model.UpcomingRide
import ru.colabike.core.model.UserId

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
) : NotificationsRepository {
    val pageCalls = mutableListOf<String?>()
    var countCalls = 0
    var nextError: DataError? = null

    private fun fail() {
        nextError?.let {
            nextError = null
            throw it
        }
    }

    override suspend fun page(cursor: String?, limit: Int): Page<AppNotification> {
        pageCalls += cursor
        fail()
        return pages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun count(): NotificationCount {
        countCalls++
        fail()
        return unread
    }
}

fun notification(
    n: Int,
    kind: String = "comment",
    type: String = "bike",
    read: Boolean = false,
    path: String = "/b/6e7f8091-a2b3-4c4d-9e5f-60718293a4b5",
    actor: Person? = PreviewData.rider,
) =
    AppNotification(
        id = "n$n",
        kind = kind,
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
    )

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

    override suspend fun signOut() {
        signOuts++
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

    /** The app as the server configured it. */
    fun set(
        features: FeatureAvailability = FeatureAvailability.AllOn,
        links: ServiceLinks = ServiceLinks.None,
        compatibility: Compatibility = Compatibility.None,
    ) {
        state.value =
            AppConfigState(
                config =
                    AppConfig.Builtin.copy(
                        revision = 1,
                        features = features,
                        links = links,
                        compatibility = compatibility,
                    ),
                loaded = true,
                validatedAt = Instant.parse("2026-10-03T20:00:00Z"),
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

class FakeDependencies(
    override val bikes: FakeBikes = FakeBikes(),
    override val account: FakeAccount = FakeAccount(),
    override val people: FakePeople = FakePeople(),
    override val feed: FakeFeed = FakeFeed(),
    override val journal: FakeJournal = FakeJournal(),
    override val comments: FakeComments = FakeComments(),
    override val rides: FakeRides = FakeRides(),
    override val notifications: FakeNotifications = FakeNotifications(),
    override val components: FakeComponents = FakeComponents(),
    override val market: FakeMarket = FakeMarket(),
    override val appConfig: FakeAppConfig = FakeAppConfig(),
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
) : AppDependencies
