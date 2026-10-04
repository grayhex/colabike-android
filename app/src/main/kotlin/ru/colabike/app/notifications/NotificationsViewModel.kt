package ru.colabike.app.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationFilter
import ru.colabike.core.model.NotificationsRepository
import ru.colabike.core.model.Page

/** What the inbox screen shows besides its list. */
@Immutable
data class InboxUi(
    val filter: NotificationFilter = NotificationFilter(),
    /**
     * The server's mark of the newest notification the person could see when the list was read;
     * "read all" goes up to it and no further. Null until the first page is in, and when the inbox
     * shows nothing.
     */
    val watermark: String? = null,
    /** "Read all" is on its way. */
    val readingAll: Boolean = false,
    /**
     * What failed in an action the person asked for (not in one made by opening a notification).
     */
    val message: UiText? = null,
)

/**
 * The inbox, newest first, and the marks that say a notification was read. A notification is marked
 * only when the person opens it, presses its button or asks for "read all", and only what the
 * server confirms is shown as read: a failed mark leaves the notification as it was. The server's
 * answer to every mark also carries the new unread count, which goes to the bell through [onCount]
 * (the bell never counts by itself).
 */
class NotificationsViewModel(
    private val repository: NotificationsRepository,
    private val onCount: (NotificationCount) -> Unit = {},
) : ViewModel() {
    private val mutableUi = MutableStateFlow(InboxUi())
    val ui: StateFlow<InboxUi> = mutableUi.asStateFlow()

    private val pager =
        Pager<AppNotification, String>(viewModelScope, { it.id }) { cursor ->
            val page = repository.page(cursor, filter = mutableUi.value.filter)
            if (cursor == null) mutableUi.update { it.copy(watermark = page.watermark) }
            Page(page.items, page.nextCursor)
        }

    val state: StateFlow<PagedState<AppNotification>> = pager.state

    /** The notifications whose mark is on its way, so that a second tap asks for nothing. */
    private val marking = mutableSetOf<String>()

    init {
        pager.load()
    }

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()

    fun toggleUnreadOnly() = narrow { it.copy(unreadOnly = !it.unreadOnly) }

    /** One category, or all of them when [category] is null (or already chosen). */
    fun selectCategory(category: NotificationCategory?) = narrow {
        it.copy(category = category.takeIf { chosen -> chosen != it.category })
    }

    private fun narrow(change: (NotificationFilter) -> NotificationFilter) {
        val next = change(mutableUi.value.filter)
        if (next == mutableUi.value.filter) return
        mutableUi.update { it.copy(filter = next, watermark = null, message = null) }
        pager.load()
    }

    /**
     * Marks [notification] read. [asked] is for the button: its failure is told. Opening a
     * notification marks it without a word (the person has gone to the object), and a mark that
     * failed then is made again the next time it is opened.
     */
    fun markRead(notification: AppNotification, asked: Boolean = false) {
        if (notification.read || !marking.add(notification.id)) return
        if (asked) mutableUi.update { it.copy(message = null) }
        viewModelScope.launch {
            try {
                val result = repository.markRead(notification.id)
                onCount(result.unread)
                pager.edit { if (it.id == notification.id) it.copy(read = true) else it }
            } catch (e: DataError) {
                if (asked) mutableUi.update { it.copy(message = e.toUiText()) }
            } finally {
                marking.remove(notification.id)
            }
        }
    }

    /** Marks everything the person has seen so far read, in the category chosen if there is one. */
    fun readAll() {
        val current = mutableUi.value
        val mark = current.watermark ?: return
        if (current.readingAll) return
        mutableUi.update { it.copy(readingAll = true, message = null) }
        viewModelScope.launch {
            try {
                val result = repository.markAllRead(mark, current.filter.category)
                onCount(result.unread)
                pager.refresh()
            } catch (e: DataError) {
                if (e is DataError.Rejected && e.status == 400) {
                    // The server no longer knows the mark (the notification it pointed at is gone,
                    // or it is another account's): take a fresh one with the list, and say so.
                    pager.refresh()
                    mutableUi.update {
                        it.copy(message = UiText.Res(R.string.notifications_read_all_stale))
                    }
                } else {
                    mutableUi.update { it.copy(message = e.toUiText()) }
                }
            } finally {
                mutableUi.update { it.copy(readingAll = false) }
            }
        }
    }

    fun dismissMessage() = mutableUi.update { it.copy(message = null) }
}

/**
 * The unread count for the bell. It is the server's number: it does not fall because the inbox was
 * opened, only when the server says so in answer to a mark ([update]). A failed request keeps the
 * last number, because a failing badge is not an error screen. Asks again at most once in
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

    /** The server's answer to a mark as read: the new count, ahead of the next request. */
    fun update(count: NotificationCount) {
        mutable.value = count
        askedAt = now()
    }

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
