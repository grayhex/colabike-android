package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.bikes.BikeJournalPreviewViewModel
import ru.colabike.app.bikes.JournalPreviewUiState
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalDraft
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.Page

/** The journal as the page of a bike shows it: the latest entry, kept up to date. */
class BikeJournalPreviewViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val bike = BikeId("b1")

    private fun BikeJournalPreviewViewModel.ready() = state.value as JournalPreviewUiState.Ready

    private val draft =
        JournalDraft(
            kind = "service",
            title = "Свежая запись",
            body = "Текст",
            status = JournalStatus.Published,
            isPublic = true,
            eventDate = null,
            mileageKm = null,
            installationResult = null,
            componentIds = emptyList(),
        )

    @Test
    fun `the first entry of the bike's journal is the preview`() = runTest {
        val journal = FakeJournal()

        val vm = BikeJournalPreviewViewModel(journal, bike)

        assertThat(vm.ready().latest?.id?.value).isEqualTo("j0")
        assertThat(journal.bikeCalls).containsExactly(bike to null)
    }

    @Test
    fun `a bike without entries has a preview with nothing in it`() = runTest {
        val vm =
            BikeJournalPreviewViewModel(
                FakeJournal(pages = mapOf(null to Page(emptyList(), null))),
                bike,
            )

        assertThat(vm.ready().latest).isNull()
    }

    @Test
    fun `a failure is the section's, and trying again reads it`() = runTest {
        val journal = FakeJournal()
        journal.nextError = DataError.Offline(java.io.IOException())

        val vm = BikeJournalPreviewViewModel(journal, bike)

        assertThat(vm.state.value)
            .isEqualTo(JournalPreviewUiState.Failed(UiText.Res(R.string.error_offline)))
        vm.load()
        assertThat(vm.ready().latest?.id?.value).isEqualTo("j0")
    }

    @Test
    fun `an entry written for this bike replaces the preview`() = runTest {
        val journal = FakeJournal()
        val vm = BikeJournalPreviewViewModel(journal, bike)

        journal.create(bike, draft, "key-1")

        assertThat(vm.ready().latest?.title).isEqualTo("Свежая запись")
    }

    @Test
    fun `an entry of another bike is not worth another request`() = runTest {
        val journal = FakeJournal()
        BikeJournalPreviewViewModel(journal, bike)
        val asked = journal.bikeCalls.size

        journal.create(BikeId("b-other"), draft, "key-1")

        assertThat(journal.bikeCalls).hasSize(asked)
    }

    @Test
    fun `a refresh that fails leaves the entry on the page`() = runTest {
        val journal = FakeJournal()
        val vm = BikeJournalPreviewViewModel(journal, bike)
        journal.nextError = DataError.Offline(java.io.IOException())

        journal.create(bike, draft, "key-1")

        assertThat(vm.ready().latest?.id?.value).isEqualTo("j0")
    }
}
