package ru.colabike.app

import android.content.Context
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
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
    val calls = mutableListOf<Pair<BikeScope, String?>>()
    var nextError: DataError? = null

    /** How many times a bike's details were requested: a ViewModel that survived asks once. */
    var detailCalls = 0

    override suspend fun bikes(scope: BikeScope, cursor: String?, limit: Int): Page<BikeSummary> {
        calls += scope to cursor
        nextError?.let {
            nextError = null
            throw it
        }
        return pages[cursor] ?: Page(emptyList(), null)
    }

    override suspend fun bike(id: BikeId): BikeDetail {
        detailCalls++
        nextError?.let {
            nextError = null
            throw it
        }
        val summary =
            pages.values.flatMap { it.items }.firstOrNull { it.id == id }
                ?: throw DataError.NotFound()
        return BikeDetail(
            summary = summary,
            description = "Надёжный городской велосипед для поездок круглый год.",
            color = "Графит",
            size = "L",
            weightKg = 14.2,
            mileageKm = 1200,
            photos = listOfNotNull(summary.cover),
            components =
                listOf(
                    ru.colabike.core.model.BikeComponent(
                        "c1",
                        "build",
                        "Рама",
                        "Cube Aluminium Superlite",
                        "",
                    )
                ),
        )
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
    override val sessions: FakeSessions = FakeSessions(),
    override val auth: FakeAuth = FakeAuth(),
    override val settings: FakeSettings = FakeSettings(),
    override val links: SiteLinks = SiteLinks("https://colabike.test"),
    override val pending: FakePending = FakePending(),
    override val clock: Clock = Clock.fixed(Instant.parse("2026-10-03T20:00:00Z"), ZoneOffset.UTC),
) : AppDependencies
