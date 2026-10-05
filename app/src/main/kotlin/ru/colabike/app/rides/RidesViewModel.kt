package ru.colabike.app.rides

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Page
import ru.colabike.core.model.RideSummary
import ru.colabike.core.model.RidesRepository
import ru.colabike.core.model.UpcomingRide

/**
 * What a ride list holds: a public ride or plan, one of the viewer's own, or one of their plans.
 */
sealed interface RideRow {
    val ride: RideSummary

    data class Public(override val ride: RideSummary) : RideRow

    data class Own(val own: OwnRide) : RideRow {
        override val ride: RideSummary
            get() = own.ride
    }

    data class Plan(val plan: UpcomingRide) : RideRow {
        override val ride: RideSummary
            get() = plan.ride
    }

    /** Whether the ride has a page to open (a private or cancelled one has not). */
    val hasPage: Boolean
        get() =
            when (this) {
                is Public -> true
                is Own -> own.hasPage
                is Plan -> plan.hasPage
            }

    val key: String
        get() = ride.id.value
}

/** The lists of the Rides section. Each is its own server list: they are never mixed. */
enum class RideSegment(val personal: Boolean) {
    Upcoming(false),
    Completed(false),
    MyPlans(true),
    Mine(true),
}

@Immutable
data class RidesUiState(
    val segment: RideSegment = RideSegment.Upcoming,
    /** What is typed in the search box: ahead of [query] by the debounce. */
    val typed: String = "",
    /** The text the list was asked for; public lists only. */
    val query: String = "",
    val page: PagedState<RideRow> = PagedState(),
)

/**
 * The Rides section: ahead (plans), done (completed), and for a member their own plans and rides. A
 * new segment or search text starts a list over and cancels the request in flight. The personal
 * plan list has no cursor, so it is one request and never asks for a second page.
 */
class RidesViewModel(
    private val repository: RidesRepository,
    private val debounceMs: Long = DEBOUNCE_MS,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
    participationChanges: Flow<String> = emptyFlow(),
) : ViewModel() {
    private val segment = MutableStateFlow(RideSegment.Upcoming)
    private val typed = MutableStateFlow("")
    private val query = MutableStateFlow("")
    private var typing: Job? = null

    private val pager =
        Pager<RideRow, String>(viewModelScope, RideRow::key) { cursor ->
            val text = query.value.takeIf { it.isNotEmpty() }
            when (segment.value) {
                RideSegment.Upcoming -> repository.upcoming(text, cursor).map { RideRow.Public(it) }
                RideSegment.Completed ->
                    repository.completed(text, cursor).map { RideRow.Public(it) }
                RideSegment.Mine -> repository.mine(cursor).map { RideRow.Own(it) }
                RideSegment.MyPlans ->
                    Page(
                        if (cursor == null) repository.myUpcoming().map { RideRow.Plan(it) }
                        else emptyList(),
                        null,
                    )
            }
        }

    val state: StateFlow<RidesUiState> =
        combine(segment, typed, query, pager.state, ::RidesUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, RidesUiState())

    init {
        pager.load()
        // A comment written or removed on a ride's page changes its count in the list.
        viewModelScope.launch {
            commentChanges.collect { change ->
                if (change.target.kind != CommentKind.Ride) return@collect
                pager.edit { row -> row.withComments(change) }
            }
        }
        // An answer taken on a plan's page changes the viewer's part in it (a role, a count, a
        // reminder): the list shows what the server now says, not what it said before.
        viewModelScope.launch { participationChanges.collect { pager.refresh() } }
    }

    fun select(value: RideSegment) {
        if (value == segment.value) return
        typing?.cancel()
        segment.value = value
        // The search box belongs to the public lists: leaving it empty in the personal ones.
        if (value.personal) {
            typed.value = ""
            query.value = ""
        }
        pager.load()
    }

    /** The field follows every key at once, the list waits for a pause. */
    fun onSearchText(text: String) {
        if (segment.value.personal) return
        typed.value = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(debounceMs)
            val next = text.trim()
            if (next != query.value) {
                query.value = next
                pager.load()
            }
        }
    }

    fun clearSearch() = onSearchText("")

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}

private fun <T, R> Page<T>.map(transform: (T) -> R): Page<R> =
    Page(items.map(transform), nextCursor)

internal fun RideRow.withComments(change: CommentCountChange): RideRow {
    if (ride.id.value != change.target.id) return this
    val updated = ride.copy(comments = (ride.comments + change.delta).coerceAtLeast(0))
    return when (this) {
        is RideRow.Public -> copy(ride = updated)
        is RideRow.Own -> copy(own = own.copy(ride = updated))
        is RideRow.Plan -> copy(plan = plan.copy(ride = updated))
    }
}

/**
 * The completed rides of one bike, page by page, with the same search as the section's lists. Rides
 * of a bike are only the public completed ones; a private bike answers 404.
 */
class BikeRidesViewModel(
    private val repository: RidesRepository,
    private val bike: BikeId,
    private val debounceMs: Long = 400L,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) : ViewModel() {
    private val typed = MutableStateFlow("")
    private val query = MutableStateFlow("")
    private var typing: Job? = null
    private val pager =
        Pager<RideRow, String>(viewModelScope, RideRow::key) { cursor ->
            val page = repository.ofBike(bike, query.value.takeIf { it.isNotEmpty() }, cursor)
            Page(page.items.map { RideRow.Public(it) }, page.nextCursor)
        }

    val state: StateFlow<RidesUiState> =
        combine(typed, query, pager.state) { typed, query, page ->
                RidesUiState(RideSegment.Completed, typed, query, page)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, RidesUiState())

    init {
        pager.load()
        viewModelScope.launch {
            commentChanges.collect { change ->
                if (change.target.kind == CommentKind.Ride) pager.edit { it.withComments(change) }
            }
        }
    }

    fun onSearchText(text: String) {
        typed.value = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(debounceMs)
            val next = text.trim()
            if (next != query.value) {
                query.value = next
                pager.load()
            }
        }
    }

    fun clearSearch() = onSearchText("")

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()
}
