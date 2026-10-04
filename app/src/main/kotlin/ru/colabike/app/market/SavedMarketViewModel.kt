package ru.colabike.app.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.core.model.ListingBrief
import ru.colabike.core.model.MarketRepository

/**
 * What the signed-in person saved and is still on the market. A listing un-saved on its own page
 * leaves the list without loading it again; one that was sold or expired is not listed at all.
 */
class SavedMarketViewModel(private val repository: MarketRepository) : ViewModel() {
    private val pager =
        Pager<ListingBrief, String>(viewModelScope, { it.id }) { cursor ->
            repository.saved(cursor)
        }

    val state: StateFlow<PagedState<ListingBrief>> = pager.state

    init {
        pager.load()
        viewModelScope.launch {
            repository.savedChanges.collect { change ->
                if (!change.saved) pager.removeWhere { it.id == change.id.value }
            }
        }
    }

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()
}
