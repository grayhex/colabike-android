package ru.colabike.core.network

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.PersonalApi
import ru.colabike.api.models.Notification as NotificationDto
import ru.colabike.api.models.NotificationTarget as NotificationTargetDto
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.ListingState
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationTarget
import ru.colabike.core.model.NotificationsRepository
import ru.colabike.core.model.Page

/**
 * The inbox of the signed-in person and its unread count. Both need a session; a guest asks for
 * nothing. Nothing here writes: marking as read is not in API v1, and the count is the server's.
 */
class NetworkNotificationsRepository(
    private val api: PersonalApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotificationsRepository {
    override suspend fun page(cursor: String?, limit: Int): Page<AppNotification> {
        val page = apiCall(dispatcher) { api.listNotifications(limit = limit, cursor = cursor) }
        return Page(page.items.map { it.toModel(media) }, page.nextCursor)
    }

    override suspend fun count(): NotificationCount {
        val count = apiCall(dispatcher) { api.getNotificationCount() }
        return NotificationCount(unread = count.unread.coerceAtLeast(0), capped = count.capped)
    }
}

private fun NotificationDto.toModel(media: MediaUrls) =
    AppNotification(
        id = id.toString(),
        kind = type,
        createdAt = Instant.from(createdAt),
        read = readAt != null,
        actor = actor?.toModel(media),
        target = target.toModel(),
    )

private fun NotificationTargetDto.toModel() =
    NotificationTarget(
        type = type,
        id = id.toString(),
        name = name,
        path = path,
        expiresAt = expiresAt?.let { Instant.from(it) },
        state =
            state?.let {
                when (it.value) {
                    "closed" -> ListingState.Closed
                    "expired" -> ListingState.Expired
                    "expiring" -> ListingState.Expiring
                    "extended" -> ListingState.Extended
                    else -> ListingState.Unknown
                }
            },
    )
