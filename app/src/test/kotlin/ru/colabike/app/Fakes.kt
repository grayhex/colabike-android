package ru.colabike.app

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.auth.YandexFailure
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
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
    override suspend fun me(): Account = result()
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

class FakeDependencies(
    override val bikes: FakeBikes = FakeBikes(),
    override val account: FakeAccount = FakeAccount(),
    override val auth: FakeAuth = FakeAuth(),
) : AppDependencies
