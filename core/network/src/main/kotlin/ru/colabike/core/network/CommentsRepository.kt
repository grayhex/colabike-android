package ru.colabike.core.network

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.colabike.api.apis.CommentsApi
import ru.colabike.api.models.CreateCommentRequest
import ru.colabike.api.models.EditCommentRequest
import ru.colabike.core.model.Comment
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentRules
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.CommentThreads
import ru.colabike.core.model.CommentsRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page

/**
 * Comments of bikes, journal entries, rides and component models: one set of operations over four
 * sets of paths. [api] is the client with the Bearer interceptor; [keyed] gives the same client
 * with an `Idempotency-Key` added, for the one request that needs it.
 */
class NetworkCommentsRepository(
    private val api: CommentsApi,
    private val keyed: (key: String) -> CommentsApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CommentsRepository {
    private val changes = MutableSharedFlow<CommentCountChange>(extraBufferCapacity = 16)
    override val countChanges: SharedFlow<CommentCountChange> = changes.asSharedFlow()

    override suspend fun threads(
        target: CommentTarget,
        cursor: String?,
        limit: Int,
        focus: String?,
    ): CommentThreads {
        val id = uuid(target.id)
        val focusId = focus?.let(::uuid)
        return apiCall(dispatcher) {
                when (target.kind) {
                    CommentKind.Bike -> api.listBikeComments(id, limit, cursor, focusId)
                    CommentKind.Journal -> api.listJournalComments(id, limit, cursor, focusId)
                    CommentKind.Ride -> api.listRideComments(id, limit, cursor, focusId)
                    CommentKind.Component -> api.listComponentComments(id, limit, cursor, focusId)
                }
            }
            .toModel(media)
    }

    override suspend fun replies(
        target: CommentTarget,
        commentId: String,
        cursor: String?,
        limit: Int,
    ): Page<Comment> {
        val id = uuid(target.id)
        val comment = uuid(commentId)
        return apiCall(dispatcher) {
                when (target.kind) {
                    CommentKind.Bike -> api.listBikeReplies(id, comment, limit, cursor)
                    CommentKind.Journal -> api.listJournalReplies(id, comment, limit, cursor)
                    CommentKind.Ride -> api.listRideReplies(id, comment, limit, cursor)
                    CommentKind.Component -> api.listComponentReplies(id, comment, limit, cursor)
                }
            }
            .toModel(media)
    }

    override suspend fun post(
        target: CommentTarget,
        body: String,
        parentId: String?,
        key: String,
    ): Comment {
        val id = uuid(target.id)
        val request = CreateCommentRequest(body = checked(body), parentId = parentId?.let(::uuid))
        // The key is a UUID in the API; anything else would be refused, so it is not sent at all.
        val client = keyed(uuid(key).toString())
        return apiCall(dispatcher) {
                when (target.kind) {
                    CommentKind.Bike -> client.createBikeComment(id, request)
                    CommentKind.Journal -> client.createJournalComment(id, request)
                    CommentKind.Ride -> client.createRideComment(id, request)
                    CommentKind.Component -> client.createComponentComment(id, request)
                }
            }
            .toModel(media)
            .also { changes.tryEmit(CommentCountChange(target, +1)) }
    }

    override suspend fun edit(target: CommentTarget, commentId: String, body: String): Comment {
        val id = uuid(target.id)
        val comment = uuid(commentId)
        val request = EditCommentRequest(body = checked(body))
        return apiCall(dispatcher) {
                when (target.kind) {
                    CommentKind.Bike -> api.editBikeComment(id, comment, request)
                    CommentKind.Journal -> api.editJournalComment(id, comment, request)
                    CommentKind.Ride -> api.editRideComment(id, comment, request)
                    CommentKind.Component -> api.editComponentComment(id, comment, request)
                }
            }
            .toModel(media)
    }

    override suspend fun delete(target: CommentTarget, commentId: String) {
        val id = uuid(target.id)
        val comment = uuid(commentId)
        apiCall(dispatcher) {
            when (target.kind) {
                CommentKind.Bike -> api.deleteBikeComment(id, comment)
                CommentKind.Journal -> api.deleteJournalComment(id, comment)
                CommentKind.Ride -> api.deleteRideComment(id, comment)
                CommentKind.Component -> api.deleteComponentComment(id, comment)
            }
        }
        changes.tryEmit(CommentCountChange(target, -1))
    }

    // A malformed id cannot name anything; the API would answer 404 as well.
    private fun uuid(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw DataError.NotFound()

    /** The text as it goes out: trimmed, 1 to 1000 characters, or no request at all. */
    private fun checked(body: String): String =
        body.trim().also {
            if (it.isEmpty() || it.length > CommentRules.MAX_LENGTH) {
                throw DataError.Rejected(400, "invalid_comment", "")
            }
        }
}
