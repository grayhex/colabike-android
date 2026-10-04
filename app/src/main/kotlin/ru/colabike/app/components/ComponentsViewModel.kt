package ru.colabike.app.components

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.core.model.ComponentFilters
import ru.colabike.core.model.ComponentModel
import ru.colabike.core.model.ComponentQuery
import ru.colabike.core.model.ComponentSort
import ru.colabike.core.model.ComponentsRepository
import ru.colabike.core.model.DataError

@Immutable
data class ComponentsUiState(
    /** What is typed in the search box: ahead of [query] by the debounce. */
    val typed: String = "",
    /** What the list was asked for. A new query starts a new list. */
    val query: ComponentQuery = ComponentQuery(),
    /** The categories and brands the server lists; null until they arrive (or if they cannot). */
    val filters: ComponentFilters? = null,
    val page: PagedState<ComponentModel> = PagedState(),
)

/**
 * The component catalog: keyset paging over `/component-models` with the server's own text search,
 * category, brand and sort. A new query cancels the request in flight and starts over with no
 * cursor, so the pages of the old query can never be appended to the new list (a cursor of one
 * order is a 400 in another). Categories and brands come from the server; if they cannot be loaded
 * the list still works and the filters are left out until the next refresh.
 */
class ComponentsViewModel(
    private val repository: ComponentsRepository,
    private val debounceMs: Long = DEBOUNCE_MS,
) : ViewModel() {
    private val typed = MutableStateFlow("")
    private val query = MutableStateFlow(ComponentQuery())
    private val filters = MutableStateFlow<ComponentFilters?>(null)
    private var typing: Job? = null
    private var filtersJob: Job? = null

    private val pager =
        Pager<ComponentModel, String>(viewModelScope, { it.id.value }) { cursor ->
            repository.page(query.value, cursor)
        }

    val state: StateFlow<ComponentsUiState> =
        combine(typed, query, filters, pager.state, ::ComponentsUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ComponentsUiState())

    init {
        pager.load()
        loadFilters()
    }

    /** The field follows every key at once, the list waits for a pause. */
    fun onSearchText(text: String) {
        typed.value = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(debounceMs)
            apply(query.value.copy(text = text.trim()))
        }
    }

    fun clearSearch() = onSearchText("")

    /** Picking the chosen one again lifts the filter. */
    fun selectCategory(category: String?) {
        apply(query.value.copy(category = category.takeUnless { it == query.value.category }))
    }

    fun selectBrand(brand: String?) {
        apply(query.value.copy(brand = brand.takeUnless { it == query.value.brand }))
    }

    fun selectSort(sort: ComponentSort) = apply(query.value.copy(sort = sort))

    /** Back to the whole catalog: no text, no filters, newest first. */
    fun clearFilters() {
        typing?.cancel()
        typed.value = ""
        apply(ComponentQuery())
    }

    fun refresh() {
        pager.refresh()
        if (filters.value == null) loadFilters()
    }

    fun retry() {
        pager.retry()
        if (filters.value == null) loadFilters()
    }

    fun loadMore() = pager.loadMore()

    private fun apply(next: ComponentQuery) {
        if (next == query.value) return
        query.value = next
        pager.load()
    }

    private fun loadFilters() {
        filtersJob?.cancel()
        filtersJob = viewModelScope.launch {
            try {
                filters.value = repository.filters()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataError) {
                // The list works without them; they come with the next refresh or retry.
                filters.value = null
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
