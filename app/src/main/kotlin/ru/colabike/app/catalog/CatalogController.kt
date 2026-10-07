package ru.colabike.app.catalog

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
import ru.colabike.app.bikes.BikeLabels
import ru.colabike.core.model.BikeCatalog
import ru.colabike.core.model.CatalogCategory
import ru.colabike.core.model.CatalogOption
import ru.colabike.core.model.CatalogRefresh
import ru.colabike.core.model.ClassificationCatalog
import ru.colabike.core.model.ComponentCatalog
import ru.colabike.core.model.SiteCatalog
import ru.colabike.core.model.SiteCatalogRepository

/**
 * The stand-in for the site's dictionaries until a copy has been read (a first start with no
 * network): the keys the server takes and the words the app has for them. It is not a source of
 * values: it has no brands, no models and no names of parts, only what a form cannot do without.
 * Version 0 marks it, so nothing mistakes it for a copy of the site's.
 */
object BuiltinCatalog {
    val value: SiteCatalog by lazy {
        fun options(keys: List<String>, name: (String) -> String) = keys.map {
            CatalogOption(it, name(it))
        }
        SiteCatalog(
            version = 0,
            classification =
                ClassificationCatalog(
                    categories =
                        BikeCatalog.categories.map { key ->
                            CatalogCategory(
                                key = key,
                                name = BikeLabels.category(key).orEmpty().ifEmpty { key },
                                subtypes =
                                    options(
                                        BikeCatalog.subtypes[key].orEmpty(),
                                        BikeLabels::subtype,
                                    ),
                            )
                        },
                    suspensions = options(BikeCatalog.suspensions, BikeLabels::suspension),
                    constructions = options(BikeCatalog.constructions, BikeLabels::construction),
                    uses = options(BikeCatalog.uses, BikeLabels::use),
                    maxUses = BikeCatalog.MAX_USES,
                ),
            purposes = emptyList(),
            brands = emptyList(),
            manufacturers = emptyList(),
            sizes = listOf("XS", "S", "M", "L", "XL"),
            components = ComponentCatalog.dictionary,
        )
    }
}

/**
 * The dictionaries in force. [catalog] is never absent: before anything is known, or when nothing
 * can be known, it is [BuiltinCatalog]; [fromSite] says whether it is a copy of the site's.
 */
@Immutable
data class CatalogState(
    val catalog: SiteCatalog = BuiltinCatalog.value,
    /** What is on the device has been read (or there was nothing). */
    val loaded: Boolean = false,
    /** [catalog] is a copy of the site's own dictionaries, not the stand-in. */
    val fromSite: Boolean = false,
    /** When the server last confirmed [catalog]; null for the stand-in. */
    val validatedAt: Instant? = null,
    /** A request is on its way. */
    val refreshing: Boolean = false,
    /** The last request came to nothing: what is shown may be out of date. */
    val refreshFailed: Boolean = false,
)

/** What a screen sees of the dictionaries: a state, and a way to ask for them. */
interface CatalogSource {
    val state: StateFlow<CatalogState>

    /**
     * Reads the copy kept on the device (once) and asks the server if the last request is old. A
     * screen that uses the dictionaries calls this when it opens; it never waits and never throws,
     * and what comes of it arrives through [state].
     */
    fun load()

    /** Asks the server now, whatever the last request was (the person pressed "try again"). */
    fun refreshNow()
}

/**
 * Keeps [CatalogState] up to date. The dictionaries are asked for when a screen needs them, not at
 * the start of the app: the copy kept on the device is shown at once, the server is asked behind
 * it, and a new copy arrives as one value. A failed request changes nothing: a form that is being
 * filled in never loses its lists to a bad connection.
 */
class CatalogController(
    private val repository: SiteCatalogRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val staleAfter: Duration = Duration.ofMinutes(10),
) : CatalogSource {
    private val mutable = MutableStateFlow(CatalogState())
    override val state: StateFlow<CatalogState> = mutable.asStateFlow()

    private var reading: Job? = null
    private var refreshing: Job? = null
    private var lastAttempt: Instant? = null

    @Synchronized
    override fun load() {
        if (reading == null) {
            reading = scope.launch {
                val cached =
                    try {
                        withTimeoutOrNull(READ_TIMEOUT_MS) { repository.cached() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                mutable.value =
                    if (cached != null)
                        mutable.value.copy(
                            catalog = cached.catalog,
                            loaded = true,
                            fromSite = true,
                            validatedAt = cached.validatedAt,
                        )
                    else mutable.value.copy(loaded = true)
                refreshIfStale()
            }
        } else if (mutable.value.loaded) {
            refreshIfStale()
        }
    }

    override fun refreshNow() {
        if (mutable.value.loaded) refresh()
    }

    private fun refreshIfStale() {
        val last = lastAttempt
        if (last != null && Duration.between(last, Instant.now(clock)) < staleAfter) return
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

    private fun apply(result: CatalogRefresh) {
        when (result) {
            is CatalogRefresh.Updated ->
                mutable.value =
                    CatalogState(
                        catalog = result.stored.catalog,
                        loaded = true,
                        fromSite = true,
                        validatedAt = result.stored.validatedAt,
                    )
            is CatalogRefresh.NotModified ->
                mutable.value =
                    mutable.value.copy(validatedAt = result.validatedAt, refreshing = false)
            // The copy in use, or the stand-in, stays; the next screen that needs it asks again.
            is CatalogRefresh.Failed ->
                mutable.value = mutable.value.copy(refreshing = false, refreshFailed = true)
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 2_000L
    }
}
