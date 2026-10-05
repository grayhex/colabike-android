package ru.colabike.app.intents

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.core.model.IntentsRepository
import ru.colabike.core.model.RideIntent

/** The two lists of "I want to ride": what the community published, and the person's own. */
enum class IntentSegment {
    Community,
    Mine,
}

@Immutable
data class IntentsUiState(
    val segment: IntentSegment = IntentSegment.Community,
    val page: PagedState<RideIntent> = PagedState(),
)

/**
 * Intentions page by page. Each segment is its own server list and a switch starts it over,
 * cancelling the request in flight. The list is read again whenever the screen comes back, so a
 * created, changed or cancelled intention shows as the server holds it.
 */
class IntentsViewModel(private val repository: IntentsRepository) : ViewModel() {
    private val segment = MutableStateFlow(IntentSegment.Community)
    private val pager =
        Pager<RideIntent, String>(viewModelScope, RideIntent::id) { cursor ->
            when (segment.value) {
                IntentSegment.Community -> repository.community(cursor)
                IntentSegment.Mine -> repository.own(cursor)
            }
        }

    val state: StateFlow<IntentsUiState> =
        combine(segment, pager.state, ::IntentsUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, IntentsUiState())

    init {
        pager.load()
    }

    fun select(value: IntentSegment) {
        if (value == segment.value) return
        segment.value = value
        pager.load()
    }

    fun refresh() = pager.refresh()

    private var seen = false

    /**
     * The screen is in front again. The first time the list is already on its way; any later time
     * (back from the form or a page) it is read again, so that it shows what the server now holds.
     */
    fun resumed() {
        if (seen) pager.refresh() else seen = true
    }

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()
}
