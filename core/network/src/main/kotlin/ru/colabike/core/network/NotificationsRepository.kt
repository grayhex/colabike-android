package ru.colabike.core.network

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.PersonalApi
import ru.colabike.api.models.Notification as NotificationDto
import ru.colabike.api.models.NotificationReadAllRequest
import ru.colabike.api.models.NotificationReadRequest
import ru.colabike.api.models.NotificationReadResult as NotificationReadResultDto
import ru.colabike.api.models.NotificationTarget as NotificationTargetDto
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ListingState
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationFilter
import ru.colabike.core.model.NotificationPage
import ru.colabike.core.model.NotificationReadResult
import ru.colabike.core.model.NotificationReason
import ru.colabike.core.model.NotificationTarget
import ru.colabike.core.model.NotificationsRepository

/**
 * The inbox of the signed-in person, its unread count and the marks that say a notification was
 * read. All of it needs a session; a guest asks for nothing. Reading the inbox marks nothing, and
 * what the server answers to a mark (how many are left) is the count, not a local guess.
 */
class NetworkNotificationsRepository(
    private val api: PersonalApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotificationsRepository {
    override suspend fun page(
        cursor: String?,
        limit: Int,
        filter: NotificationFilter,
    ): NotificationPage {
        val unread = if (filter.unreadOnly) PersonalApi.UnreadListNotifications._1 else null
        val category =
            filter.category?.let { wanted ->
                PersonalApi.CategoryListNotifications.entries.firstOrNull { it.value == wanted.key }
            }
        val page =
            apiCall(dispatcher) {
                api.listNotifications(
                    unread = unread,
                    category = category,
                    limit = limit,
                    cursor = cursor,
                )
            }
        return NotificationPage(
            items = page.items.map { it.toModel(media) },
            nextCursor = page.nextCursor,
            watermark = page.watermark,
        )
    }

    override suspend fun count(): NotificationCount {
        val count = apiCall(dispatcher) { api.getNotificationCount() }
        return NotificationCount(
            unread = count.unread.coerceAtLeast(0),
            capped = count.capped,
            watermark = count.watermark,
        )
    }

    override suspend fun markRead(id: String): NotificationReadResult {
        val uuid = id.toUuidOrNull() ?: throw DataError.NotFound()
        return apiCall(dispatcher) { api.markNotificationRead(uuid) }.toModel()
    }

    override suspend fun markRead(ids: List<String>): NotificationReadResult {
        val uuids = ids.mapNotNull { it.toUuidOrNull() }.distinct()
        if (uuids.isEmpty()) return NotificationReadResult(0, count())
        var marked = 0
        var last: NotificationReadResult? = null
        // The server takes at most 100 at a time, as many as fit on a screen.
        for (chunk in uuids.chunked(READ_BATCH)) {
            val result =
                apiCall(dispatcher) {
                        api.markNotificationsRead(NotificationReadRequest(ids = chunk))
                    }
                    .toModel()
            marked += result.marked
            last = result
        }
        return NotificationReadResult(marked, last!!.unread)
    }

    override suspend fun markAllRead(
        watermark: String,
        category: NotificationCategory?,
    ): NotificationReadResult {
        val wanted = category?.let { c ->
            NotificationReadAllRequest.Category.entries.firstOrNull { it.value == c.key }
        }
        var marked = 0
        var result: NotificationReadResult
        var rounds = 0
        // One request marks at most 10 000; a full one means there may be more under the mark.
        do {
            result =
                apiCall(dispatcher) {
                        api.markAllNotificationsRead(
                            NotificationReadAllRequest(watermark = watermark, category = wanted)
                        )
                    }
                    .toModel()
            marked += result.marked
            rounds++
        } while (result.marked >= NotificationsRepository.READ_ALL_LIMIT && rounds < MAX_ROUNDS)
        return NotificationReadResult(marked, result.unread)
    }

    private companion object {
        const val READ_BATCH = 100
        const val MAX_ROUNDS = 5
    }
}

private fun String.toUuidOrNull(): java.util.UUID? =
    try {
        java.util.UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun NotificationReadResultDto.toModel() =
    NotificationReadResult(
        marked = marked.coerceAtLeast(0),
        unread = NotificationCount(unread = unread.coerceAtLeast(0), capped = capped),
    )

private fun NotificationDto.toModel(media: MediaUrls) =
    AppNotification(
        id = id.toString(),
        kind = type,
        category = NotificationCategory.of(category),
        createdAt = Instant.from(createdAt),
        read = readAt != null,
        actor = actor?.toModel(media),
        target = target.toModel(),
        reasons =
            reasons
                .mapNotNull {
                    when (it) {
                        NotificationDto.Reasons.friend -> NotificationReason.Friend
                        NotificationDto.Reasons.nearby -> NotificationReason.Nearby
                        NotificationDto.Reasons.intent -> NotificationReason.Intent
                        else -> null
                    }
                }
                .toSet(),
    )

private fun NotificationTargetDto.toModel() =
    NotificationTarget(
        type = type,
        id = id.toString(),
        name = name,
        path = path,
        commentId = commentId?.toString(),
        occurrenceAt = occurrenceAt?.let { Instant.from(it) },
        agreementRevision = agreementRevision,
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
