package ru.colabike.app.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationsRepository

/**
 * The inbox, newest first. It only reads: marking as read is not in API v1, so nothing here claims
 * a notification was read, and nothing changes the unread count by itself.
 */
class NotificationsViewModel(repository: NotificationsRepository) : ViewModel() {
    private val pager =
        Pager<AppNotification, String>(viewModelScope, { it.id }) { cursor ->
            repository.page(cursor)
        }

    val state: StateFlow<PagedState<AppNotification>> = pager.state

    init {
        pager.load()
    }

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()
}

/**
 * The unread count for the bell. It is the server's number, and it does not fall to zero because
 * the inbox was opened (that would be a lie: nothing was marked as read). A failed request keeps
 * the last number, because a failing badge is not an error screen. Asks again at most once in
 * [minIntervalMs] unless [refresh] is forced.
 */
class NotificationBadgeViewModel(
    private val repository: NotificationsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = 30_000,
) : ViewModel() {
    private val mutable = MutableStateFlow<NotificationCount?>(null)
    val count: StateFlow<NotificationCount?> = mutable.asStateFlow()
    private var askedAt: Long? = null
    private var asking = false

    fun refresh(force: Boolean = false) {
        val last = askedAt
        if (asking || (!force && last != null && now() - last < minIntervalMs)) return
        asking = true
        askedAt = now()
        viewModelScope.launch {
            try {
                mutable.value = repository.count()
            } catch (_: DataError) {
                // Keep what was shown; the next resume asks again.
                askedAt = null
            } finally {
                asking = false
            }
        }
    }
}

/** How the bell shows the count, from what the server said. */
@Immutable
sealed interface BellBadge {
    /** No badge: nothing unread, or not known yet. */
    data object None : BellBadge

    /** An exact number up to 99. */
    data class Exact(val unread: Int) : BellBadge

    /** 99 or more by the display, or the server only knows a floor: never an exact number. */
    data object Many : BellBadge
}

fun NotificationCount?.badge(): BellBadge =
    when {
        this == null -> BellBadge.None
        capped || unread > MAX_EXACT -> BellBadge.Many
        unread <= 0 -> BellBadge.None
        else -> BellBadge.Exact(unread)
    }

private const val MAX_EXACT = 99
