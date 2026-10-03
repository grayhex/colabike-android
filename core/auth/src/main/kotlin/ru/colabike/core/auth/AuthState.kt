package ru.colabike.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.colabike.core.model.Account

/** Whether the app has a device session. Screens observe this; they never see tokens. */
sealed interface AuthState {
    /** The stored session has not been read yet (first frames after start). */
    data object Restoring : AuthState

    data object SignedOut : AuthState

    /**
     * A device session exists. [account] comes with every token grant; after a cold start it is
     * null until the first refresh, because only the refresh token is stored.
     */
    data class SignedIn(val account: Account?) : AuthState
}

/** What the server says about this device; sent with every sign-in. */
data class DeviceInfo(val name: String, val appVersion: String)

/**
 * One item each time a signed-in person is signed out (by choice, or because the session ended for
 * good). Not for the start without a session (`Restoring` to `SignedOut`): there is nothing of a
 * previous person to clear, and a guest's cached pictures should survive a restart.
 */
fun Flow<AuthState>.signOuts(): Flow<Unit> = flow {
    var before: AuthState? = null
    collect { now ->
        if (before is AuthState.SignedIn && now == AuthState.SignedOut) emit(Unit)
        before = now
    }
}
