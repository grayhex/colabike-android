package ru.colabike.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.bikes.PendingState
import ru.colabike.app.bikes.PhotoImportException
import ru.colabike.app.journal.JournalPhotosUiState
import ru.colabike.app.journal.JournalPhotosViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.Photo
import ru.colabike.core.model.PhotoRules

/** An entry of one's own as its author reads it: with a version and three photos. */
private fun photoEntry(version: String? = "\"e0\""): JournalEntry {
    val base = journalEntry(1)
    return base.copy(
        summary =
            base.summary.copy(
                id = JournalId("j-own"),
                bike = BikeRef(BikeId("b-own"), "Мой трейл"),
            ),
        photos = (1..3).map { Photo("q$it", "https://example.test/q$it.jpg") },
        version = version,
    )
}

class JournalPhotosViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val journal = FakeJournal(entries = mapOf("j-own" to photoEntry()))
    private val files = FakePhotoFiles()

    private fun photos(id: String = "j-own", files: FakePhotoFiles = this.files) =
        JournalPhotosViewModel(journal, JournalId(id), files)

    private fun JournalPhotosViewModel.ready() = state.value as JournalPhotosUiState.Ready

    @Test
    fun `the author's photos are listed, with room for the rest of eight`() = runTest {
        val vm = photos()

        assertThat(vm.ready().entry.photos.map { it.id }).containsExactly("q1", "q2", "q3")
        assertThat(vm.ready().slotsLeft).isEqualTo(PhotoRules.MAX_PER_ENTRY - 3)
    }

    @Test
    fun `an entry that is not the person's, or is gone, has no photos to change`() = runTest {
        journal.entries = mapOf("j-read" to photoEntry(version = null))

        assertThat(photos("j-read").state.value).isEqualTo(JournalPhotosUiState.Unavailable)
        assertThat(photos("nope").state.value).isEqualTo(JournalPhotosUiState.Unavailable)
    }

    @Test
    fun `a failure to read offers to try again`() = runTest {
        journal.nextError = DataError.Offline(java.io.IOException("down"))
        val vm = photos()

        assertThat(vm.state.value).isInstanceOf(JournalPhotosUiState.Failed::class.java)
        vm.load()
        assertThat(vm.state.value).isInstanceOf(JournalPhotosUiState.Ready::class.java)
    }

    @Test
    fun `pictures go one at a time, each under a key of its own, with no floor for the size`() =
        runTest {
            val vm = photos()

            vm.add(listOf("content://a", "content://b"))

            assertThat(journal.uploaded.map { it.first.value }).containsExactly("j-own", "j-own")
            assertThat(journal.uploaded.map { it.third }.toSet()).hasSize(2)
            // The server sets no minimum for the size of an entry's picture.
            assertThat(files.checks).containsExactly(false, false)
            assertThat(vm.ready().pending).isEmpty()
            assertThat(vm.ready().entry.photos.map { it.id })
                .containsExactly("q1", "q2", "q3", "jp-new-1", "jp-new-2")
                .inOrder()
            assertThat(files.discarded).hasSize(2)
        }

    @Test
    fun `no more pictures are taken than the entry has room for, and the rest is counted`() =
        runTest {
            val seven = (1..7).map { Photo("q$it", "https://example.test/q$it.jpg") }
            journal.entries = mapOf("j-own" to photoEntry().copy(photos = seven))
            val vm = photos()

            vm.add(listOf("content://a", "content://b", "content://c"))

            assertThat(files.imported).containsExactly("content://a")
            assertThat(vm.ready().skipped).isEqualTo(2)
            assertThat(vm.ready().entry.photos).hasSize(PhotoRules.MAX_PER_ENTRY)
            assertThat(vm.ready().slotsLeft).isEqualTo(0)
        }

    @Test
    fun `a lost connection keeps the picture and the retry sends it under the same key`() =
        runTest {
            val keys = mutableListOf<String>()
            journal.uploading = { key, _ -> keys += key }
            journal.writeError = DataError.Offline(java.io.IOException("down"))
            val vm = photos()

            vm.add(listOf("content://a"))

            val failed = vm.ready().pending.single().state as PendingState.Failed
            assertThat(failed.retry).isTrue()
            vm.retry(vm.ready().pending.single().id)

            assertThat(keys).hasSize(2)
            assertThat(keys[1]).isEqualTo(keys[0])
            assertThat(journal.uploaded.single().third).isEqualTo(keys[0])
            assertThat(vm.ready().pending).isEmpty()
            assertThat(vm.ready().entry.photos).hasSize(4)
        }

    @Test
    fun `a picture the server will not read stays with its reason and can only be dropped`() =
        runTest {
            journal.writeError = DataError.Rejected(415, "unsupported_media_type", "Нужен JPEG.")
            val vm = photos()

            vm.add(listOf("content://a"))
            val item = vm.ready().pending.single()
            vm.retry(item.id)

            val failed = item.state as PendingState.Failed
            assertThat(failed.retry).isFalse()
            assertThat(failed.message).isEqualTo(UiText.Plain("Нужен JPEG."))
            assertThat(journal.uploaded).isEmpty()
            vm.cancel(item.id)
            assertThat(vm.ready().pending).isEmpty()
        }

    @Test
    fun `a published entry without a confirmed address is told in its own words`() = runTest {
        journal.writeError = DataError.Rejected(403, "email_verification_required", "")
        val vm = photos()

        vm.add(listOf("content://a"))

        val failed = vm.ready().pending.single().state as PendingState.Failed
        assertThat(failed.message).isEqualTo(UiText.Res(R.string.journal_photos_needs_email))
        assertThat(failed.retry).isTrue()
    }

    @Test
    fun `a file that cannot be made is told by what is wrong with it, before any upload`() =
        runTest {
            val broken =
                FakePhotoFiles(mapOf("content://text" to PhotoImportException.Reason.Unreadable))
            val vm = photos(files = broken)

            vm.add(listOf("content://text"))

            val failed = vm.ready().pending.single().state as PendingState.Failed
            assertThat(failed.message).isEqualTo(UiText.Res(R.string.photos_import_unreadable))
            assertThat(journal.uploaded).isEmpty()
        }

    @Test
    fun `a picture still on its way is stopped by the person and nothing is left of it`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            journal.uploading = { _, _ -> gate.await() }
            val vm = photos()
            vm.add(listOf("content://a"))
            val item = vm.ready().pending.single().id

            vm.cancel(item)

            assertThat(vm.ready().pending).isEmpty()
            assertThat(files.discarded).hasSize(1)
            gate.complete(Unit)
            assertThat(journal.uploaded).isEmpty()
        }

    @Test
    fun `a photo is removed only after the person confirms`() = runTest {
        val vm = photos()

        vm.askDelete("q2")
        assertThat(vm.ready().confirmingDelete).isEqualTo("q2")
        vm.cancelDelete()
        assertThat(journal.removedPhotos).isEmpty()

        vm.askDelete("q2")
        vm.confirmDelete()

        assertThat(journal.removedPhotos).containsExactly("q2")
        assertThat(vm.ready().entry.photos.map { it.id }).containsExactly("q1", "q3").inOrder()
        assertThat(vm.ready().busy).isNull()
    }

    @Test
    fun `a refused removal keeps the photo and says why`() = runTest {
        journal.writeError = DataError.Offline(java.io.IOException("down"))
        val vm = photos()

        vm.askDelete("q2")
        vm.confirmDelete()

        assertThat(vm.ready().entry.photos).hasSize(3)
        assertThat(vm.ready().problem).isEqualTo(UiText.Res(R.string.error_offline))
    }

    @Test
    fun `an entry deleted elsewhere is no longer here`() = runTest {
        val vm = photos()

        journal.delete(JournalId("j-own"))

        assertThat(vm.state.value).isEqualTo(JournalPhotosUiState.Unavailable)
    }

    @Test
    fun `the files of what did not go are removed with the screen`() = runTest {
        journal.writeError = DataError.Offline(java.io.IOException("down"))
        val store = ViewModelStore()
        val vm =
            ViewModelProvider.create(
                    store,
                    viewModelFactory {
                        initializer { JournalPhotosViewModel(journal, JournalId("j-own"), files) }
                    },
                )[JournalPhotosViewModel::class]
        vm.add(listOf("content://a"))
        assertThat(files.discarded).isEmpty()

        store.clear()

        assertThat(files.discarded).hasSize(1)
    }
}
