package ru.colabike.app.comments

import java.util.concurrent.ConcurrentHashMap
import ru.colabike.core.model.CommentTarget

/**
 * Text typed into a comment box and not sent yet, kept while the person walks around the app (to a
 * bike and back, to another section, a rotation) and not a moment longer than the session: it is
 * only in memory, never written anywhere, and [clear] runs when the person signs out so the next
 * one finds nothing.
 */
interface CommentDrafts {
    fun get(target: CommentTarget): String

    /** An empty text forgets the draft. */
    fun put(target: CommentTarget, text: String)

    fun clear()
}

class InMemoryCommentDrafts : CommentDrafts {
    private val drafts = ConcurrentHashMap<CommentTarget, String>()

    override fun get(target: CommentTarget): String = drafts[target].orEmpty()

    override fun put(target: CommentTarget, text: String) {
        if (text.isBlank()) drafts.remove(target) else drafts[target] = text
    }

    override fun clear() = drafts.clear()
}
