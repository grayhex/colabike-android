package ru.colabike.app.auth

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.colabike.core.auth.AuthState
import ru.colabike.core.auth.CustomTabs
import ru.colabike.core.auth.DeviceSession
import ru.colabike.core.auth.YandexSignIn
import ru.colabike.core.model.DataError
import ru.colabike.core.network.apiCall

/** Sign-in and sign-out as the screens see them. An interface, so tests and previews fake it. */
interface AuthActions {
    val state: StateFlow<AuthState>
    val yandexEnabled: Boolean
    /** Failures of the browser flow, which finishes outside any screen. */
    val yandexFailures: Flow<YandexFailure>

    /** Throws [DataError]; on success [state] becomes signed in. */
    suspend fun signIn(email: String, password: String)

    fun startYandex(context: Context)

    suspend fun signOut()
}

sealed interface YandexFailure {
    data class Flow(val reason: YandexSignIn.Reason) : YandexFailure

    data class Exchange(val error: DataError) : YandexFailure
}

class AuthController(
    private val session: DeviceSession,
    private val yandex: YandexSignIn,
    private val yandexReady: () -> Boolean,
    private val revoke: () -> Unit,
    private val scope: CoroutineScope,
) : AuthActions {
    override val yandexEnabled: Boolean
        get() = yandexReady()

    override val state: StateFlow<AuthState> = session.state

    private val failures = Channel<YandexFailure>(Channel.BUFFERED)
    override val yandexFailures: Flow<YandexFailure> = failures.receiveAsFlow()

    override suspend fun signIn(email: String, password: String) {
        session.signIn(email, password)
    }

    override fun startYandex(context: Context) {
        CustomTabs.open(context, yandex.startUrl())
    }

    /**
     * An App Link reached the activity: finish the native flow if it is ours. Someone already
     * signed in has no sign-in to finish; a stray return would only leave an error waiting for the
     * next sign-in screen.
     */
    fun handleLink(link: String) {
        if (state.value is AuthState.SignedIn) return
        when (val result = yandex.handleReturn(link)) {
            is YandexSignIn.Return.Code ->
                scope.launch {
                    try {
                        session.signInWithCode(result.code, result.verifier)
                    } catch (e: DataError) {
                        failures.send(YandexFailure.Exchange(e))
                    }
                }
            is YandexSignIn.Return.Failed -> failures.trySend(YandexFailure.Flow(result.reason))
            YandexSignIn.Return.NotOurs -> Unit
        }
    }

    override suspend fun signOut() {
        session.signOut { apiCall { revoke() } }
    }
}
