package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError

@Immutable
data class BikesUiState(
    val scope: BikeScope = BikeScope.Public,
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
)

/** Keyset paging over `/bikes`: one page at a time, the cursor from the last answer. */
class BikesViewModel(private val repository: BikesRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(BikesUiState())
    val state: StateFlow<BikesUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        load(refresh = false)
    }

    fun selectScope(scope: BikeScope) {
        if (scope == state.value.scope) return
        mutableState.value = BikesUiState(scope = scope)
        load(refresh = false)
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
                val page = repository.bikes(current.scope, cursor)
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
        val scope = state.value.scope
        job = viewModelScope.launch {
            try {
                val page = repository.bikes(scope)
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
}
