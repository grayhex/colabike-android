package ru.colabike.core.model

import java.time.Instant
import kotlinx.coroutines.flow.SharedFlow

/** What a discussion hangs on. One comments model serves all of them (api-v1, "Комментарии"). */
enum class CommentKind {
    Bike,
    Journal,
    Ride,
    Component,
}

data class CommentTarget(val kind: CommentKind, val id: String)

/**
 * A comment. A deleted, hidden or blocked author's comment that still has readable replies stays as
 * a tombstone: [deleted] is true and [body] and [author] are null.
 */
data class Comment(
    val id: String,
    /** The root comment this is a reply to, or null for a root; replies go one level deep. */
    val parentId: String?,
    val author: Person?,
    val body: String?,
    val createdAt: Instant,
    /** When the author last changed the text; null if never. */
    val editedAt: Instant?,
    val deleted: Boolean,
    /** Readable replies (roots only). */
    val replyCount: Int,
)

/** A root comment and the first replies of it (at most three), as the list gives them. */
data class CommentThread(val root: Comment, val replies: List<Comment>)

/**
 * A page of root comments, oldest first. With a `focus` the page is the one branch the comment
 * belongs to, [focusPath] runs from the root to it, and there are no more pages.
 */
data class CommentThreads(
    val items: List<CommentThread>,
    val nextCursor: String?,
    val focusPath: List<Comment>,
)

/** A comment that was added (+1) or removed (−1), for counters elsewhere to follow. */
data class CommentCountChange(val target: CommentTarget, val delta: Int)

object CommentRules {
    /** The longest comment after trimming (the API's limit). */
    const val MAX_LENGTH = 1_000
}

/**
 * Discussions under bikes, journal entries, rides and component models. Implementations throw
 * [DataError]; a missing, closed or hidden object is [DataError.NotFound] for all of them alike.
 */
interface CommentsRepository {
    /** Root comments, oldest first; [focus] asks for the branch of one comment instead. */
    suspend fun threads(
        target: CommentTarget,
        cursor: String? = null,
        limit: Int = 24,
        focus: String? = null,
    ): CommentThreads

    suspend fun replies(
        target: CommentTarget,
        commentId: String,
        cursor: String? = null,
        limit: Int = 24,
    ): Page<Comment>

    /**
     * Writes a comment, or a reply when [parentId] is set. [key] is the `Idempotency-Key`: sending
     * the same text with the same key again returns the first comment instead of a second one, so a
     * retry after a lost answer is safe; a new text needs a new key.
     */
    suspend fun post(target: CommentTarget, body: String, parentId: String?, key: String): Comment

    /** Changes the text of one's own comment; the last change wins (no `If-Match`). */
    suspend fun edit(target: CommentTarget, commentId: String, body: String): Comment

    /** Removes one's own comment; asking again is the same as asking once. */
    suspend fun delete(target: CommentTarget, commentId: String)

    /** Emitted after every comment that went through or was removed. */
    val countChanges: SharedFlow<CommentCountChange>
}
