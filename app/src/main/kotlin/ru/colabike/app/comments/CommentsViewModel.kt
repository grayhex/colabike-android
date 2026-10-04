package ru.colabike.app.comments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.Comment
import ru.colabike.core.model.CommentRules
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.CommentsRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page

/** A root comment with the replies the screen has so far. */
@Immutable
data class CommentNode(
    val comment: Comment,
    val replies: List<Comment>,
    /** Every reply of the root is in [replies]. */
    val repliesComplete: Boolean,
    /** Where the next page of replies starts, once any have been asked for. */
    val repliesCursor: String? = null,
    val loadingReplies: Boolean = false,
    val repliesError: UiText? = null,
) {
    /** How many replies are not shown yet. */
    val hidden: Int
        get() = (comment.replyCount - replies.size).coerceAtLeast(0)
}

@Immutable
data class Composer(
    val text: String = "",
    /** The comment being answered. The answer goes under its root (replies are one level deep). */
    val replyTo: Comment? = null,
    /** The own comment being changed. */
    val editing: Comment? = null,
    val sending: Boolean = false,
    val error: UiText? = null,
    /**
     * The `Idempotency-Key` of the send in progress. It is kept when the send fails, so asking
     * again with the same text cannot make a second comment; any change of the text drops it.
     */
    val key: String? = null,
) {
    val length: Int
        get() = text.trim().length

    val canSend: Boolean
        get() = !sending && length in 1..CommentRules.MAX_LENGTH
}

@Immutable
data class CommentsUiState(
    val page: PagedState<CommentNode> = PagedState(),
    /** The comment the screen was opened at or has just written: only its branch is shown. */
    val focus: String? = null,
    /** From the root to the focused comment. */
    val focusPath: List<Comment> = emptyList(),
    val composer: Composer = Composer(),
    /** An own comment the person was asked about deleting. */
    val deleting: Comment? = null,
    /** A failure that is not the composer's: a delete, more replies. */
    val notice: UiText? = null,
)

/**
 * The discussion under one object. Root comments come oldest first with the first replies of each;
 * the rest of a root's replies are asked for on request. A [focus] (a deep link, or a comment just
 * written somewhere the list does not reach) shows only the branch it belongs to. The composer
 * writes, answers and edits; its text is kept as a [drafts] entry until it is sent.
 */
