package ru.colabike.app.ui

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page

/** What a list over a keyset-paged endpoint shows: the items so far and what is happening. */
@Immutable
data class PagedState<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    /** The first page is on its way. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** The first page failed: the whole screen shows it. */
    val error: UiText? = null,
    /** A later page failed: the list stays, its end offers a retry of that page. */
    val moreError: UiText? = null,
    /** A refresh failed: the old list stays, its top offers a retry of the refresh. */
    val refreshError: UiText? = null,
) {
    /** Nothing to show and nothing more to ask for. */
    val isEmpty: Boolean
        get() = !loading && error == null && items.isEmpty() && nextCursor == null
}

/**
 * Keyset paging for a ViewModel: one request at a time, the cursor from the last answer. [load]
 * starts the list over (cancelling whatever was in flight, so an old answer can never be appended
 * to a new list); [refresh] does the same but keeps the old list on a failure; [loadMore] asks for
 * the next page and keeps the list on a failure. A page that adds nothing new (every key already
 * shown) still moves the cursor on.
 */
class Pager<T, K : Any>(
    private val scope: CoroutineScope,
    private val keyOf: (T) -> K,
    private val fetch: suspend (cursor: String?) -> Page<T>,
) {
    private val mutable = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = mutable.asStateFlow()
    private var job: Job? = null

    fun load() {
        job?.cancel()
        mutable.value = PagedState()
        job = scope.launch {
            try {
                val page = fetch(null)
                mutable.value =
                    PagedState(
                        items = page.items.distinctBy(keyOf),
                        nextCursor = page.nextCursor,
                        loading = false,
                    )
                skipEmptyPages(0)
            } catch (e: DataError) {
                mutable.value = PagedState(loading = false, error = e.toUiText())
            }
        }
    }

    fun refresh() {
        val current = mutable.value
        if (current.loading) return load()
        job?.cancel()
        mutable.update {
            it.copy(refreshing = true, loadingMore = false, refreshError = null, moreError = null)
        }
        job = scope.launch {
            try {
                val page = fetch(null)
                mutable.value =
                    PagedState(
                        items = page.items.distinctBy(keyOf),
                        nextCursor = page.nextCursor,
                        loading = false,
                    )
                skipEmptyPages(0)
            } catch (e: DataError) {
                mutable.update { it.copy(refreshing = false, refreshError = e.toUiText()) }
            }
        }
    }

    /** Asks again for what failed: the first page, or the page after the last one shown. */
    fun retry() {
        val current = mutable.value
        when {
            current.error != null -> load()
            current.refreshError != null -> refresh()
            else -> loadMore()
        }
    }

    fun loadMore() = loadMore(0)

    private fun loadMore(skipped: Int) {
        val current = mutable.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.refreshing || current.loadingMore) return
        mutable.update { it.copy(loadingMore = true, moreError = null) }
        job = scope.launch {
            try {
                val page = fetch(cursor)
                mutable.update {
                    it.copy(
                        items = (it.items + page.items).distinctBy(keyOf),
                        nextCursor = page.nextCursor,
                        loadingMore = false,
                    )
                }
                skipEmptyPages(skipped)
            } catch (e: DataError) {
                mutable.update { it.copy(loadingMore = false, moreError = e.toUiText()) }
            }
        }
    }

    /**
     * A page can come back empty with more behind it (everything on it was left out as unknown or
     * hidden); an empty list must not stop there, since nothing on screen would ask for the next.
     */
    private fun skipEmptyPages(skipped: Int) {
        val now = mutable.value
        if (now.items.isEmpty() && now.nextCursor != null && skipped < MAX_EMPTY_PAGES) {
            loadMore(skipped + 1)
        }
    }

    /** Changes the items that [transform] touches, for a like or a save made elsewhere. */
    fun edit(transform: (T) -> T) {
        mutable.update { it.copy(items = it.items.map(transform)) }
    }

    /** Drops the items for which [predicate] holds (an entry that is no longer saved). */
    fun removeWhere(predicate: (T) -> Boolean) {
        mutable.update { it.copy(items = it.items.filterNot(predicate)) }
    }

    private companion object {
        const val MAX_EMPTY_PAGES = 5
    }
}
