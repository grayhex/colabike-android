package ru.colabike.app.config

import androidx.compose.runtime.Immutable
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.colabike.core.model.AppConfig
import ru.colabike.core.model.AppConfigRefresh
import ru.colabike.core.model.AppConfigRepository
import ru.colabike.core.model.FeatureAvailability

/**
 * The config in force. [config] is never absent: before anything is known, or when nothing can be
 * known (first start, no network, a cache that cannot be read), it is [AppConfig.Builtin].
 */
@Immutable
data class AppConfigState(
    val config: AppConfig = AppConfig.Builtin,
    /** What is on the device has been read. The shell waits for this and no longer. */
    val loaded: Boolean = false,
    /** When the server last confirmed [config]; null for the built-in one. */
    val validatedAt: Instant? = null,
    /** A request is on its way. */
    val refreshing: Boolean = false,
    /** The last request came to nothing (no network, a fault): what is shown may be out of date. */
    val refreshFailed: Boolean = false,
) {
    val features: FeatureAvailability
        get() = config.features
}

/** What the rest of the app sees of the config: a state and a request to look again. */
interface AppConfigSource {
    val state: StateFlow<AppConfigState>

    /**
     * Asks the server again if the last attempt is older than a few minutes. Never waits, never
     * throws; what comes of it arrives through [state].
     */
    fun refreshIfStale()

    /**
     * Asks the server now, whatever the last attempt was (the person pressed "check again" on the
     * update screen). Same promises as above.
     */
    fun refreshNow()
}

/**
 * Keeps [AppConfigState] up to date. The cold start never waits on the network: the kept config is
 * read from the device (or the built-in one is used), the app starts on it, and the server is asked
 * afterwards, in the background. A new config arrives as one value: the texts, links, flags and
 * pictures of a revision are all there or the old revision is. A failed request changes nothing.
 */
class AppConfigController(
    private val repository: AppConfigRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val staleAfter: Duration = Duration.ofMinutes(10),
) : AppConfigSource {
    private val mutable = MutableStateFlow(AppConfigState())
    override val state: StateFlow<AppConfigState> = mutable.asStateFlow()

    private var refreshing: Job? = null
    private var lastAttempt: Instant? = null

    /**
     * Reads what the device has, then asks the server. Called once, at the start of the process.
     */
    fun start() {
        scope.launch {
            // The app waits for this to start, so it cannot be allowed to take long or to fail:
            // whatever goes wrong here is "nothing kept".
            val cached =
                try {
                    withTimeoutOrNull(READ_TIMEOUT_MS) { repository.cached() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            mutable.value =
                AppConfigState(
                    config = cached?.config ?: AppConfig.Builtin,
                    loaded = true,
                    validatedAt = cached?.validatedAt,
                )
            refresh()
        }
    }

    override fun refreshIfStale() {
        if (!mutable.value.loaded) return
        val last = lastAttempt
        if (last != null && Duration.between(last, Instant.now(clock)) < staleAfter) return
        refresh()
    }

    override fun refreshNow() {
        if (!mutable.value.loaded) return
        refresh()
    }

    /** One request at a time; a second call while one is on its way is the same request. */
    @Synchronized
    private fun refresh() {
        if (refreshing?.isActive == true) return
        lastAttempt = Instant.now(clock)
        mutable.value = mutable.value.copy(refreshing = true, refreshFailed = false)
        refreshing = scope.launch { apply(repository.refresh()) }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 2_000L
    }

    private fun apply(result: AppConfigRefresh) {
        when (result) {
            is AppConfigRefresh.Updated ->
                mutable.value =
                    AppConfigState(
                        config = result.stored.config,
                        loaded = true,
                        validatedAt = result.stored.validatedAt,
                    )
            is AppConfigRefresh.NotModified ->
                mutable.value =
                    mutable.value.copy(validatedAt = result.validatedAt, refreshing = false)
            // The old config, or the built-in one, stays in use; the next start or resume asks
            // again.
            is AppConfigRefresh.Failed ->
                mutable.value = mutable.value.copy(refreshing = false, refreshFailed = true)
        }
    }
}