class CommentsViewModel(
    private val repository: CommentsRepository,
    private val drafts: CommentDrafts,
    private val target: CommentTarget,
    focus: String? = null,
) : ViewModel() {
    private val focusId = MutableStateFlow(focus)
    private val focusPath = MutableStateFlow<List<Comment>>(emptyList())
    private val composer = MutableStateFlow(Composer(text = drafts.get(target)))
    private val deleting = MutableStateFlow<Comment?>(null)
    private val notice = MutableStateFlow<UiText?>(null)

    private val pager =
        Pager<CommentNode, String>(viewModelScope, { it.comment.id }) { cursor ->
            val result = repository.threads(target, cursor, focus = focusId.value)
            focusPath.value = result.focusPath
            Page(
                result.items.map {
                    CommentNode(
                        comment = it.root,
                        replies = it.replies,
                        repliesComplete = it.root.replyCount <= it.replies.size,
                    )
                },
                result.nextCursor,
            )
        }

    val state: StateFlow<CommentsUiState> =
        combine(pager.state, focusId, focusPath, composer, combine(deleting, notice, ::Pair)) {
                page,
                focus,
                path,
                composer,
                (deleting, notice) ->
                CommentsUiState(page, focus, path, composer, deleting, notice)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CommentsUiState())

    init {
        pager.load()
    }

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()

    /** Back from one branch to the whole discussion. */
    fun showAll() {
        focusId.value = null
        focusPath.value = emptyList()
        pager.load()
    }

    private fun showBranch(id: String) {
        focusId.value = id
        pager.load()
    }

    // --- more replies ---------------------------------------------------------------------

    fun loadReplies(rootId: String) {
        val node = pager.state.value.items.firstOrNull { it.comment.id == rootId } ?: return
        if (node.loadingReplies || node.repliesComplete) return
        changeNode(rootId) { it.copy(loadingReplies = true, repliesError = null) }
        viewModelScope.launch {
            try {
                val page = repository.replies(target, rootId, cursor = node.repliesCursor)
                changeNode(rootId) {
                    val replies =
                        if (node.repliesCursor == null) page.items
                        else (it.replies + page.items).distinctBy { c -> c.id }
                    it.copy(
                        replies = replies,
                        repliesCursor = page.nextCursor,
                        repliesComplete = page.nextCursor == null,
                        loadingReplies = false,
                    )
                }
            } catch (e: DataError) {
                changeNode(rootId) { it.copy(loadingReplies = false, repliesError = e.toUiText()) }
            }
        }
    }

    private fun changeNode(rootId: String, transform: (CommentNode) -> CommentNode) {
        pager.update { nodes -> nodes.map { if (it.comment.id == rootId) transform(it) else it } }
    }

    // --- the composer ---------------------------------------------------------------------

    fun onText(text: String) {
        composer.update { it.copy(text = text, error = null, key = null) }
        if (composer.value.editing == null) drafts.put(target, text)
    }

    /**
     * Answers [comment]. An answer to an answer goes under the same root, so the addressee is named
     * in the text, as people do.
     */
    fun startReply(comment: Comment) {
        if (comment.deleted) return
        composer.update { current ->
            val mention = comment.author?.username?.takeIf { comment.parentId != null }
            // Starting an answer ends an edit; what was being written before it comes back.
            val typed = if (current.editing != null) drafts.get(target) else current.text
            val text =
                when {
                    typed.isNotBlank() -> typed
                    mention != null -> "@$mention "
                    else -> ""
                }
            Composer(text = text, replyTo = comment)
        }
        drafts.put(target, composer.value.text)
    }

    fun cancelReply() {
        composer.update { it.copy(replyTo = null, error = null, key = null) }
    }

    fun startEdit(comment: Comment) {
        val body = comment.body ?: return
        composer.value = Composer(text = body, editing = comment)
    }

    fun cancelEdit() {
        composer.value = Composer(text = drafts.get(target))
    }

    fun send() {
        val current = composer.value
        if (!current.canSend) return
        val key = current.key ?: UUID.randomUUID().toString()
        composer.value = current.copy(sending = true, error = null, key = key)
        viewModelScope.launch {
            try {
                val editing = current.editing
                if (editing != null) {
                    applyEdited(repository.edit(target, editing.id, current.text))
                    composer.value = Composer(text = drafts.get(target))
                } else {
                    val parent = current.replyTo?.let { it.parentId ?: it.id }
                    val created = repository.post(target, current.text, parent, key)
                    drafts.put(target, "")
                    composer.value = Composer()
                    applyCreated(created)
                }
            } catch (e: DataError) {
                composer.update { it.copy(sending = false, error = e.toUiText()) }
            }
        }
    }

    private fun applyCreated(created: Comment) {
        val parentId = created.parentId
        val nodes = pager.state.value.items
        if (parentId == null) {
            // At the end of the discussion only if the end has been reached; otherwise the new
            // comment is shown with its branch.
            if (focusId.value == null && pager.state.value.nextCursor == null) {
                pager.update { it + CommentNode(created, emptyList(), repliesComplete = true) }
            } else {
                showBranch(created.id)
            }
        } else {
            val node = nodes.firstOrNull { it.comment.id == parentId }
            if (node != null && node.repliesComplete) {
                changeNode(parentId) {
                    it.copy(
                        comment = it.comment.copy(replyCount = it.comment.replyCount + 1),
                        replies = it.replies + created,
                    )
                }
            } else {
                showBranch(created.id)
            }
        }
    }

    private fun applyEdited(edited: Comment) {
        pager.update { nodes ->
            nodes.map { node ->
                when {
                    node.comment.id == edited.id ->
                        node.copy(comment = edited.copy(replyCount = node.comment.replyCount))
                    node.replies.any { it.id == edited.id } ->
                        node.copy(
                            replies = node.replies.map { if (it.id == edited.id) edited else it }
                        )
                    else -> node
                }
            }
        }
    }

    // --- deleting -------------------------------------------------------------------------

    fun askDelete(comment: Comment) {
        deleting.value = comment
    }

    fun dismissDelete() {
        deleting.value = null
    }

    fun confirmDelete() {
        val comment = deleting.value ?: return
        deleting.value = null
        notice.value = null
        viewModelScope.launch {
            try {
                repository.delete(target, comment.id)
                applyDeleted(comment)
            } catch (e: DataError) {
                notice.value = e.toUiText()
            }
        }
    }

    fun dismissNotice() {
        notice.value = null
    }

    private fun applyDeleted(deleted: Comment) {
        pager.update { nodes ->
            nodes.mapNotNull { node ->
                when {
                    // A root with readable replies stays as a tombstone; one without goes.
                    node.comment.id == deleted.id ->
                        if (node.comment.replyCount > 0)
                            node.copy(
                                comment =
                                    node.comment.copy(deleted = true, body = null, author = null)
                            )
                        else null
                    node.replies.any { it.id == deleted.id } -> {
                        val replies = node.replies.filterNot { it.id == deleted.id }
                        val root =
                            node.comment.copy(
                                replyCount = (node.comment.replyCount - 1).coerceAtLeast(0)
                            )
                        if (root.deleted && root.replyCount == 0) null
                        else node.copy(comment = root, replies = replies)
                    }
                    else -> node
                }
            }
        }
        val current = composer.value
        if (current.replyTo?.id == deleted.id) composer.update { it.copy(replyTo = null) }
        if (current.editing?.id == deleted.id) composer.value = Composer(text = drafts.get(target))
    }
}
