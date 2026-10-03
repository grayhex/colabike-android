package ru.colabike.app.search

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
import ru.colabike.core.model.BikeSearch
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.PersonSummary

enum class SearchTab {
    Bikes,
    People,
}

/** What one tab shows: nothing asked yet, a first page on its way, results, or why not. */
@Immutable
data class Results<T>(
    /** A search has been run; before that the tab explains what to type. */
    val asked: Boolean = false,
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    val moreError: UiText? = null,
)

@Immutable
data class SearchUiState(
    val tab: SearchTab = SearchTab.Bikes,
    /** What is typed now: ahead of the request by the debounce. */
    val typed: String = "",
    /** The text that was last sent. */
    val text: String = "",
    val category: String? = null,
    val suspension: String? = null,
    val electric: Boolean = false,
    val fatbike: Boolean = false,
    val bikes: Results<BikeSummary> = Results(),
    val people: Results<PersonSummary> = Results(),
) {
    /** The bike search as the server is asked for it; "no" for a facet is not a filter. */
    val bikeSearch: BikeSearch
        get() =
            BikeSearch(
                text = text,
                category = category,
                suspension = suspension,
                electric = electric.takeIf { it },
                fatbike = fatbike.takeIf { it },
            )
}

/**
 * One search box over the two things the API can search: builds (text and facets) and people (text
 * only). Each tab has its own job; a new condition cancels that tab's request in flight and starts
 * its list over with no cursor, so an old page can never be appended to a new search. Typing waits
 * [debounceMs] after the last key. A tab that is not on screen is searched when it is opened, if
 * its condition changed since.
 */
class SearchViewModel(
    private val bikes: BikesRepository,
    private val people: PeopleRepository,
    private val debounceMs: Long = DEBOUNCE_MS,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private var typing: Job? = null
    private var bikesJob: Job? = null
    private var peopleJob: Job? = null

    /** The condition each tab's current results answer; a tab is stale when it differs. */
    private var bikesAnswered: BikeSearch? = null
    private var peopleAnswered: String? = null

    fun selectTab(tab: SearchTab) {
        mutableState.update { it.copy(tab = tab) }
        refreshActive()
    }

    fun onText(text: String) {
        mutableState.update { it.copy(typed = text) }
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(debounceMs)
            mutableState.update { it.copy(text = text.trim()) }
            refreshActive()
        }
    }

    fun clearText() {
        typing?.cancel()
        mutableState.update { it.copy(typed = "", text = "") }
        refreshActive()
    }

    /** One family at a time; choosing the one chosen clears it. */
    fun selectCategory(key: String) = changeFacets {
        it.copy(category = key.takeIf { k -> k != it.category })
    }

    fun selectSuspension(key: String) = changeFacets {
        it.copy(suspension = key.takeIf { k -> k != it.suspension })
    }

    fun toggleElectric() = changeFacets { it.copy(electric = !it.electric) }

    fun toggleFatbike() = changeFacets { it.copy(fatbike = !it.fatbike) }

    fun retry() = refreshActive(force = true)

    fun loadMore() {
        val current = state.value
        when (current.tab) {
            SearchTab.Bikes -> {
                val cursor = current.bikes.nextCursor ?: return
                if (current.bikes.loading || current.bikes.loadingMore) return
                val query = current.bikeSearch
                mutableState.update {
                    it.copy(bikes = it.bikes.copy(loadingMore = true, moreError = null))
                }
                bikesJob = viewModelScope.launch {
                    try {
                        val page = bikes.search(query, cursor)
                        mutableState.update {
                            it.copy(bikes = it.bikes.append(page) { b -> b.id })
                        }
                    } catch (e: DataError) {
                        mutableState.update {
                            it.copy(
                                bikes = it.bikes.copy(loadingMore = false, moreError = e.toUiText())
                            )
                        }
                    }
                }
            }
            SearchTab.People -> {
                val cursor = current.people.nextCursor ?: return
                if (current.people.loading || current.people.loadingMore) return
                val text = current.text
                mutableState.update {
                    it.copy(people = it.people.copy(loadingMore = true, moreError = null))
                }
                peopleJob = viewModelScope.launch {
                    try {
                        val page = people.search(text, cursor)
                        mutableState.update {
                            it.copy(people = it.people.append(page) { p -> p.person.id })
                        }
                    } catch (e: DataError) {
                        mutableState.update {
                            it.copy(
                                people =
                                    it.people.copy(loadingMore = false, moreError = e.toUiText())
                            )
                        }
                    }
                }
            }
        }
    }

    private fun changeFacets(change: (SearchUiState) -> SearchUiState) {
        mutableState.update(change)
        refreshActive()
    }

    private fun refreshActive(force: Boolean = false) {
        when (state.value.tab) {
            SearchTab.Bikes -> searchBikes(force)
            SearchTab.People -> searchPeople(force)
        }
    }

    private fun searchBikes(force: Boolean) {
        val query = state.value.bikeSearch
        if (!force && query == bikesAnswered) return
        bikesJob?.cancel()
        bikesAnswered = query
        if (query.isEmpty) {
            mutableState.update { it.copy(bikes = Results()) }
            return
        }
        mutableState.update { it.copy(bikes = Results(asked = true, loading = true)) }
        bikesJob = viewModelScope.launch {
            try {
                val page = bikes.search(query)
                mutableState.update {
                    it.copy(
                        bikes =
                            Results(asked = true, items = page.items, nextCursor = page.nextCursor)
                    )
                }
            } catch (e: DataError) {
                bikesAnswered = null // asking again is allowed
                mutableState.update { it.copy(bikes = Results(asked = true, error = e.toUiText())) }
            }
        }
    }

    private fun searchPeople(force: Boolean) {
        val text = state.value.text
        if (!force && text == peopleAnswered) return
        peopleJob?.cancel()
        peopleAnswered = text
        if (text.isEmpty()) {
            mutableState.update { it.copy(people = Results()) }
            return
        }
        mutableState.update { it.copy(people = Results(asked = true, loading = true)) }
        peopleJob = viewModelScope.launch {
            try {
                val page = people.search(text)
                mutableState.update {
                    it.copy(
                        people =
                            Results(asked = true, items = page.items, nextCursor = page.nextCursor)
                    )
                }
            } catch (e: DataError) {
                peopleAnswered = null
                mutableState.update {
                    it.copy(people = Results(asked = true, error = e.toUiText()))
                }
            }
        }
    }

    private inline fun <T, K> Results<T>.append(page: Page<T>, key: (T) -> K): Results<T> =
        copy(
            items = (items + page.items).distinctBy(key),
            nextCursor = page.nextCursor,
            loadingMore = false,
        )

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
