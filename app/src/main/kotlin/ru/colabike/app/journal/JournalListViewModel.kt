package ru.colabike.app.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalSummary

/** Whose entries a list shows. */
sealed interface JournalSource {
    /** The journal of one bike: published and public entries, and the owner's drafts. */
    data class OfBike(val id: BikeId) : JournalSource

    /** What the signed-in person saved. */
    data object Saved : JournalSource
}

/**
 * A page-by-page list of journal entries, for a bike or for the saved ones. An entry un-saved on
 * its own page leaves the saved list without loading it again.
 */
class JournalListViewModel(
    private val repository: JournalRepository,
    private val source: JournalSource,
) : ViewModel() {
    private val pager =
        Pager<JournalSummary, String>(viewModelScope, { it.id.value }) { cursor ->
            when (source) {
                is JournalSource.OfBike -> repository.ofBike(source.id, cursor)
                JournalSource.Saved -> repository.saved(cursor)
            }
        }

    val state: StateFlow<PagedState<JournalSummary>> = pager.state

    init {
        pager.load()
        if (source == JournalSource.Saved) {
            viewModelScope.launch {
                repository.savedChanges.collect { change ->
                    if (!change.saved) pager.removeWhere { it.id == change.id }
                }
            }
        }
    }

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()
}
