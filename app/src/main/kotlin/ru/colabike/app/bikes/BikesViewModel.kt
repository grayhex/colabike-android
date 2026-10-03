package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError

@Immutable
data class BikesUiState(
    /** What the list is for now (scope, text, categories). A new query starts a new list. */
    val query: BikeQuery = BikeQuery(),
    /** What the person has typed so far: ahead of [query] by the debounce. */
    val typed: String = "",
    val bikes: List<BikeSummary> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** The first page failed: the whole screen shows it. */
    val error: UiText? = null,
    /** A later page failed: the list stays, the end of it offers a retry of that page. */
    val moreError: UiText? = null,
    /** Pull-to-refresh failed: the old list stays, its top offers a retry of the refresh. */
    val refreshError: UiText? = null,
    val nextCursor: String? = null,
) {
    val scope: BikeScope
        get() = query.scope
}

/**
 * Keyset paging over `/bikes`: one page at a time, the cursor from the last answer. A new query
 * (scope, text, categories) cancels the request in flight and starts over with no cursor, so the
 * old query's pages can never be appended to the new one's list. Typing waits [debounceMs] after
 * the last key.
 */
class BikesViewModel(
    private val repository: BikesRepository,
    private val debounceMs: Long = DEBOUNCE_MS,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BikesUiState())
    val state: StateFlow<BikesUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var typing: Job? = null

    init {
        load(refresh = false)
        // A like given on the bike's page shows here without loading the list again.
        viewModelScope.launch {
            repository.likeChanges.collect { change ->
                mutableState.update { current ->
                    current.copy(
                        bikes =
                            current.bikes.map {
                                if (it.id == change.id)
                                    it.copy(liked = change.state.liked, likes = change.state.likes)
                                else it
                            }
                    )
                }
            }
        }
    }

    fun selectScope(scope: BikeScope) = apply(state.value.query.copy(scope = scope))

    fun toggleCategory(key: String) {
        val categories = state.value.query.categories
        apply(
            state.value.query.copy(
                categories = if (key in categories) categories - key else categories + key
            )
        )
    }

    /** Every key stroke: the field follows at once, the request waits for a pause. */
    fun onSearchText(text: String) {
        mutableState.update { it.copy(typed = text) }
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(debounceMs)
            apply(state.value.query.copy(text = text.trim()))
        }
    }

    /** Back to everything in the scope: no text, no categories. */
    fun clearFilters() {
        typing?.cancel()
        mutableState.update { it.copy(typed = "") }
        apply(state.value.query.copy(text = "", categories = emptySet()))
    }

    fun refresh() = load(refresh = true)

    fun retry() = load(refresh = false)

    fun loadMore() {
        val current = state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore || job?.isActive == true) return
        mutableState.update { it.copy(loadingMore = true, moreError = null) }
        job = viewModelScope.launch {
            try {
                val page = repository.bikes(current.query, cursor)
                mutableState.update {
                    it.copy(
                        bikes = (it.bikes + page.items).distinctBy(BikeSummary::id),
                        nextCursor = page.nextCursor,
                        loadingMore = false,
                    )
                }
            } catch (e: DataError) {
                mutableState.update { it.copy(loadingMore = false, moreError = e.toUiText()) }
            }
        }
    }

    private fun apply(query: BikeQuery) {
        if (query == state.value.query) return
        mutableState.value = BikesUiState(query = query, typed = state.value.typed)
        load(refresh = false)
    }

    private fun load(refresh: Boolean) {
        job?.cancel()
        mutableState.update {
            it.copy(
                loading = !refresh,
                refreshing = refresh,
                error = null,
                moreError = null,
                refreshError = null,
            )
        }
        val query = state.value.query
        job = viewModelScope.launch {
            try {
                val page = repository.bikes(query)
                mutableState.update {
                    it.copy(
                        bikes = page.items,
                        nextCursor = page.nextCursor,
                        loading = false,
                        refreshing = false,
                    )
                }
            } catch (e: DataError) {
                mutableState.update {
                    // A failed refresh keeps what is on screen.
                    if (refresh && it.bikes.isNotEmpty())
                        it.copy(refreshing = false, refreshError = e.toUiText())
                    else it.copy(loading = false, refreshing = false, error = e.toUiText())
                }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
