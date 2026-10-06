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
import ru.colabike.app.bikes.BikePhotosUiState
import ru.colabike.app.bikes.BikePhotosViewModel
import ru.colabike.app.bikes.PendingPhoto
import ru.colabike.app.bikes.PendingState
import ru.colabike.app.bikes.PhotoImportException
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.BikeChange
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Photo
import ru.colabike.core.model.PhotoRules
import ru.colabike.core.model.toDraft

private val bikeWithPhotos =
    PreviewData.bikeDetail.fromDraft(BikeId("b-own"), PreviewData.bikeDetail.toDraft(), "\"v1\"")

class BikePhotosViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val bikes = FakeBikes().also { it.details = mapOf("b-own" to bikeWithPhotos) }
    private val files = FakePhotoFiles()

    private fun photos(id: String = "b-own", files: FakePhotoFiles = this.files) =
        BikePhotosViewModel(bikes, BikeId(id), files)

    private fun BikePhotosViewModel.ready() = state.value as BikePhotosUiState.Ready

    private fun BikePhotosViewModel.pending(): List<PendingPhoto> = ready().pending

    @Test
    fun `an own bike's photos are listed with its cover`() = runTest {
        val vm = photos()

        assertThat(vm.ready().bike.photos.map { it.id }).containsExactly("p1", "p2", "p3").inOrder()
        assertThat(vm.ready().bike.summary.cover?.id).isEqualTo("p1")
        assertThat(vm.ready().slotsLeft).isEqualTo(PhotoRules.MAX_PER_BIKE - 3)
    }

    @Test
    fun `someone else's bike and a missing one have no photos to change`() = runTest {
        bikes.details =
            mapOf(
                "b-other" to
                    bikeWithPhotos.copy(summary = bikeWithPhotos.summary.copy(isOwner = false))
            )

        assertThat(photos("b-other").state.value).isEqualTo(BikePhotosUiState.Unavailable)
        assertThat(photos("nobody").state.value).isEqualTo(BikePhotosUiState.Unavailable)
    }

    @Test
    fun `a failure to read offers to try again`() = runTest {
        bikes.nextError = DataError.Offline(java.io.IOException("down"))
        val vm = photos()

        assertThat(vm.state.value).isInstanceOf(BikePhotosUiState.Failed::class.java)
        vm.load()
        assertThat(vm.state.value).isInstanceOf(BikePhotosUiState.Ready::class.java)
    }

    @Test
    fun `what is picked goes to the server one picture at a time, each under a key of its own`() =
        runTest {
            val vm = photos()

            vm.add(listOf("content://a", "content://b"))

            assertThat(files.imported).containsExactly("content://a", "content://b").inOrder()
            assertThat(bikes.uploaded).hasSize(2)
            assertThat(bikes.uploaded.map { it.third }.toSet()).hasSize(2)
            assertThat(vm.pending()).isEmpty()
            assertThat(vm.ready().bike.photos.map { it.id })
                .containsExactly("p1", "p2", "p3", "ph-new-1", "ph-new-2")
                .inOrder()
            // The files were of use only for the transfer.
            assertThat(files.discarded).hasSize(2)
        }

    @Test
    fun `the first picture of a bike without any becomes its cover`() = runTest {
        bikes.details =
            mapOf(
                "b-own" to
                    bikeWithPhotos.copy(
                        photos = emptyList(),
                        summary = bikeWithPhotos.summary.copy(cover = null),
                    )
            )
        val vm = photos()

        vm.add(listOf("content://a", "content://b"))

        assertThat(vm.ready().bike.summary.cover?.id).isEqualTo("ph-new-1")
        assertThat(vm.ready().bike.photos).hasSize(2)
    }

    @Test
    fun `the pictures wait for their turn and the one that is sent shows its progress`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val keys = mutableListOf<String>()
        bikes.uploading = { key, progress ->
            keys += key
            progress(0.5f)
            if (keys.size == 1) gate.await()
        }
        val vm = photos()

        vm.add(listOf("content://a", "content://b"))

        assertThat(vm.pending().map { it.state })
            .containsExactly(PendingState.Sending(0.5f), PendingState.Waiting)
            .inOrder()
        assertThat(keys).hasSize(1)

        gate.complete(Unit)

        assertThat(keys).hasSize(2)
        assertThat(vm.pending()).isEmpty()
        assertThat(bikes.uploaded).hasSize(2)
    }

    @Test
    fun `a progress of a hundredth is not worth a new state`() = runTest {
        val gate = CompletableDeferred<Unit>()
        bikes.uploading = { _, progress ->
            progress(0.2f)
            progress(0.205f)
            gate.await()
        }
        val vm = photos()

        vm.add(listOf("content://a"))

        assertThat(vm.pending().single().state).isEqualTo(PendingState.Sending(0.2f))
        gate.complete(Unit)
    }

    @Test
    fun `a picture still on its way is stopped by the person and nothing is left of it`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            bikes.uploading = { _, _ -> gate.await() }
            val vm = photos()
            vm.add(listOf("content://a", "content://b"))
            val first = vm.pending().first().id

            vm.cancel(first)

            // The second one took the turn the first gave up.
            assertThat(vm.pending().map { it.id }).doesNotContain(first)
            assertThat(files.discarded).hasSize(1)
            gate.complete(Unit)
            assertThat(bikes.uploaded).hasSize(1)
            assertThat(vm.pending()).isEmpty()
        }

    @Test
    fun `a lost connection keeps the picture and the retry sends it under the same key`() =
        runTest {
            val keys = mutableListOf<String>()
            bikes.uploading = { key, _ -> keys += key }
            bikes.writeError = DataError.Offline(java.io.IOException("down"))
            val vm = photos()

            vm.add(listOf("content://a"))

            val failed = vm.pending().single().state as PendingState.Failed
            assertThat(failed.retry).isTrue()
            assertThat(failed.message).isEqualTo(UiText.Res(R.string.error_offline))
            assertThat(files.discarded).isEmpty()

            vm.retry(vm.pending().single().id)

            assertThat(keys).hasSize(2)
            assertThat(keys[1]).isEqualTo(keys[0])
            assertThat(bikes.uploaded.single().third).isEqualTo(keys[0])
            assertThat(vm.pending()).isEmpty()
            assertThat(vm.ready().bike.photos).hasSize(4)
        }

    @Test
    fun `a picture the server will not read stays with its reason and cannot be sent again`() =
        runTest {
            bikes.writeError = DataError.Rejected(415, "unsupported_media_type", "Нужен JPEG.")
            val vm = photos()

            vm.add(listOf("content://a"))
            val item = vm.pending().single()
            vm.retry(item.id)

            val failed = item.state as PendingState.Failed
            assertThat(failed.retry).isFalse()
            assertThat(failed.message).isEqualTo(UiText.Plain("Нужен JPEG."))
            assertThat(bikes.uploaded).isEmpty()
            assertThat(files.discarded).hasSize(1)

            vm.cancel(item.id)
            assertThat(vm.pending()).isEmpty()
        }

    @Test
    fun `an unconfirmed address is told in its own words and a retry is allowed`() = runTest {
        bikes.writeError = DataError.Rejected(403, "email_verification_required", "")
        val vm = photos()

        vm.add(listOf("content://a"))

        val failed = vm.pending().single().state as PendingState.Failed
        assertThat(failed.message).isEqualTo(UiText.Res(R.string.photos_needs_email))
        assertThat(failed.retry).isTrue()
    }

    @Test
    fun `a bike that is gone while a picture is sent is no longer there to change`() = runTest {
        bikes.writeError = DataError.NotFound()
        val vm = photos()

        vm.add(listOf("content://a"))

        assertThat(vm.state.value).isEqualTo(BikePhotosUiState.Unavailable)
        assertThat(files.discarded).hasSize(1)
    }

    @Test
    fun `a file that cannot be made is told by what is wrong with it, before any upload`() =
        runTest {
            val broken =
                FakePhotoFiles(
                    mapOf(
                        "content://small" to PhotoImportException.Reason.TooSmall,
                        "content://big" to PhotoImportException.Reason.TooLarge,
                        "content://text" to PhotoImportException.Reason.Unreadable,
                    )
                )
            val vm = photos(files = broken)

            vm.add(listOf("content://small", "content://big", "content://text"))

            val messages = vm.pending().map { (it.state as PendingState.Failed).message }
            assertThat(messages)
                .containsExactly(
                    UiText.Res(R.string.photos_import_too_small),
                    UiText.Res(R.string.photos_import_too_large),
                    UiText.Res(R.string.photos_import_unreadable),
                )
                .inOrder()
            assertThat(vm.pending().map { (it.state as PendingState.Failed).retry }.toSet())
                .containsExactly(false)
            assertThat(bikes.uploaded).isEmpty()
        }

    @Test
    fun `no more pictures are taken than the bike has room for, and the rest is counted`() =
        runTest {
            val eleven = (1..11).map { Photo("q$it", "https://example.test/q$it.jpg") }
            bikes.details = mapOf("b-own" to bikeWithPhotos.copy(photos = eleven))
            val vm = photos()

            vm.add(listOf("content://a", "content://b", "content://c"))

            assertThat(files.imported).containsExactly("content://a")
            assertThat(vm.ready().skipped).isEqualTo(2)
            assertThat(vm.ready().bike.photos).hasSize(PhotoRules.MAX_PER_BIKE)
            assertThat(vm.ready().slotsLeft).isEqualTo(0)

            vm.add(listOf("content://d"))

            assertThat(files.imported).hasSize(1)
        }

    @Test
    fun `a picture that did not go does not take a place from the next one`() = runTest {
        bikes.writeError = DataError.Offline(java.io.IOException("down"))
        val vm = photos()
        vm.add(listOf("content://a"))

        assertThat(vm.ready().occupied).isEqualTo(3)
        assertThat(vm.ready().slotsLeft).isEqualTo(PhotoRules.MAX_PER_BIKE - 3)
    }

    @Test
    fun `a photo made the cover goes first and is the cover`() = runTest {
        val vm = photos()

        vm.setCover("p3")

        assertThat(bikes.covers).containsExactly("p3")
        assertThat(vm.ready().bike.summary.cover?.id).isEqualTo("p3")
        assertThat(vm.ready().bike.photos.first().id).isEqualTo("p3")
        assertThat(vm.ready().busy).isNull()
    }

    @Test
    fun `the cover is not made the cover again`() = runTest {
        val vm = photos()

        vm.setCover("p1")

        assertThat(bikes.covers).isEmpty()
    }

    @Test
    fun `a refused cover says why and changes nothing`() = runTest {
        bikes.writeError = DataError.Rejected(403, "email_verification_required", "")
        val vm = photos()

        vm.setCover("p2")

        assertThat(vm.ready().problem).isEqualTo(UiText.Res(R.string.photos_needs_email))
        assertThat(vm.ready().bike.summary.cover?.id).isEqualTo("p1")
        assertThat(vm.ready().busy).isNull()
    }

    @Test
    fun `a photo is removed only after the person confirms`() = runTest {
        val vm = photos()

        vm.askDelete("p2")
        assertThat(vm.ready().confirmingDelete).isEqualTo("p2")
        vm.cancelDelete()
        assertThat(vm.ready().confirmingDelete).isNull()
        assertThat(bikes.removedPhotos).isEmpty()

        vm.askDelete("p2")
        vm.confirmDelete()

        assertThat(bikes.removedPhotos).containsExactly("p2")
        assertThat(vm.ready().bike.photos.map { it.id }).containsExactly("p1", "p3").inOrder()
        assertThat(vm.ready().confirmingDelete).isNull()
        assertThat(vm.ready().busy).isNull()
    }

    @Test
    fun `the cover removed goes to the earliest of the rest`() = runTest {
        val vm = photos()

        vm.askDelete("p1")
        vm.confirmDelete()

        assertThat(vm.ready().bike.summary.cover?.id).isEqualTo("p2")
    }

    @Test
    fun `a refused removal keeps the photo and says why`() = runTest {
        bikes.writeError = DataError.Offline(java.io.IOException("down"))
        val vm = photos()

        vm.askDelete("p2")
        vm.confirmDelete()

        assertThat(vm.ready().bike.photos).hasSize(3)
        assertThat(vm.ready().problem).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(vm.ready().busy).isNull()
    }

    @Test
    fun `a bike deleted elsewhere is no longer here`() = runTest {
        val vm = photos()

        bikes.delete(BikeId("b-own"))

        assertThat(vm.state.value).isEqualTo(BikePhotosUiState.Unavailable)
    }

    @Test
    fun `a bike saved elsewhere replaces the page and keeps what is on its way`() = runTest {
        val gate = CompletableDeferred<Unit>()
        bikes.uploading = { _, _ -> gate.await() }
        val vm = photos()
        vm.add(listOf("content://a"))

        bikes.update(
            BikeId("b-own"),
            ru.colabike.core.model.BikePatch(name = "Другое имя"),
            "\"v1\"",
        )

        assertThat(vm.ready().bike.summary.name).isEqualTo("Другое имя")
        assertThat(vm.pending()).hasSize(1)
        gate.complete(Unit)
    }

    @Test
    fun `the files of what did not go are removed with the screen`() = runTest {
        bikes.writeError = DataError.Offline(java.io.IOException("down"))
        val store = ViewModelStore()
        val vm =
            ViewModelProvider.create(
                    store,
                    viewModelFactory {
                        initializer { BikePhotosViewModel(bikes, BikeId("b-own"), files) }
                    },
                )[BikePhotosViewModel::class]
        vm.add(listOf("content://a"))
        assertThat(files.discarded).isEmpty()

        store.clear()

        assertThat(files.discarded).hasSize(1)
    }

    @Test
    fun `a change of the photos of this bike is read again, one of another bike is not`() =
        runTest {
            val vm = photos()
            val before = bikes.detailCalls

            bikes.announce(BikeChange.Photos(BikeId("another")))
            assertThat(bikes.detailCalls).isEqualTo(before)

            bikes.announce(BikeChange.Photos(BikeId("b-own")))
            assertThat(bikes.detailCalls).isEqualTo(before + 1)
            assertThat(vm.ready().bike.photos).hasSize(3)
        }
}
