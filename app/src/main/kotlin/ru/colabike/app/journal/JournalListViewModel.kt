package ru.colabike.app.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.JournalChange
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
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
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
        viewModelScope.launch {
            commentChanges.collect { change ->
                if (change.target.kind != CommentKind.Journal) return@collect
                pager.edit {
                    if (it.id.value == change.target.id)
                        it.copy(comments = (it.comments + change.delta).coerceAtLeast(0))
                    else it
                }
            }
        }
        if (source is JournalSource.OfBike) {
            // An entry written, changed or deleted in the editor: the list is the server's again.
            viewModelScope.launch {
                repository.changes.collect { change ->
                    val here =
                        when (change) {
                            is JournalChange.Saved -> change.entry.summary.bike.id == source.id
                            is JournalChange.Removed -> true
                        }
                    if (here) pager.refresh()
                }
            }
        }
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
