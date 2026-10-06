package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.Page
import ru.colabike.core.model.toDraft

/**
 * The journal of a bike as its owner writes it (docs/adr/0022): from the bike's page to the form,
 * an entry written, changed and deleted, and the findings of a form that is not good.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class JournalEditorFlowTest {
    @get:Rule val compose = createComposeRule()

    private val own =
        PreviewData.bikeDetail.fromDraft(
            BikeId("b-own"),
            PreviewData.bikeDetail.toDraft().copy(name = "Мой трейл"),
            "\"v1\"",
        )

    private val entry =
        journalEntry(1).let {
            it.copy(
                summary =
                    it.summary.copy(
                        id = JournalId("j-own"),
                        kind = "service",
                        title = "Замена цепи",
                        status = JournalStatus.Published,
                        isPublic = true,
                        eventDate = LocalDate.parse("2026-09-14"),
                        mileageKm = 4200,
                        bike = BikeRef(BikeId("b-own"), "Мой трейл"),
                    ),
                version = "\"e0\"",
            )
        }

    private fun bikes() =
        FakeBikes(mapOf(null to Page(listOf(own.summary), null))).also {
            it.details = mapOf("b-own" to own)
        }

    private fun journal(own: Boolean = true) =
        FakeJournal(
            pages = mapOf(null to Page(listOf(entry.summary), null)),
            entries = mapOf("j-own" to if (own) entry else entry.copy(version = null)),
        )

    private fun start(bikes: FakeBikes = bikes(), journal: FakeJournal = journal()) {
        val dependencies =
            FakeDependencies(
                bikes = bikes,
                journal = journal,
                auth = FakeAuth(AuthState.SignedIn(account)),
            )
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun openBike() {
        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
    }

    private fun openEntry() {
        openBike()
        compose.onNodeWithText("Журнал велосипеда").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journal:j-own").performClick()
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag("journal-editor:$tag").performScrollTo().performTextInput(text)
    }

    /** Text typed into a field goes before what is there: the old text is cleared first. */
    private fun retype(tag: String, text: String) {
        val field = compose.onNodeWithTag("journal-editor:$tag").performScrollTo()
        field.performTextClearance()
        field.performTextInput(text)
    }

    @Test
    fun `someone else's bike has no way to write in its journal`() {
        start(FakeBikes())

        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bike:journal-new").assertDoesNotExist()
    }

    @Test
    fun `an entry is written from the bike's page and opens as its page`() {
        val journal = journal()
        start(journal = journal)
        openBike()

        compose.onNodeWithTag("bike:journal-new").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journal-editor").assertIsDisplayed()
        type("title", "Новая цепь")
        type("body", "Поставил KMC.")
        compose.onNodeWithTag("journal-editor:kind:service").performScrollTo().performClick()
        compose.onNodeWithTag("journal-editor:status:published").performScrollTo().performClick()
        compose.onNodeWithTag("journal-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        val (bike, draft, key) = journal.created.single()
        assertThat(bike.value).isEqualTo("b-own")
        assertThat(draft.title).isEqualTo("Новая цепь")
        assertThat(draft.kind).isEqualTo("service")
        assertThat(draft.status).isEqualTo(JournalStatus.Published)
        assertThat(key).isNotEmpty()
        compose.onNodeWithTag("journal-editor").assertDoesNotExist()
        compose.onNodeWithText("Новая цепь").assertIsDisplayed()
    }

    @Test
    fun `a published entry without a title says what is missing and sends nothing`() {
        val journal = journal()
        start(journal = journal)
        openBike()

        compose.onNodeWithTag("bike:journal-new").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journal-editor:status:published").performScrollTo().performClick()
        compose.onNodeWithTag("journal-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(journal.created).isEmpty()
        compose.onNodeWithText("Для публикации нужен заголовок.").assertExists()
        compose.onNodeWithText("Для публикации нужен текст.").assertExists()
    }

    @Test
    fun `the author changes an entry and its page shows the change`() {
        val journal = journal()
        start(journal = journal)
        openEntry()

        compose.onNodeWithTag("journal:edit").performClick()
        compose.waitForIdle()
        retype("title", "Замена цепи и кассеты")
        compose.onNodeWithTag("journal-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        val (id, patch, version) = journal.updated.single()
        assertThat(id.value).isEqualTo("j-own")
        assertThat(patch.title).isEqualTo("Замена цепи и кассеты")
        assertThat(version).isEqualTo("\"e0\"")
        compose.onNodeWithTag("journal-editor").assertDoesNotExist()
        compose.onNodeWithText("Замена цепи и кассеты").assertIsDisplayed()
    }

    @Test
    fun `an entry of someone else has no way to be changed`() {
        start(journal = journal(own = false))

        openEntry()

        compose.onNodeWithTag("journal:edit").assertDoesNotExist()
    }

    @Test
    fun `an entry is deleted after the question and the list no longer shows it`() {
        val journal = journal()
        start(journal = journal)
        openEntry()

        compose.onNodeWithTag("journal:edit").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journal-editor:delete").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Удалить запись?").assertIsDisplayed()
        compose.onNodeWithTag("journal-editor:delete-cancel").performClick()
        compose.waitForIdle()
        assertThat(journal.deleted).isEmpty()

        compose.onNodeWithTag("journal-editor:delete").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journal-editor:delete-confirm").performClick()
        compose.waitForIdle()

        assertThat(journal.deleted.single().value).isEqualTo("j-own")
        compose.onNodeWithTag("journal-editor").assertDoesNotExist()
        compose.onNodeWithTag("journal:j-own").assertDoesNotExist()
    }

    @Test
    fun `a refusal because of the address is told in words and the form stays`() {
        val journal = journal()
        journal.writeError = DataError.Rejected(403, "email_verification_required", "")
        start(journal = journal)
        openBike()

        compose.onNodeWithTag("bike:journal-new").performScrollTo().performClick()
        compose.waitForIdle()
        type("title", "Заголовок")
        type("body", "Текст")
        compose.onNodeWithTag("journal-editor:status:published").performScrollTo().performClick()
        compose.onNodeWithTag("journal-editor:public").performScrollTo().performClick()
        compose.onNodeWithTag("journal-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("journal-editor:refused").assertIsDisplayed()
        compose.onNodeWithTag("journal-editor").assertIsDisplayed()
    }
}
