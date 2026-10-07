package ru.colabike.app

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.Page
import ru.colabike.core.model.Photo
import ru.colabike.core.model.toDraft

/**
 * The pictures of a journal entry as its author changes them (docs/adr/0022): from the entry's page
 * to the screen, pictures picked and sent, one removed after a question, an entry of someone else
 * with no way in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class JournalPhotosFlowTest {
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
                        title = "Замена цепи",
                        bike = BikeRef(BikeId("b-own"), "Мой трейл"),
                    ),
                photos = (1..2).map { n -> Photo("q$n", "https://example.test/q$n.jpg") },
                version = "\"e0\"",
            )
        }

    private val picker =
        PickedRegistry(listOf(Uri.parse("content://media/a"), Uri.parse("content://media/b")))

    private fun start(authors: Boolean = true, journal: FakeJournal? = null): FakeJournal {
        val fake =
            journal
                ?: FakeJournal(
                    pages = mapOf(null to Page(listOf(entry.summary), null)),
                    entries = mapOf("j-own" to if (authors) entry else entry.copy(version = null)),
                )
        val bikes =
            FakeBikes(mapOf(null to Page(listOf(own.summary), null))).also {
                it.details = mapOf("b-own" to own)
            }
        val dependencies =
            FakeDependencies(
                bikes = bikes,
                journal = fake,
                auth = FakeAuth(AuthState.SignedIn(account)),
            )
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = picker
            }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                ColaBikeTheme { ColaBikeApp(dependencies) }
            }
        }
        compose.waitForIdle()
        return fake
    }

    private fun openEntry() {
        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Журнал велосипеда").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journal:j-own").performClick()
        compose.waitForIdle()
    }

    private fun openPhotos() {
        openEntry()
        compose.onNodeWithTag("journal:photos").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `an entry of someone else has no way to its photos editor`() {
        start(authors = false)

        openEntry()

        compose.onNodeWithTag("journal:photos").assertDoesNotExist()
    }

    @Test
    fun `the photos open from the entry's page with the count and no cover`() {
        start()

        openPhotos()

        compose.onNodeWithTag("photos").assertIsDisplayed()
        compose.onNodeWithText("Фото: 2 из 8").assertIsDisplayed()
        compose.onNodeWithTag("photos:photo:q1").assertExists()
        // An entry has no cover to choose.
        compose.onNodeWithTag("photos:cover:q2").assertDoesNotExist()
        compose.onNodeWithTag("photos:is-cover:q1").assertDoesNotExist()
    }

    @Test
    fun `pictures picked go to the server and join the others`() {
        val journal = start()
        openPhotos()

        compose.onNodeWithTag("photos:add").performClick()
        compose.waitForIdle()

        assertThat(picker.launches).isEqualTo(1)
        assertThat(journal.uploaded).hasSize(2)
        compose.onNodeWithText("Фото: 4 из 8").assertIsDisplayed()
        compose.onNodeWithTag("photos:photo:jp-new-1").assertExists()
        compose.onNodeWithTag("photos:photo:jp-new-2").assertExists()
    }

    @Test
    fun `a refused picture stays with the reason and can be dropped`() {
        val journal = start()
        journal.writeError = DataError.Rejected(415, "unsupported_media_type", "Нужен JPEG.")
        picker.picked = listOf(Uri.parse("content://media/a"))
        openPhotos()

        compose.onNodeWithTag("photos:add").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Нужен JPEG.").assertIsDisplayed()
        compose.onNodeWithTag("photos:retry:1").assertDoesNotExist()
        compose.onNodeWithTag("photos:cancel:1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("photos:pending:1").assertDoesNotExist()
    }

    @Test
    fun `a photo is removed after the person confirms, and not before`() {
        val journal = start()
        openPhotos()

        compose.onNodeWithTag("photos:delete:q2").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Удалить фото?").assertIsDisplayed()
        compose.onNodeWithTag("photos:delete-cancel").performClick()
        compose.waitForIdle()
        assertThat(journal.removedPhotos).isEmpty()

        compose.onNodeWithTag("photos:delete:q2").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("photos:delete-confirm").performClick()
        compose.waitForIdle()

        assertThat(journal.removedPhotos).containsExactly("q2")
        compose.onNodeWithTag("photos:photo:q2").assertDoesNotExist()
        compose.onNodeWithText("Фото: 1 из 8").assertIsDisplayed()
    }

    @Test
    fun `an entry with eight photos has no room for another`() {
        val journal =
            FakeJournal(
                pages = mapOf(null to Page(listOf(entry.summary), null)),
                entries =
                    mapOf(
                        "j-own" to
                            entry.copy(
                                photos =
                                    (1..8).map { Photo("q$it", "https://example.test/q$it.jpg") }
                            )
                    ),
            )
        start(journal = journal)
        openPhotos()

        compose.onNodeWithTag("photos:add").assertIsNotEnabled()
        compose.onNodeWithText("Фото: 8 из 8").assertIsDisplayed()
    }

    @Test
    fun `the new photos are on the entry's page when the person goes back`() {
        start()
        openPhotos()
        compose.onNodeWithTag("photos:add").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("journal:photos").assertExists()
    }
}
