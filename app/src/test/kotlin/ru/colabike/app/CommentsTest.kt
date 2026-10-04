package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.comments.CommentDrafts
import ru.colabike.app.comments.CommentsViewModel
import ru.colabike.app.comments.InMemoryCommentDrafts
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.CommentThread
import ru.colabike.core.model.CommentThreads
import ru.colabike.core.model.DataError

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val target = CommentTarget(CommentKind.Bike, "b1")
    private val me = ru.colabike.core.designsystem.component.PreviewData.rider

    /** Two roots: `r1` has three replies, two of them in the preview; `r2` has none. */
    private val comments =
        FakeComments(
            threads =
                listOf(
                    CommentThread(
                        commentOf("r1", replyCount = 3),
                        listOf(
                            commentOf("x1", parentId = "r1"),
                            commentOf("x2", parentId = "r1", author = me),
                        ),
                    ),
                    CommentThread(commentOf("r2", author = me), emptyList()),
                ),
            moreReplies =
                mapOf(
                    "r1" to
                        listOf(
                            commentOf("x1", parentId = "r1"),
                            commentOf("x2", parentId = "r1", author = me),
                            commentOf("x3", parentId = "r1"),
                        )
                ),
        )
    private val drafts = InMemoryCommentDrafts()

    private fun vm(focus: String? = null, repository: FakeComments = comments) =
        CommentsViewModel(repository, drafts, target, focus)

    private fun CommentsViewModel.roots() = state.value.page.items.map { it.comment.id }

    // --- reading ---------------------------------------------------------------------------

    @Test
    fun `roots load with the preview of their replies and say how many are hidden`() = runTest {
        val vm = vm()

        val (first, second) = vm.state.value.page.items
        assertThat(first.replies.map { it.id }).containsExactly("x1", "x2").inOrder()
        assertThat(first.hidden).isEqualTo(1)
        assertThat(first.repliesComplete).isFalse()
        assertThat(second.repliesComplete).isTrue()
        assertThat(second.hidden).isEqualTo(0)
    }

    @Test
    fun `the rest of the replies is asked for on request and replaces the preview`() = runTest {
        val vm = vm()

        vm.loadReplies("r1")

        val node = vm.state.value.page.items.first()
        assertThat(node.replies.map { it.id }).containsExactly("x1", "x2", "x3").inOrder()
        assertThat(node.repliesComplete).isTrue()
        assertThat(node.loadingReplies).isFalse()
        vm.loadReplies("r1") // nothing more to ask
        assertThat(comments.replyCalls).containsExactly("r1" to null)
    }

    @Test
    fun `a failed request for replies keeps the preview and offers its own retry`() = runTest {
        val vm = vm()
        comments.nextError = DataError.Offline(java.io.IOException())

        vm.loadReplies("r1")

        val node = vm.state.value.page.items.first()
        assertThat(node.replies).hasSize(2)
        assertThat(node.repliesError).isEqualTo(UiText.Res(R.string.error_offline))
        vm.loadReplies("r1")
        assertThat(vm.state.value.page.items.first().replies).hasSize(3)
        assertThat(vm.state.value.page.items.first().repliesError).isNull()
    }

    @Test
    fun `a hidden discussion is not found, a failed one can be asked again`() = runTest {
        comments.nextError = DataError.NotFound()
        val vm = vm()
        assertThat(vm.state.value.page.error).isEqualTo(UiText.Res(R.string.error_not_found))

        vm.retry()

        assertThat(vm.state.value.page.error).isNull()
        assertThat(vm.roots()).containsExactly("r1", "r2").inOrder()
    }

    @Test
    fun `a deep link shows one branch with the way to it, and Show all brings the rest`() =
        runTest {
            val vm = vm(focus = "x2")

            assertThat(vm.state.value.focus).isEqualTo("x2")
            assertThat(vm.roots()).containsExactly("r1")
            assertThat(vm.state.value.focusPath.map { it.id }).containsExactly("r1", "x2").inOrder()
            assertThat(comments.threadCalls.last().third).isEqualTo("x2")

            vm.showAll()

            assertThat(vm.state.value.focus).isNull()
            assertThat(vm.state.value.focusPath).isEmpty()
            assertThat(vm.roots()).containsExactly("r1", "r2").inOrder()
            assertThat(comments.threadCalls.last().third).isNull()
        }

    @Test
    fun `later pages of roots are appended without repeating one`() = runTest {
        comments.nextPage =
            mapOf(
                null to CommentThreads(listOf(comments.threads[0]), "c1", emptyList()),
                "c1" to CommentThreads(comments.threads, null, emptyList()),
            )
        val vm = vm()

        vm.loadMore()

        assertThat(vm.roots()).containsExactly("r1", "r2").inOrder()
    }

    // --- writing ---------------------------------------------------------------------------

    @Test
    fun `a comment is sent trimmed with a key, shows at the end and the box is empty again`() =
        runTest {
            val vm = vm()

            vm.onText("  Отличный велосипед  ")
            vm.send()

            val sent = comments.posted.single()
            assertThat(sent.body)
                .isEqualTo("  Отличный велосипед  ") // trimming is the repository's
            assertThat(sent.parentId).isNull()
            assertThat(java.util.UUID.fromString(sent.key)).isNotNull()
            assertThat(vm.roots()).containsExactly("r1", "r2", "new-1").inOrder()
            assertThat(vm.state.value.composer.text).isEmpty()
            assertThat(vm.state.value.composer.sending).isFalse()
            assertThat(drafts.get(target)).isEmpty()
        }

    @Test
    fun `an answer to a root goes under it and counts one more`() = runTest {
        val vm = vm()
        val root = vm.state.value.page.items[1].comment

        vm.startReply(root)
        vm.onText("Согласен")
        vm.send()

        assertThat(comments.posted.single().parentId).isEqualTo("r2")
        val node = vm.state.value.page.items[1]
        assertThat(node.replies.map { it.id }).containsExactly("new-1")
        assertThat(node.comment.replyCount).isEqualTo(1)
        assertThat(vm.state.value.composer.replyTo).isNull()
    }

    @Test
    fun `an answer to an answer goes under the same root and names the addressee`() = runTest {
        val vm = vm()
        val reply = vm.state.value.page.items[0].replies[0]

        vm.startReply(reply)

        assertThat(vm.state.value.composer.text).isEqualTo("@neighbour ")
        assertThat(vm.state.value.composer.replyTo).isEqualTo(reply)
        vm.onText("@neighbour спасибо")
        vm.send()
        assertThat(comments.posted.single().parentId).isEqualTo("r1")
    }

    @Test
    fun `an answer under a root whose replies are not all here shows the new branch`() = runTest {
        val vm = vm()
        val root = vm.state.value.page.items[0].comment

        vm.startReply(root)
        vm.onText("Ответ")
        vm.send()

        // r1 has a reply the screen has not loaded: the new answer is shown with its branch.
        assertThat(vm.state.value.focus).isEqualTo("new-1")
        assertThat(comments.threadCalls.last().third).isEqualTo("new-1")
    }

    @Test
    fun `a comment written when the end of the list was not reached shows its branch`() = runTest {
        comments.nextPage = mapOf(null to CommentThreads(comments.threads, "c1", emptyList()))
        val vm = vm()

        vm.onText("Новый")
        vm.send()

        assertThat(vm.state.value.focus).isEqualTo("new-1")
    }

    @Test
    fun `nothing is sent that is empty or longer than allowed`() = runTest {
        val vm = vm()

        vm.onText("   ")
        vm.send()
        vm.onText("я".repeat(1_001))
        vm.send()

        assertThat(comments.posted).isEmpty()
        assertThat(vm.state.value.composer.canSend).isFalse()
        vm.onText("я".repeat(1_000))
        assertThat(vm.state.value.composer.canSend).isTrue()
    }

    @Test
    fun `a failed send keeps the text and the key, the retry cannot make a second comment`() =
        runTest {
            comments.loseNextAnswer = true
            val vm = vm()
            vm.onText("Текст")

            vm.send()

            val failed = vm.state.value.composer
            assertThat(failed.text).isEqualTo("Текст")
            assertThat(failed.error).isEqualTo(UiText.Res(R.string.error_offline))
            assertThat(failed.sending).isFalse()
            assertThat(vm.roots()).containsExactly("r1", "r2")
            val key = comments.posted.single().key

            vm.send()

            assertThat(comments.posted.map { it.key }).containsExactly(key, key)
            assertThat(vm.roots()).containsExactly("r1", "r2", "new-1").inOrder()
            assertThat(vm.state.value.composer.text).isEmpty()
        }

    @Test
    fun `changing the text after a failure is a new comment with a new key`() = runTest {
        comments.postError = DataError.Server(502, "req-1")
        val vm = vm()
        vm.onText("Первый вариант")
        vm.send()
        val first = comments.posted.single().key

        vm.onText("Второй вариант")
        vm.send()

        assertThat(comments.posted.map { it.key }.toSet()).hasSize(2)
        assertThat(comments.posted.last().key).isNotEqualTo(first)
    }

    @Test
    fun `unverified mail, a limit and a closed discussion are told in words and the text stays`() =
        runTest {
            val vm = vm()
            vm.onText("Текст")

            comments.postError =
                DataError.Rejected(403, "email_verification_required", "Подтвердите")
            vm.send()
            assertThat(vm.state.value.composer.error)
                .isEqualTo(UiText.Res(R.string.error_email_verification))

            comments.postError = DataError.RateLimited(300)
            vm.send()
            assertThat(vm.state.value.composer.error)
                .isEqualTo(UiText.Res(R.string.error_rate_limited, listOf(5)))

            comments.postError = DataError.NotFound()
            vm.send()
            assertThat(vm.state.value.composer.error)
                .isEqualTo(UiText.Res(R.string.error_not_found))
            assertThat(vm.state.value.composer.text).isEqualTo("Текст")
        }

    @Test
    fun `a double tap sends once`() = runTest {
        val vm = vm()
        vm.onText("Один раз")

        vm.send()
        vm.send()

        assertThat(comments.posted).hasSize(1)
    }

    // --- the draft -------------------------------------------------------------------------

    @Test
    fun `typed text is kept for the session and found again, an edit does not touch it`() =
        runTest {
            val vm = vm()
            vm.onText("Недописанное")

            val again = vm()

            assertThat(again.state.value.composer.text).isEqualTo("Недописанное")

            val own = again.state.value.page.items[1].comment
            again.startEdit(own)
            assertThat(again.state.value.composer.text).isEqualTo(own.body)
            again.onText("Правка")
            assertThat(drafts.get(target)).isEqualTo("Недописанное")
            again.cancelEdit()
            assertThat(again.state.value.composer.text).isEqualTo("Недописанное")
        }

    @Test
    fun `clearing the drafts leaves the next person an empty box`() = runTest {
        vm().onText("Личное")

        drafts.clear()

        assertThat(vm().state.value.composer.text).isEmpty()
    }

    // --- editing and deleting ------------------------------------------------------------------

    @Test
    fun `an own comment is changed in place and marked as changed`() = runTest {
        val vm = vm()
        val own = vm.state.value.page.items[0].replies[1]

        vm.startEdit(own)
        vm.onText("Новый текст")
        vm.send()

        val node = vm.state.value.page.items[0]
        assertThat(node.replies[1].body).isEqualTo("Новый текст")
        assertThat(node.replies[1].editedAt).isNotNull()
        assertThat(node.comment.replyCount).isEqualTo(3)
        assertThat(comments.edits).containsExactly("x2" to "Новый текст")
        assertThat(comments.posted).isEmpty()
        assertThat(vm.state.value.composer.editing).isNull()
    }

    @Test
    fun `a root keeps its reply count when it is edited`() = runTest {
        val vm = vm()
        val root = vm.state.value.page.items[1].comment

        vm.startEdit(root)
        vm.onText("Иначе")
        vm.send()

        assertThat(vm.state.value.page.items[1].comment.body).isEqualTo("Иначе")
    }

    @Test
    fun `a refused edit keeps the box in edit mode with the text`() = runTest {
        val vm = vm()
        val own = vm.state.value.page.items[0].replies[1]
        comments.editError = DataError.Rejected(403, "forbidden", "Чужой комментарий")

        vm.startEdit(own)
        vm.onText("Не получится")
        vm.send()

        val composer = vm.state.value.composer
        assertThat(composer.editing).isEqualTo(own)
        assertThat(composer.text).isEqualTo("Не получится")
        assertThat(composer.error).isEqualTo(UiText.Plain("Чужой комментарий"))
    }

    @Test
    fun `deleting asks first and then removes a comment without replies`() = runTest {
        val vm = vm()
        val own = vm.state.value.page.items[1].comment

        vm.askDelete(own)
        assertThat(vm.state.value.deleting).isEqualTo(own)
        assertThat(comments.deletes).isEmpty()
        vm.confirmDelete()

        assertThat(comments.deletes).containsExactly("r2")
        assertThat(vm.roots()).containsExactly("r1")
        assertThat(vm.state.value.deleting).isNull()
    }

    @Test
    fun `saying no to the question deletes nothing`() = runTest {
        val vm = vm()

        vm.askDelete(vm.state.value.page.items[1].comment)
        vm.dismissDelete()
        vm.confirmDelete()

        assertThat(comments.deletes).isEmpty()
        assertThat(vm.roots()).containsExactly("r1", "r2")
    }

    @Test
    fun `a root with replies stays as a tombstone, its replies stay`() = runTest {
        val repository =
            FakeComments(
                threads =
                    listOf(
                        CommentThread(
                            commentOf("r1", author = me, replyCount = 1),
                            listOf(commentOf("x1", parentId = "r1")),
                        )
                    )
            )
        val vm = vm(repository = repository)

        vm.askDelete(vm.state.value.page.items[0].comment)
        vm.confirmDelete()

        val node = vm.state.value.page.items.single()
        assertThat(node.comment.deleted).isTrue()
        assertThat(node.comment.body).isNull()
        assertThat(node.comment.author).isNull()
        assertThat(node.replies.map { it.id }).containsExactly("x1")
    }

    @Test
    fun `an own reply goes and the root counts one fewer, the last reply under a tombstone takes it along`() =
        runTest {
            val repository =
                FakeComments(
                    threads =
                        listOf(
                            CommentThread(
                                commentOf("r1", replyCount = 1),
                                listOf(commentOf("x1", parentId = "r1", author = me)),
                            ),
                            CommentThread(
                                commentOf("t1", deleted = true, replyCount = 1),
                                listOf(commentOf("y1", parentId = "t1", author = me)),
                            ),
                        )
                )
            val vm = vm(repository = repository)

            vm.askDelete(vm.state.value.page.items[0].replies[0])
            vm.confirmDelete()
            assertThat(vm.state.value.page.items[0].replies).isEmpty()
            assertThat(vm.state.value.page.items[0].comment.replyCount).isEqualTo(0)

            vm.askDelete(vm.state.value.page.items[1].replies[0])
            vm.confirmDelete()
            assertThat(vm.roots()).containsExactly("r1")
        }

    @Test
    fun `a refused delete keeps the comment and says why`() = runTest {
        val vm = vm()
        comments.deleteError = DataError.Server(502, "req-3")

        vm.askDelete(vm.state.value.page.items[1].comment)
        vm.confirmDelete()

        assertThat(vm.roots()).containsExactly("r1", "r2")
        assertThat(vm.state.value.notice)
            .isEqualTo(UiText.Res(R.string.error_server, listOf("req-3")))
        vm.dismissNotice()
        assertThat(vm.state.value.notice).isNull()
    }

    @Test
    fun `a comment cannot be answered once it is a tombstone`() = runTest {
        val repository =
            FakeComments(
                threads =
                    listOf(
                        CommentThread(commentOf("t1", deleted = true, replyCount = 1), emptyList())
                    )
            )
        val vm = vm(repository = repository)

        vm.startReply(vm.state.value.page.items[0].comment)

        assertThat(vm.state.value.composer.replyTo).isNull()
    }

    @Test
    fun `the counters elsewhere are told about every comment that came and went`() = runTest {
        val vm = vm()
        val seen = mutableListOf<CommentCountChange>()
        val job =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).let { scope
                ->
                scope.launch { comments.countChanges.collect { seen += it } }
            }
        vm.onText("Раз")
        vm.send()
        runCurrent()
        vm.askDelete(vm.state.value.page.items[1].comment)
        vm.confirmDelete()
        runCurrent()

        assertThat(seen.map { it.delta }).containsExactly(1, -1).inOrder()
        job.cancel()
    }
}

class CommentDraftsTest {
    private val drafts: CommentDrafts = InMemoryCommentDrafts()
    private val bike = CommentTarget(CommentKind.Bike, "b1")
    private val entry = CommentTarget(CommentKind.Journal, "b1")

    @Test
    fun `a draft belongs to its discussion`() {
        drafts.put(bike, "про велосипед")

        assertThat(drafts.get(bike)).isEqualTo("про велосипед")
        assertThat(drafts.get(entry)).isEmpty()
    }

    @Test
    fun `an empty text forgets the draft`() {
        drafts.put(bike, "что-то")
        drafts.put(bike, "  ")

        assertThat(drafts.get(bike)).isEmpty()
    }

    @Test
    fun `clear forgets everything`() {
        drafts.put(bike, "а")
        drafts.put(entry, "б")

        drafts.clear()

        assertThat(drafts.get(bike)).isEmpty()
        assertThat(drafts.get(entry)).isEmpty()
    }
}
