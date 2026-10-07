package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentThread
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page

/**
 * The discussion on the page of a bike (issue #56): the first two root comments, the rest opened in
 * place and shut again to the two, replies under their parent, "Ещё" for a long text, the box to
 * write in at the end of the section, and a section that fails without taking the page with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class InlineDiscussionTest {
    @get:Rule val compose = createComposeRule()

    private fun dependencies(
        comments: FakeComments = sampleDiscussion(),
        signedIn: Boolean = true,
        bikes: FakeBikes = FakeBikes(),
    ) =
        FakeDependencies(
            bikes = bikes,
            comments = comments,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )

    private fun start(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun openBike(name: String = "Велосипед 1") {
        compose.onNodeWithContentDescription(name, substring = true).performClick()
        compose.waitForIdle()
    }

    private fun write(text: String) {
        compose.onNodeWithTag("comments:field").performScrollTo().performTextInput(text)
    }

    private fun send(label: String = "Отправить") {
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** The menu of one comment, by the comment's id. */
    private fun openMenu(id: String) {
        compose
            .onNode(
                hasContentDescription("Действия с комментарием") and
                    hasAnyAncestor(hasTestTag("comment:$id"))
            )
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
    }

    // --- reading ---------------------------------------------------------------------------

    @Test
    fun `the page opens with the count and the first two root comments`() {
        start(dependencies())
        openBike()

        // The page's own count: the heading says it as one thing for TalkBack.
        compose
            .onNodeWithContentDescription("Комментарии, 21")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Красивая рама").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Мой вопрос о раме").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Комментарий удалён").assertDoesNotExist()
        compose.onNodeWithTag("discussion:all").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("discussion:collapse").assertDoesNotExist()
    }

    @Test
    fun `all comments opens the rest in place, collapse goes back to the two and not to nothing`() {
        start(dependencies())
        openBike()

        compose.onNodeWithTag("discussion:all").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Комментарий удалён").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("discussion:all").assertDoesNotExist()

        compose.onNodeWithTag("discussion:collapse").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Комментарий удалён").assertDoesNotExist()
        compose.onNodeWithText("Красивая рама").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Мой вопрос о раме").assertIsDisplayed()
        compose.onNodeWithContentDescription("Комментарии, 21").assertIsDisplayed()
        compose.onNodeWithTag("discussion:all").assertIsDisplayed()
    }

    @Test
    fun `the rest of the root comments is asked for page by page`() {
        val first = sampleDiscussion()
        first.nextPage =
            mapOf(
                null to
                    ru.colabike.core.model.CommentThreads(
                        first.threads.take(2),
                        nextCursor = "c2",
                        focusPath = emptyList(),
                    ),
                "c2" to
                    ru.colabike.core.model.CommentThreads(
                        listOf(
                            CommentThread(commentOf("r9", body = "Из второй страницы"), emptyList())
                        ),
                        nextCursor = null,
                        focusPath = emptyList(),
                    ),
            )
        start(dependencies(first))
        openBike()

        compose.onNodeWithTag("discussion:all").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Из второй страницы").assertDoesNotExist()
        compose.onNodeWithTag("discussion:more").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Из второй страницы").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("discussion:more").assertDoesNotExist()
        assertThat(first.threadCalls.map { it.second }).containsExactly(null, "c2").inOrder()
    }

    @Test
    fun `replies open under their parent, the rest of them are asked for, and shut again`() {
        start(dependencies())
        openBike()
        compose.onNodeWithText("Согласен").assertDoesNotExist()

        compose.onNodeWithText("3 ответа").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Согласен").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Мой ответ").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Показать ещё ответы (1)").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Третий ответ").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Показать ещё ответы (1)").assertDoesNotExist()

        compose.onNodeWithText("3 ответа").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Согласен").assertDoesNotExist()
    }

    @Test
    fun `the author of the bike is marked among the answers`() {
        val author = PreviewData.bike.author!!
        val comments =
            FakeComments(
                threads =
                    listOf(
                        CommentThread(
                            commentOf("r1", body = "Красивая рама", replyCount = 1),
                            listOf(
                                commentOf(
                                    "x1",
                                    body = "Спасибо, сам собирал",
                                    parentId = "r1",
                                    author = author,
                                )
                            ),
                        )
                    )
            )
        start(dependencies(comments))
        openBike()

        compose.onNodeWithText("1 ответ").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Автор").performScrollTo().assertIsDisplayed()
    }

    // --- an empty discussion and a guest -----------------------------------------------------

    @Test
    fun `an empty discussion invites a member to write, with the box under it`() {
        start(dependencies(FakeComments()))
        openBike()

        compose
            .onNodeWithText("Начните обсуждение велосипеда")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("comments:field").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a guest reads, is asked to sign in to write or to answer, and nothing is sent`() {
        val comments = sampleDiscussion()
        start(dependencies(comments, signedIn = false))
        openBike()

        compose.onNodeWithText("Красивая рама").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("comments:field").assertDoesNotExist()
        compose.onNodeWithTag("discussion:sign-in").performScrollTo().assertIsDisplayed()

        compose.onAllNodesWithText("Ответить")[0].performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(comments.posted).isEmpty()
    }

    @Test
    fun `an empty discussion invites a guest to sign in first`() {
        start(dependencies(FakeComments(), signedIn = false))
        openBike()

        compose
            .onNodeWithText("Комментариев пока нет. Войдите, чтобы написать первым.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("discussion:sign-in").assertIsDisplayed()
    }

    // --- writing ---------------------------------------------------------------------------

    @Test
    fun `a comment is sent from the page, shows at once and the count follows`() {
        val comments = FakeComments()
        start(dependencies(comments))
        openBike()

        write("Привет из страницы")
        send()

        val posted = comments.posted.single()
        assertThat(posted.target.kind).isEqualTo(CommentKind.Bike)
        assertThat(posted.body).isEqualTo("Привет из страницы")
        compose.onNodeWithText("Привет из страницы").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Комментарии, 22").assertExists()
    }

    @Test
    fun `a new comment is shown even when the preview holds two others`() {
        val comments = sampleDiscussion()
        start(dependencies(comments))
        openBike()

        write("Третий корневой")
        send()

        compose.onNodeWithText("Третий корневой").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("discussion:collapse").assertExists()
    }

    @Test
    fun `answering names the addressee, sends under the root and opens its replies`() {
        val comments = sampleDiscussion()
        start(dependencies(comments))
        openBike()

        // The second root has no answers yet: the new one is added under it and shown there.
        compose.onAllNodesWithText("Ответить")[1].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ответ для Тестовый Райдер").performScrollTo().assertIsDisplayed()
        write("Спасибо")
        send()

        assertThat(comments.posted.single().parentId).isEqualTo("r2")
        compose.onNodeWithText("Ответ для Тестовый Райдер").assertDoesNotExist()
        compose.onNodeWithText("1 ответ").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Спасибо").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Back puts away the answer first and stays on the page`() {
        start(dependencies())
        openBike()
        compose.onAllNodesWithText("Ответить")[0].performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ответ для Сосед").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithText("Ответ для Сосед").assertDoesNotExist()
        compose.onNodeWithText("Красивая рама").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a failed send keeps the text and sending again sends the same key`() {
        val comments = sampleDiscussion()
        comments.loseNextAnswer = true
        start(dependencies(comments))
        openBike()
        write("Важный текст")

        send()

        compose.onNodeWithText("Важный текст").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Нет соединения", substring = true)).assertIsDisplayed()
        send()

        assertThat(comments.posted.map { it.key }.toSet()).hasSize(1)
        assertThat(comments.posted).hasSize(2)
        assertThat(compose.onAllNodesWithText("Важный текст").fetchSemanticsNodes()).hasSize(1)
    }

    @Test
    fun `the unsent text stays while the section opens and shuts, and after leaving the page`() {
        start(dependencies())
        openBike()
        write("Недописанное")

        compose.onNodeWithTag("discussion:all").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Недописанное").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("discussion:collapse").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Недописанное").performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        openBike()

        compose.onNodeWithText("Недописанное").performScrollTo().assertIsDisplayed()
    }

    // --- one's own comments, the others' --------------------------------------------------

    @Test
    fun `own comments are changed or deleted from their menu, another's is reported`() {
        start(dependencies())
        openBike()

        openMenu("r2")
        compose.onNodeWithText("Изменить").assertIsDisplayed()
        compose.onNodeWithText("Удалить").assertIsDisplayed()
        compose.onNodeWithText("Пожаловаться").assertDoesNotExist()
        Espresso.pressBack()
        compose.waitForIdle()

        openMenu("r1")
        compose.onNodeWithText("Пожаловаться").assertIsDisplayed()
        compose.onNodeWithText("Изменить").assertDoesNotExist()
    }

    @Test
    fun `an own comment is changed in the box and shows as changed`() {
        val comments = sampleDiscussion()
        start(dependencies(comments))
        openBike()

        openMenu("r2")
        compose.onNodeWithText("Изменить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Правка комментария").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("comments:field").performTextReplacement("Исправленный вопрос")
        send("Сохранить правку")

        assertThat(comments.edits).containsExactly("r2" to "Исправленный вопрос")
        compose.onNodeWithText("Исправленный вопрос", substring = true).performScrollTo()
        compose.onNodeWithText("Правка комментария").assertDoesNotExist()
    }

    @Test
    fun `deleting asks first, no keeps it, yes removes it`() {
        val comments = sampleDiscussion()
        start(dependencies(comments))
        openBike()

        openMenu("r2")
        compose.onNodeWithText("Удалить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Удалить комментарий?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.waitForIdle()
        assertThat(comments.deletes).isEmpty()
        compose.onNodeWithText("Мой вопрос о раме").performScrollTo().assertIsDisplayed()

        openMenu("r2")
        compose.onNodeWithText("Удалить").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Удалить") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertThat(comments.deletes).containsExactly("r2")
        compose.onNodeWithText("Мой вопрос о раме").assertDoesNotExist()
    }

    // --- what can go wrong --------------------------------------------------------------

    @Test
    fun `a section that fails does not take the page with it, and offers a retry`() {
        val comments = sampleDiscussion()
        comments.nextError = DataError.Offline(java.io.IOException())
        start(dependencies(comments))
        openBike()

        // The page of the bike is there: its name, its like, its rides.
        compose.onNodeWithText("Велосипед 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Нравится", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("discussion:error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("bike:rides").performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("discussion:error").performScrollTo()
        compose
            .onNode(hasText("Повторить") and hasAnyAncestor(hasTestTag("discussion:error")))
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Красивая рама").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("discussion:error").assertDoesNotExist()
    }

    @Test
    fun `a private bike has no discussion, no count, and none is asked for`() {
        val comments = sampleDiscussion()
        val private =
            PreviewData.bike.copy(
                id = BikeId("b-private"),
                name = "Закрытый",
                isOwner = true,
                isPublic = false,
            )
        start(dependencies(comments, bikes = FakeBikes(mapOf(null to Page(listOf(private), null)))))

        openBike("Закрытый")

        compose.onNodeWithText("Закрытый").assertIsDisplayed()
        compose.onNodeWithTag("discussion").assertDoesNotExist()
        compose.onNode(hasContentDescription("Комментарии,", substring = true)).assertDoesNotExist()
        compose.onNodeWithTag("bike:rides").assertDoesNotExist()
        assertThat(comments.threadCalls).isEmpty()
    }
}
