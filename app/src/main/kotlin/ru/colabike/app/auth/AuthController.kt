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

    /**
     * A fresh sign-in with Yandex by someone who is signed in already, to confirm an action that
     * cannot be undone (deleting an account that has no password). The browser flow is the same;
     * what comes back is [yandexReauth], not a new session.
     */
    fun startYandexReauth(context: Context)

    /** What the browser brought back for [startYandexReauth]; one result per return. */
    val yandexReauth: Flow<YandexReauth>

    suspend fun signOut()

    /**
     * The server deleted the account and ended every session with it: there is nothing to revoke,
     * and everything this phone kept of the person goes, as at sign-out.
     */
    suspend fun accountDeleted()
}

/** The return of the browser for a confirmation made while signed in. */
sealed interface YandexReauth {
    /** A one-time code and the verifier it belongs to. Secrets: [toString] hides both. */
    class Proof(val code: String, val verifier: String) : YandexReauth {
        override fun toString() = "Proof(…)"
    }

    data class Failed(val reason: YandexSignIn.Reason) : YandexReauth
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

    // A return that nobody collects yet (the app was recreated while the browser was in front)
    // waits here for the screen that asked.
    private val reauths = Channel<YandexReauth>(Channel.BUFFERED)
    override val yandexReauth: Flow<YandexReauth> = reauths.receiveAsFlow()

    override suspend fun signIn(email: String, password: String) {
        session.signIn(email, password)
    }

    override fun startYandex(context: Context) {
        CustomTabs.open(context, yandex.startUrl())
    }

    // True from the moment the person asked for a confirmation until its return: only then is a
    // failed return (a cancelled browser) news for a screen. Lost with the process, which only
    // means that a cancellation after a restart is silent.
    @Volatile private var reauthAsked = false

    override fun startYandexReauth(context: Context) {
        CustomTabs.open(context, beginReauth())
    }

    /** The asking, without the browser: the address to open, with a new verifier kept for it. */
    internal fun beginReauth(): String {
        // What a previous attempt left unread is not the answer to this one.
        while (reauths.tryReceive().isSuccess) Unit
        reauthAsked = true
        return yandex.startUrl()
    }

    /**
     * An App Link reached the activity: finish the native flow if it is ours. Someone already
     * signed in has no sign-in to finish: a return is a confirmation they asked for, and goes to
     * [yandexReauth]; a stray one would only leave an error waiting for the next sign-in screen.
     */
    fun handleLink(link: String) {
        if (state.value is AuthState.SignedIn) {
            when (val result = yandex.handleReturn(link)) {
                is YandexSignIn.Return.Code -> {
                    reauthAsked = false
                    reauths.trySend(YandexReauth.Proof(result.code, result.verifier))
                }
                is YandexSignIn.Return.Failed -> {
                    // A link of nobody's asking is not an error to show.
                    if (reauthAsked && result.reason != YandexSignIn.Reason.NoPendingSignIn)
                        reauths.trySend(YandexReauth.Failed(result.reason))
                    reauthAsked = false
                }
                YandexSignIn.Return.NotOurs -> Unit
            }
            return
        }
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

    override suspend fun accountDeleted() {
        session.signOut {}
    }
}
