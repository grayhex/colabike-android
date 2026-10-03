package ru.colabike.core.auth

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ru.colabike.api.apis.SessionsApi
import ru.colabike.api.infrastructure.ClientException
import ru.colabike.api.infrastructure.ServerException
import ru.colabike.api.models.CreateSessionRequest
import ru.colabike.api.models.DeviceInput
import ru.colabike.api.models.RefreshRequest
import ru.colabike.api.models.SessionGrant
import ru.colabike.core.model.Account
import ru.colabike.core.model.DataError
import ru.colabike.core.network.MediaUrls
import ru.colabike.core.network.apiCall
import ru.colabike.core.network.failure
import ru.colabike.core.network.toAccount

/**
 * The device session of API v1 (cola docs/modules/api-v1.md, "Сессии устройств"): the access token
 * in memory, the refresh token in [store], one refresh at a time.
 *
 * [plainSessions] must use an HTTP client without [AuthInterceptor]: sign-in and refresh carry
 * their own credentials. [authedSessions] (with the interceptor) is only used to sign out.
 */
class DeviceSession(
    private val plainSessions: SessionsApi,
    private val store: SecretStore,
    private val device: DeviceInfo,
    private val media: MediaUrls,
    private val clock: Clock = Clock.systemUTC(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = ReentrantLock()
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state: StateFlow<AuthState> = mutableState.asStateFlow()

    // Guarded by lock. Never logged, never put into state.
    private var access: Token? = null
    private var refresh: String? = null

    /** Reads the stored refresh token; no network. Call once at start. */
    suspend fun restore() =
        withContext(io) {
            lock.withLock {
                refresh = store.read()
                mutableState.value =
                    if (refresh != null) AuthState.SignedIn(account = null) else AuthState.SignedOut
            }
        }

    /**
     * Signs in with e-mail and password. Throws [DataError] (`invalid_credentials` is Rejected).
     */
    suspend fun signIn(email: String, password: String): Account =
        signIn(
            CreateSessionRequest(device = deviceInput(), email = email.trim(), password = password)
        )

    /** Signs in with the one-time code of the native Yandex flow and its PKCE verifier. */
    suspend fun signInWithCode(code: String, codeVerifier: String): Account =
        signIn(
            CreateSessionRequest(device = deviceInput(), code = code, codeVerifier = codeVerifier)
        )

    private suspend fun signIn(request: CreateSessionRequest): Account {
        val grant = apiCall(io) { plainSessions.createSession(request) }
        return withContext(io) { lock.withLock { accept(grant) } }
    }

    /**
     * Ends the session on this device: the server first (best effort, so a lost connection does not
     * keep the user signed in), then everything local.
     */
    suspend fun signOut(revoke: suspend () -> Unit) {
        runCatching { revoke() }
        withContext(io) { lock.withLock { clearLocked() } }
    }

    /** The access token to send now, refreshing first when there is none or it is about to end. */
    fun accessTokenForRequest(): String? = lock.withLock {
        val current = access
        when {
            current != null &&
                current.expiresAt.isAfter(clock.instant().plusSeconds(EXPIRY_MARGIN_S)) ->
                current.value
            refresh == null -> null
            else -> refreshLocked()
        }
    }

    /**
     * After a 401 `token_expired` for [failed]: one refresh for all callers. A caller that comes
     * after another thread already refreshed gets the new token without a second refresh. Returns
     * null when the session ended; throws [IOException] when it could not be checked.
     */
    fun refreshAfter(failed: String): String? = lock.withLock {
        val current = access
        if (current != null && current.value != failed) current.value else refreshLocked()
    }

    /** After a 401 `invalid_token`: the session is gone (revoked, blocked, reused token). */
    fun invalidate(failed: String) = lock.withLock {
        if (access == null || access?.value == failed) clearLocked()
    }

    private fun refreshLocked(): String? {
        val token = refresh ?: return null
        val grant =
            try {
                exchange(token)
            } catch (e: IOException) {
                // A lost answer: the server accepts the same token once more within 30 seconds.
                exchange(token)
            }
                ?: run {
                    clearLocked()
                    return null
                }
        accept(grant)
        return access?.value
    }

    /** One refresh call; null when the server says the session is over. */
    private fun exchange(token: String): SessionGrant? =
        try {
            plainSessions.refreshSession(RefreshRequest(token))
        } catch (e: ClientException) {
            val failure = e.failure()
            if (failure.status == 401 || failure.status == 400) null
            else throw IOException("refresh refused: ${failure.status} ${failure.code}")
        } catch (e: ServerException) {
            throw IOException("refresh failed: ${e.statusCode}")
        }

    private fun accept(grant: SessionGrant): Account {
        store.write(grant.refreshToken)
        refresh = grant.refreshToken
        access = Token(grant.accessToken, grant.accessTokenExpiresAt.toInstant())
        val account = grant.user.toAccount(media)
        mutableState.value = AuthState.SignedIn(account)
        return account
    }

    private fun clearLocked() {
        access = null
        refresh = null
        store.clear()
        mutableState.value = AuthState.SignedOut
    }

    private fun deviceInput() =
        DeviceInput(
            name = device.name,
            platform = DeviceInput.Platform.android,
            appVersion = device.appVersion,
        )

    /** An access token with its end; toString never prints the value. */
    private class Token(val value: String, val expiresAt: Instant) {
        override fun toString() = "Token(expiresAt=$expiresAt)"
    }

    private companion object {
        /** Refresh this early instead of sending a token that ends on the way. */
        const val EXPIRY_MARGIN_S = 30L
    }
}
