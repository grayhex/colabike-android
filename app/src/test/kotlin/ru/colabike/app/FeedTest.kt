package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.feed.FeedViewModel
import ru.colabike.app.journal.JournalListViewModel
import ru.colabike.app.journal.JournalSource
import ru.colabike.app.journal.JournalUiState
import ru.colabike.app.journal.JournalViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.LikeChange
import ru.colabike.core.model.LikeState
import ru.colabike.core.model.Page

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val feed =
        FakeFeed(
            mapOf(
                null to Page(listOf(feedBike(0), feedJournal(1)), "c1"),
                "c1" to Page(listOf(feedJournal(1), feedJournal(2)), null),
            )
        )
    private val bikes = FakeBikes()

    private fun vm() = FeedViewModel(feed, bikes)

    @Test
    fun `the first page loads, the next is appended without repeating an item`() = runTest {
        val vm = vm()
        assertThat(vm.state.value.page.items.map { it.key })
            .containsExactly("bike:b0", "journal:j1")
            .inOrder()

        vm.loadMore()

        assertThat(vm.state.value.page.items.map { it.key })
            .containsExactly("bike:b0", "journal:j1", "journal:j2")
            .inOrder()
        assertThat(vm.state.value.page.nextCursor).isNull()
        vm.loadMore() // the last page: nothing more to ask
        assertThat(feed.calls).containsExactly(FeedFilter.All to null, FeedFilter.All to "c1")
    }

    @Test
    fun `a filter starts the list over and a late page of the old filter is not shown`() = runTest {
        val late = CompletableDeferred<Page<FeedItem>>()
        feed.answer = { filter, cursor ->
            if (filter == FeedFilter.All && cursor == "c1") late.await()
            else if (filter == FeedFilter.Rides) Page(listOf(feedJournal(7)), null)
            else feed.pages.getValue(cursor)
        }
        val vm = vm()

        vm.loadMore() // the second page of "all" is on its way ...
        vm.select(FeedFilter.Rides) // ... when the filter changes
        late.complete(Page(listOf(feedJournal(2)), null))
        runCurrent()

        assertThat(vm.state.value.filter).isEqualTo(FeedFilter.Rides)
        assertThat(vm.state.value.page.items.map { it.key }).containsExactly("journal:j7")
        assertThat(vm.state.value.page.nextCursor).isNull()
        assertThat(vm.state.value.page.loadingMore).isFalse()
    }

    @Test
    fun `choosing the filter that is chosen asks nothing`() = runTest {
        val vm = vm()
        val before = feed.calls.size

        vm.select(FeedFilter.All)

        assertThat(feed.calls).hasSize(before)
    }

    @Test
    fun `a failed first page is a screen error and retrying asks for the same filter`() = runTest {
        feed.nextError = DataError.Offline(java.io.IOException())
        val vm = vm()
        assertThat(vm.state.value.page.error).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(vm.state.value.page.items).isEmpty()

        vm.retry()

        assertThat(vm.state.value.page.error).isNull()
        assertThat(vm.state.value.page.items).hasSize(2)
    }

    @Test
    fun `a failed next page keeps the list and retrying asks for that page`() = runTest {
        val vm = vm()
        feed.nextError = DataError.Server(502, "req-1")

        vm.loadMore()

        assertThat(vm.state.value.page.items).hasSize(2)
        assertThat(vm.state.value.page.moreError)
            .isEqualTo(UiText.Res(R.string.error_server, listOf("req-1")))
        vm.retry()
        assertThat(vm.state.value.page.items).hasSize(3)
        assertThat(vm.state.value.page.moreError).isNull()
    }

    @Test
    fun `a failed refresh keeps the list and is retried as a refresh`() = runTest {
        val vm = vm()
        feed.nextError = DataError.Offline(java.io.IOException())

        vm.refresh()

        assertThat(vm.state.value.page.items).hasSize(2)
        assertThat(vm.state.value.page.refreshError).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(vm.state.value.page.refreshing).isFalse()
        vm.retry()
        assertThat(vm.state.value.page.refreshError).isNull()
        assertThat(feed.calls.takeLast(2))
            .containsExactly(FeedFilter.All to null, FeedFilter.All to null)
    }

    @Test
    fun `a page that came back empty with more behind it asks for the next one`() = runTest {
        feed.pages =
            mapOf(
                null to Page(emptyList(), "c1"),
                "c1" to Page(emptyList(), "c2"),
                "c2" to Page(listOf(feedJournal(4)), null),
            )

        val vm = vm()

        assertThat(vm.state.value.page.items.map { it.key }).containsExactly("journal:j4")
        assertThat(vm.state.value.page.isEmpty).isFalse()
        assertThat(feed.calls.map { it.second }).containsExactly(null, "c1", "c2").inOrder()
    }

    @Test
    fun `an empty feed is empty only when there is nothing more to ask`() = runTest {
        feed.pages = mapOf(null to Page(emptyList(), null))

        val vm = vm()

        assertThat(vm.state.value.page.isEmpty).isTrue()
    }

    @Test
    fun `a like given on a bike's page shows on its card, other items are untouched`() = runTest {
        val vm = vm()

        bikes.setLiked(BikeId("b0"), true)
        runCurrent()

        val bike = vm.state.value.page.items.filterIsInstance<FeedItem.Bike>().single()
        assertThat(bike.bike.liked).isTrue()
        assertThat(vm.state.value.page.items[1]).isEqualTo(feedJournal(1))
        val change = LikeChange(BikeId("b0"), LikeState(liked = false, likes = 99))
        assertThat(change.state.likes).isEqualTo(99)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class JournalListViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val journal = FakeJournal()

    @Test
    fun `a bike's journal pages through the cursor`() = runTest {
        journal.pages =
            mapOf(null to Page(journals(0, 2), "c1"), "c1" to Page(journals(1, 2), null))
        val vm = JournalListViewModel(journal, JournalSource.OfBike(BikeId("b1")))

        vm.loadMore()

        assertThat(vm.state.value.items.map { it.id.value })
            .containsExactly("j0", "j1", "j2")
            .inOrder()
        assertThat(journal.bikeCalls).containsExactly(BikeId("b1") to null, BikeId("b1") to "c1")
    }

    @Test
    fun `a bike without entries is empty, an error is an error`() = runTest {
        journal.pages = mapOf(null to Page(emptyList(), null))
        assertThat(
                JournalListViewModel(journal, JournalSource.OfBike(BikeId("b1")))
                    .state
                    .value
                    .isEmpty
            )
            .isTrue()

        journal.nextError = DataError.NotFound()
        val failed = JournalListViewModel(journal, JournalSource.OfBike(BikeId("b1")))
        assertThat(failed.state.value.error).isEqualTo(UiText.Res(R.string.error_not_found))
    }

    @Test
    fun `the saved list is the saved one, and an entry un-saved elsewhere leaves it`() = runTest {
        journal.savedPages = mapOf(null to Page(journals(0, 3), null))
        val vm = JournalListViewModel(journal, JournalSource.Saved)
        assertThat(vm.state.value.items).hasSize(3)
        assertThat(journal.bikeCalls).isEmpty()

        journal.setSaved(JournalId("j1"), false)
        runCurrent()

        assertThat(vm.state.value.items.map { it.id.value }).containsExactly("j0", "j2").inOrder()
    }

    @Test
    fun `a bike's list ignores saves`() = runTest {
        val vm = JournalListViewModel(journal, JournalSource.OfBike(BikeId("b1")))

        journal.setSaved(JournalId("j1"), false)
        runCurrent()

        assertThat(vm.state.value.items).hasSize(3)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val journal = FakeJournal()

    private fun vm(id: String = "j1") = JournalViewModel(journal, JournalId(id))

    @Test
    fun `an entry loads with the saved state this session knows`() = runTest {
        journal.know("j1", true)

        val loaded = vm().state.value as JournalUiState.Loaded

        assertThat(loaded.entry.summary.title).isEqualTo("Запись 1")
        assertThat(loaded.saved).isTrue()
        assertThat((vm("j2").state.value as JournalUiState.Loaded).saved).isNull()
    }

    @Test
    fun `a hidden, deleted or closed entry is not found, other failures are not`() = runTest {
        assertThat(vm("missing").state.value)
            .isEqualTo(JournalUiState.Failed(UiText.Res(R.string.error_not_found), notFound = true))

        journal.nextError = DataError.Offline(java.io.IOException())
        assertThat(vm().state.value)
            .isEqualTo(JournalUiState.Failed(UiText.Res(R.string.error_offline), notFound = false))
    }

    @Test
    fun `saving shows the bookmark at once and the server's answer has the last word`() = runTest {
        val vm = vm()

        vm.toggleSaved()

        val saved = vm.state.value as JournalUiState.Loaded
        assertThat(saved.saved).isTrue()
        assertThat(saved.saving).isFalse()
        assertThat(journal.saves).containsExactly(JournalId("j1") to true)

        vm.toggleSaved()
        assertThat((vm.state.value as JournalUiState.Loaded).saved).isFalse()
        assertThat(journal.saves.last()).isEqualTo(JournalId("j1") to false)
    }

    @Test
    fun `a refused save puts the bookmark back and says why`() = runTest {
        val vm = vm()
        journal.saveError = DataError.RateLimited(60)

        vm.toggleSaved()

        val loaded = vm.state.value as JournalUiState.Loaded
        assertThat(loaded.saved).isNull()
        assertThat(loaded.saving).isFalse()
        assertThat(loaded.saveError).isEqualTo(UiText.Res(R.string.error_rate_limited, listOf(1)))
        // The next try starts clean.
        vm.toggleSaved()
        assertThat((vm.state.value as JournalUiState.Loaded).saveError).isNull()
    }

    @Test
    fun `a draft cannot be saved and asks the server nothing`() = runTest {
        journal.entries =
            mapOf(
                "j1" to
                    journalEntry(1).let {
                        it.copy(summary = it.summary.copy(status = JournalStatus.Draft))
                    }
            )
        val vm = vm()

        assertThat((vm.state.value as JournalUiState.Loaded).canSave).isFalse()
        vm.toggleSaved()

        assertThat(journal.saves).isEmpty()
    }

    @Test
    fun `saved or un-saved on another screen shows here without loading again`() = runTest {
        val vm = vm()
        val calls = journal.entryCalls

        journal.setSaved(JournalId("j1"), true)
        runCurrent()
        journal.setSaved(JournalId("j2"), true) // another entry: not this one
        runCurrent()

        assertThat((vm.state.value as JournalUiState.Loaded).saved).isTrue()
        assertThat(journal.entryCalls).isEqualTo(calls)
    }

    @Test
    fun `loading again after a failure shows the entry`() = runTest {
        journal.nextError = DataError.Offline(java.io.IOException())
        val vm = vm()

        vm.load()

        assertThat(vm.state.value).isInstanceOf(JournalUiState.Loaded::class.java)
    }
}
