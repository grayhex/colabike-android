package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.DataError

/** The discussion as a person uses it: reading, writing, answering, changing, deleting. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class CommentsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val me = PreviewData.rider

    private fun discussion() = sampleDiscussion()

    private fun dependencies(
        comments: FakeComments = discussion(),
        signedIn: Boolean = true,
        journal: FakeJournal = FakeJournal(),
    ) =
        FakeDependencies(
            bikes = FakeBikes(),
            comments = comments,
            journal = journal,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )

    private fun start(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun openDiscussion() {
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Комментарии").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun write(text: String) {
        compose.onNodeWithTag("comments:field").performTextInput(text)
    }

    private fun send(label: String = "Отправить") {
        compose.onNodeWithContentDescription(label).performClick()
        compose.waitForIdle()
    }

    // --- reading ---------------------------------------------------------------------------

    @Test
    fun `the discussion shows roots with replies, a tombstone with its reply, and what is hidden`() {
        start(dependencies())

        openDiscussion()

        compose.onNodeWithText("Красивая рама").assertIsDisplayed()
        compose.onNodeWithText("Согласен").assertIsDisplayed()
        compose.onNodeWithText("Показать ещё ответы (1)").assertIsDisplayed()
        compose.onNodeWithTag("comments:list").performScrollToNode(hasText("Комментарий удалён"))
        compose.onNodeWithText("Комментарий удалён").assertIsDisplayed()
        compose.onNodeWithText("Ответ под удалённым").assertIsDisplayed()
    }

    @Test
    fun `more replies replace the preview and the button goes`() {
        start(dependencies())
        openDiscussion()

        compose.onNodeWithText("Показать ещё ответы (1)").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Третий ответ").assertIsDisplayed()
        compose.onNodeWithText("Показать ещё ответы (1)").assertDoesNotExist()
    }

    @Test
    fun `an empty discussion invites a member and a guest differently`() {
        start(dependencies(comments = FakeComments()))
        openDiscussion()
        compose.onNodeWithText("Комментариев пока нет").assertIsDisplayed()
        compose.onNodeWithText("Напишите первым.").assertIsDisplayed()
    }

    @Test
    fun `a hidden discussion offers a retry`() {
        val comments = discussion()
        comments.nextError = DataError.NotFound()
        start(dependencies(comments))

        openDiscussion()
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Красивая рама").assertIsDisplayed()
    }

    @Test
    fun `the floating bar steps aside for the box and comes back with Back`() {
        start(dependencies())
        openDiscussion()

        compose.onNode(hasText("Лента") and hasClickAction()).assertDoesNotExist()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Лента") and hasClickAction()).assertIsDisplayed()
    }

    // --- writing ---------------------------------------------------------------------------

    @Test
    fun `the send button waits for text`() {
        start(dependencies())
        openDiscussion()

        compose.onNodeWithContentDescription("Отправить").assertIsNotEnabled()
        write("Привет")
        compose.onNodeWithContentDescription("Отправить").assertIsEnabled()
    }

    @Test
    fun `a comment appears at the end and the bike page counts it`() {
        val comments = discussion()
        start(dependencies(comments))
        openDiscussion()

        write("Новый комментарий")
        send()

        assertThat(comments.posted.single().target.kind).isEqualTo(CommentKind.Bike)
        compose.onNodeWithTag("comments:list").performScrollToNode(hasText("Новый комментарий"))
        compose.onNodeWithText("Новый комментарий").assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("22 комментария").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `answering a comment names the addressee, sends under it, and closes the answer`() {
        val comments = discussion()
        start(dependencies(comments))
        openDiscussion()

        compose.onAllNodesWithText("Ответить")[1].performClick() // the reply "Согласен"
        compose.waitForIdle()
        compose.onNodeWithText("Ответ для Сосед").assertIsDisplayed()
        compose.onNodeWithTag("comments:field").assertIsDisplayed()
        write("Спасибо")
        send()

        assertThat(comments.posted.single().parentId).isEqualTo("r1")
        compose.onNodeWithText("Ответ для Сосед").assertDoesNotExist()
    }

    @Test
    fun `Back first puts away the answer and only then leaves`() {
        start(dependencies())
        openDiscussion()
        compose.onAllNodesWithText("Ответить")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ответ для Сосед").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Ответ для Сосед").assertDoesNotExist()
        compose.onNodeWithText("Красивая рама").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Красивая рама").assertDoesNotExist()
    }

    @Test
    fun `a failed send keeps the text and says why, and sending again sends the same key`() {
        val comments = discussion()
        comments.loseNextAnswer = true
        start(dependencies(comments))
        openDiscussion()
        write("Важный текст")

        send()

        compose.onNodeWithText("Важный текст").assertIsDisplayed()
        compose.onNode(hasText("Нет соединения", substring = true)).assertIsDisplayed()
        send()

        assertThat(comments.posted.map { it.key }.toSet()).hasSize(1)
        assertThat(comments.posted).hasSize(2)
        compose.onNodeWithTag("comments:list").performScrollToNode(hasText("Важный текст"))
        // One comment on the server: the list has it once.
        assertThat(compose.onAllNodesWithText("Важный текст").fetchSemanticsNodes()).hasSize(1)
    }

    @Test
    fun `unverified mail is told in words`() {
        val comments = discussion()
        comments.postError = DataError.Rejected(403, "email_verification_required", "x")
        start(dependencies(comments))
        openDiscussion()
        write("Текст")

        send()

        compose.onNode(hasText("Подтвердите почту на сайте", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `the unsent text is found again after leaving and coming back`() {
        val dependencies = dependencies()
        start(dependencies)
        openDiscussion()
        write("Недописанное")

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Комментарии").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Недописанное").assertIsDisplayed()
    }

    // --- one's own comments ---------------------------------------------------------------

    @Test
    fun `only one's own comments can be changed or deleted`() {
        start(dependencies())
        openDiscussion()

        // Own: the reply "Мой ответ" and the root "Мой вопрос о раме".
        assertThat(compose.onAllNodesWithText("Изменить").fetchSemanticsNodes()).hasSize(2)
        assertThat(compose.onAllNodesWithText("Удалить").fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun `an own comment is changed in the box and shows as changed`() {
        val comments = discussion()
        start(dependencies(comments))
        openDiscussion()

        compose.onAllNodesWithText("Изменить")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Правка комментария").assertIsDisplayed()
        compose.onNodeWithTag("comments:field").performTextReplacement("Исправленный ответ")
        send("Сохранить правку")

        assertThat(comments.edits).containsExactly("x2" to "Исправленный ответ")
        compose.onNodeWithText("Исправленный ответ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Правка комментария").assertDoesNotExist()
    }

    @Test
    fun `deleting asks first, no keeps it, yes removes it`() {
        val comments = discussion()
        start(dependencies(comments))
        openDiscussion()

        compose.onAllNodesWithText("Удалить")[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Удалить комментарий?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.waitForIdle()
        assertThat(comments.deletes).isEmpty()
        compose.onNodeWithText("Мой вопрос о раме").assertIsDisplayed()

        compose.onAllNodesWithText("Удалить")[1].performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertThat(comments.deletes).containsExactly("r2")
        compose.onNodeWithText("Мой вопрос о раме").assertDoesNotExist()
        compose.onNodeWithText("Красивая рама").assertIsDisplayed()
    }

    // --- a guest ---------------------------------------------------------------------------

    @Test
    fun `a guest reads, is asked to sign in to write, and nothing is sent`() {
        val comments = discussion()
        start(dependencies(comments, signedIn = false))
        openDiscussion()

        compose.onNodeWithText("Красивая рама").assertIsDisplayed()
        compose.onNodeWithText("Войдите, чтобы писать комментарии").assertIsDisplayed()
        compose.onNodeWithTag("comments:field").assertDoesNotExist()
        assertThat(compose.onAllNodesWithText("Изменить").fetchSemanticsNodes()).isEmpty()

        compose.onAllNodesWithText("Ответить")[0].performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(comments.posted).isEmpty()
    }

    // --- a journal entry ---------------------------------------------------------------

    @Test
    fun `an entry of the journal has its own discussion`() {
        val comments = discussion()
        start(dependencies(comments))
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Журнал велосипеда").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Запись 0", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Комментарии").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(comments.threadCalls.last().first)
            .isEqualTo(ru.colabike.core.model.CommentTarget(CommentKind.Journal, "j0"))
        compose.onNodeWithText("Красивая рама").assertIsDisplayed()
    }
}
