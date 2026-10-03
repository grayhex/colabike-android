package ru.colabike.core.auth

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
