package ru.colabike.app.feed

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.FeedRepository

@Immutable
data class FeedUiState(
    val filter: FeedFilter = FeedFilter.All,
    val page: PagedState<FeedItem> = PagedState(),
)

/**
 * The signed-in person's feed, page by page. The filter is the server's (`type`): a new filter
 * starts the list over and cancels the request in flight, so a page of the old filter can never be
 * appended to the new one.
 */
class FeedViewModel(
    private val feed: FeedRepository,
    bikes: BikesRepository,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) : ViewModel() {
    private val filter = MutableStateFlow(FeedFilter.All)
    private val pager =
        Pager(viewModelScope, FeedItem::key) { cursor -> feed.feed(filter.value, cursor) }

    val state: StateFlow<FeedUiState> =
        combine(filter, pager.state, ::FeedUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState())

    init {
        pager.load()
        // A comment written or removed on a bike or an entry changes its count on the card.
        viewModelScope.launch {
            commentChanges.collect { change ->
                pager.edit { item ->
                    when {
                        item is FeedItem.Bike &&
                            change.target.kind == CommentKind.Bike &&
                            item.bike.id.value == change.target.id ->
                            item.copy(
                                bike =
                                    item.bike.copy(
                                        comments =
                                            (item.bike.comments + change.delta).coerceAtLeast(0)
                                    )
                            )
                        item is FeedItem.Journal &&
                            change.target.kind == CommentKind.Journal &&
                            item.entry.id.value == change.target.id ->
                            item.copy(
                                entry =
                                    item.entry.copy(
                                        comments =
                                            (item.entry.comments + change.delta).coerceAtLeast(0)
                                    )
                            )
                        else -> item
                    }
                }
            }
        }
        // A like given on a bike's page shows on its card here without loading the feed again.
        viewModelScope.launch {
            bikes.likeChanges.collect { change ->
                pager.edit { item ->
                    if (item is FeedItem.Bike && item.bike.id == change.id)
                        item.copy(
                            bike =
                                item.bike.copy(
                                    liked = change.state.liked,
                                    likes = change.state.likes,
                                )
                        )
                    else item
                }
            }
        }
    }

    fun select(value: FeedFilter) {
        if (value == filter.value) return
        filter.value = value
        pager.load()
    }

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()
}
