package ru.colabike.app.rides

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.colabike.core.model.DataError
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideRoute
import ru.colabike.core.model.RidesRepository

@Immutable
sealed interface RidePreview {
    data object Loading : RidePreview

    data class Ready(val route: RideRoute) : RidePreview

    data object Unavailable : RidePreview
}

/**
 * A bounded, in-memory set of the current viewer's server-filtered routes. Only visible cards
 * request data. Leaving a card cancels queued/in-flight work; leaving the account discards the
 * whole owner. This does not retain descriptions, meeting points, participants or sensor data.
 */
class RidePreviews(
    private val rides: RidesRepository,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    private data class Cached(val preview: RidePreview, val at: Long)

    private val permits = Semaphore(2)
    private val jobs = mutableMapOf<RideId, Job>()
    private val watchers = mutableMapOf<RideId, Int>()
    private val cache = LinkedHashMap<RideId, Cached>(CAPACITY, 0.75f, true)
    private val mutable = MutableStateFlow<Map<RideId, RidePreview>>(emptyMap())
    val state: StateFlow<Map<RideId, RidePreview>> = mutable

    fun show(id: RideId) {
        watchers[id] = (watchers[id] ?: 0) + 1
        cache[id]
            ?.takeIf { nowMillis() - it.at < FRESH_MS }
            ?.let {
                mutable.value = mutable.value + (id to it.preview)
                return
            }
        if (id in jobs) return
        mutable.value = mutable.value + (id to RidePreview.Loading)
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = permits.withPermit {
                        val route = rides.ride(id).route
                        if (
                            route != null &&
                                route.lines.isNotEmpty() &&
                                route.lines.all { it.isNotEmpty() }
                        )
                            RidePreview.Ready(route)
                        else RidePreview.Unavailable
                    }
                    coroutineContext.ensureActive()
                    cache[id] = Cached(result, nowMillis())
                    while (cache.size > CAPACITY) cache.remove(cache.keys.first())
                    if (id in watchers) mutable.value = mutable.value + (id to result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: DataError) {
                    // The card remains useful with its title and metrics; a failed preview must not
                    // replace it with an error wall. Revisiting the card can retry.
                    if (id in watchers)
                        mutable.value = mutable.value + (id to RidePreview.Unavailable)
                } finally {
                    if (jobs[id] === coroutineContext[Job]) jobs.remove(id)
                }
            }
        jobs[id] = job
        job.start()
    }

    fun hide(id: RideId) {
        val remaining = (watchers[id] ?: 1) - 1
        if (remaining > 0) {
            watchers[id] = remaining
            return
        }
        watchers.remove(id)
        jobs.remove(id)?.cancel()
        mutable.value = mutable.value - id
    }

    fun clear() {
        jobs.values.toList().asReversed().forEach { it.cancel() }
        jobs.clear()
        watchers.clear()
        cache.clear()
        mutable.value = emptyMap()
    }

    /** Refresh visible previews without losing their composition subscriptions. */
    fun refresh() {
        val visible = watchers.toMap()
        clear()
        visible.forEach { (id, count) -> repeat(count) { show(id) } }
    }

    override fun onCleared() = clear()

    private companion object {
        const val CAPACITY = 20
        const val FRESH_MS = 60_000L
    }
}
