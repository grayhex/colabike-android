package ru.colabike.app.push

import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.colabike.app.notifications.settings.DeviceNotifications
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NotificationSettingsRepository
import ru.colabike.core.model.PushDeviceRegistration
import ru.colabike.core.model.PushDeviceRepository

/**
 * Asks for the phone's registration to be made right with the server. Cheap to call and safe to
 * call often (a start, a sign-in, a change in the settings, an answer to the system's question):
 * the work is serialised and does nothing that is not needed.
 */
fun interface PushSync {
    /**
     * [force] reads the account's consent again before deciding; without it a registration that is
     * whole and fresh is left alone without any request.
     */
    fun request(force: Boolean)

    companion object {
        val None = PushSync {}
    }
}

/** What came of one reconciliation. */
sealed interface PushSyncResult {
    data object NotSignedIn : PushSyncResult

    /** The provider cannot deliver on this phone (no distributor, no project, a fault). */
    data object ProviderUnavailable : PushSyncResult

    /** The registration is whole and fresh, or there is nothing to do. */
    data object Unchanged : PushSyncResult

    /** The account has not said yes to push, or the phone does not show notifications. */
    data object NotWanted : PushSyncResult

    /** The provider has no address yet; it will say when it has (`onNewToken`). */
    data object NoToken : PushSyncResult

    data class Registered(val generation: Int) : PushSyncResult

    /** The consent was withdrawn or the phone stopped showing: the registration is gone. */
    data object Revoked : PushSyncResult

    /** Offline, refused or a fault: nothing changed here, the next trigger tries again. */
    data class Failed(val error: DataError) : PushSyncResult
}

/**
 * Keeps the phone's push registration at the server (cola#342) true to what the person chose. The
 * phone is registered while three things hold: the provider can deliver, the account has said yes
 * to push (and the server can carry it), and the phone shows notifications. When any stops holding,
 * the registration is revoked, so the server keeps no address for a phone that is not wanted. The
 * address the provider gives is only handed over: it is not stored, logged or shown. The provider's
 * library is started only after the account's yes (see [PushProvider.start]).
 *
 * The server forgets a registration with its session, so a sign-out needs no request here: the
 * binding is dropped at once, without a network, and the provider is asked to forget its address.
 */
class PushRegistrar(
    private val provider: PushProvider,
    private val devices: PushDeviceRepository,
    private val settings: NotificationSettingsRepository,
    private val phone: DeviceNotifications,
    private val store: StoredPushBinding,
    /** The signed-in account, or null (no session, or not known yet after a cold start). */
    private val account: () -> String?,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    /** A whole registration is repeated this often, so that the server sees the phone alive. */
    private val refreshAfter: Duration = Duration.ofDays(7),
) : PushSync {
    private val lock = Mutex()

    override fun request(force: Boolean) {
        scope.launch { sync(force = force) }
    }

    /** The provider issued a new address: it is sent with the generation the phone holds. */
    fun onNewToken(token: String) {
        scope.launch { sync(force = true, newToken = token) }
    }

    /** A sign-out: the binding is dropped now, and the provider forgets its address afterwards. */
    fun onSignedOut() {
        store.clear()
        scope.launch {
            try {
                provider.deleteToken()
            } catch (_: Exception) {
                // Best effort: the server has already forgotten this session's registration.
            }
        }
    }

    suspend fun sync(force: Boolean = false, newToken: String? = null): PushSyncResult =
        lock.withLock {
            val accountId =
                account()
                    ?: run {
                        // No account to speak for: whatever was kept belongs to nobody now.
                        store.clear()
                        return PushSyncResult.NotSignedIn
                    }
            val kept = store.record()
            val bound = kept?.takeIf { it.accountId == accountId }
            if (kept != null && bound == null) store.clear()

            if (provider.availability() != PushAvailability.Available) {
                if (bound != null) revoke(quiet = true)
                return PushSyncResult.ProviderUnavailable
            }
            val device = phone.state()
            val fresh = bound != null && isFresh(bound.syncedAtMillis)
            // Whole and fresh, nothing new asked for: no request at all.
            if (!force && newToken == null && fresh && device.canShow)
                return PushSyncResult.Unchanged

            val channels =
                try {
                    settings.settings().channels
                } catch (e: DataError) {
                    return failed(e)
                }
            if (!(channels.pushAvailable && channels.pushEnabled && device.canShow)) {
                return if (bound != null) revoke(quiet = false) else PushSyncResult.NotWanted
            }

            // The person said yes: only now does the provider's library start (and then it says
            // what the phone really can do, which the first look could not).
            provider.start()
            if (provider.availability() != PushAvailability.Available) {
                if (bound != null) revoke(quiet = true)
                return PushSyncResult.ProviderUnavailable
            }

            val token = newToken ?: provider.token() ?: return PushSyncResult.NoToken
            val fingerprint = fingerprintOf(token)
            if (
                newToken == null &&
                    bound != null &&
                    fresh &&
                    bound.fingerprint == fingerprint &&
                    bound.projectId == provider.projectId
            )
                return PushSyncResult.Unchanged
            return register(accountId, token, fingerprint, bound?.generation)
        }

    private suspend fun register(
        accountId: String,
        token: String,
        fingerprint: String,
        generation: Int?,
    ): PushSyncResult {
        var expected = generation
        for (attempt in 0..1) {
            try {
                val binding =
                    devices.register(
                        PushDeviceRegistration(
                            installationId = store.installationId(),
                            projectId = provider.projectId,
                            token = token,
                            expectedGeneration = expected,
                        )
                    )
                store.save(
                    StoredPushBinding.Record(
                        accountId = accountId,
                        generation = binding.generation,
                        projectId = binding.projectId,
                        fingerprint = fingerprint,
                        syncedAtMillis = clock.millis(),
                    )
                )
                return PushSyncResult.Registered(binding.generation)
            } catch (e: DataError.Rejected) {
                // The server holds another generation (a late answer of this phone, or another
                // callback got there first): read the current one and decide again, once.
                if (e.status != 409 || attempt == 1) return failed(e)
                expected =
                    try {
                        devices.current()?.generation
                    } catch (inner: DataError) {
                        return failed(inner)
                    }
            } catch (e: DataError) {
                return failed(e)
            }
        }
        return PushSyncResult.Failed(DataError.Unexpected(null))
    }

    /**
     * Takes the registration back; [quiet] ignores a failure of the request (the provider left).
     */
    private suspend fun revoke(quiet: Boolean): PushSyncResult {
        try {
            devices.revoke()
        } catch (e: DataError.SignedOut) {
            // The session is gone, and its registration with it.
        } catch (e: DataError) {
            if (!quiet) return failed(e)
        }
        store.clear()
        return PushSyncResult.Revoked
    }

    private fun failed(error: DataError): PushSyncResult {
        // A session that ended for good took the registration with it.
        if (error is DataError.SignedOut) store.clear()
        return PushSyncResult.Failed(error)
    }

    private fun isFresh(syncedAtMillis: Long) =
        clock.millis() - syncedAtMillis in 0 until refreshAfter.toMillis()

    private fun fingerprintOf(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
