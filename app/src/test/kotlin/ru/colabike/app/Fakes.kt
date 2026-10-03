package ru.colabike.app

import android.content.Context
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.auth.YandexFailure
import ru.colabike.app.links.PendingNavigation
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.navigation.Destination
import ru.colabike.app.settings.AppSettings
import ru.colabike.app.settings.ThemeMode
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.BikeSearch
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.FeedRepository
import ru.colabike.core.model.FollowChange
import ru.colabike.core.model.FollowState
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.LikeChange
import ru.colabike.core.model.LikeState
import ru.colabike.core.model.Page
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.Person
import ru.colabike.core.model.PersonSummary
import ru.colabike.core.model.Profile
import ru.colabike.core.model.ProfileCounts
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.SavedChange
import ru.colabike.core.model.SessionKind
import ru.colabike.core.model.SessionPlatform
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

class FakeDependencies(
    override val bikes: FakeBikes = FakeBikes(),
    override val account: FakeAccount = FakeAccount(),
    override val people: FakePeople = FakePeople(),
    override val feed: FakeFeed = FakeFeed(),
    override val journal: FakeJournal = FakeJournal(),
    override val sessions: FakeSessions = FakeSessions(),
    override val auth: FakeAuth = FakeAuth(),
    override val settings: FakeSettings = FakeSettings(),
    override val links: SiteLinks = SiteLinks("https://colabike.test"),
    override val pending: FakePending = FakePending(),
    override val clock: Clock = Clock.fixed(Instant.parse("2026-10-03T20:00:00Z"), ZoneOffset.UTC),
) : AppDependencies
