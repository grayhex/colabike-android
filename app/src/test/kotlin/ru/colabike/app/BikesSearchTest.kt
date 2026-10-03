package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.bikes.BikesViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.DataError
import ru.colabike.core.model.LikeChange
import ru.colabike.core.model.LikeState
import ru.colabike.core.model.Page

/** Search and filters of the bike list: one request at a time, the newest condition wins. */
@OptIn(ExperimentalCoroutinesApi::class)
class BikesSearchTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeBikes()

    private fun names(vm: BikesViewModel) = vm.state.value.bikes.map { it.name }

    @Test
    fun `typing waits for a pause and asks once, with the whole text`() = runTest {
        val vm = BikesViewModel(repo, debounceMs = 400)
        repo.calls.clear()

        vm.onSearchText("к")
        advanceTimeBy(200)
        vm.onSearchText("ку")
        advanceTimeBy(200)
        vm.onSearchText("куб ")
        // The field follows every key at once, the list waits.
        assertThat(vm.state.value.typed).isEqualTo("куб ")
        assertThat(repo.calls).isEmpty()

        advanceTimeBy(401)
        runCurrent()

        assertThat(repo.calls).containsExactly(BikeQuery(text = "куб") to null)
        assertThat(vm.state.value.query.text).isEqualTo("куб")
    }

    @Test
    fun `a new condition cancels the request in flight and its late answer is not shown`() =
        runTest {
            val first = CompletableDeferred<Page<BikeSummary>>()
            val second = CompletableDeferred<Page<BikeSummary>>()
            repo.answer = { query, _ ->
                if (query.categories.isEmpty()) first.await() else second.await()
            }
            val vm = BikesViewModel(repo, debounceMs = 0)

            vm.toggleCategory("mtb")
            second.complete(Page(bikes(10, 2), null))
            first.complete(Page(bikes(0, 3), "stale-cursor"))
            runCurrent()

            // Only the newest query's list; the old query's cursor is gone with it.
            assertThat(names(vm)).containsExactly("Велосипед 10", "Велосипед 11").inOrder()
            assertThat(vm.state.value.nextCursor).isNull()
            assertThat(vm.state.value.query.categories).containsExactly("mtb")
        }

    @Test
    fun `a page of the old query is not appended to the new query's list`() = runTest {
        repo.pages = mapOf(null to Page(bikes(0, 3), "c1"), "c1" to Page(bikes(3, 3), null))
        val vm = BikesViewModel(repo, debounceMs = 0)
        val late = CompletableDeferred<Page<BikeSummary>>()
        repo.answer = { query, cursor ->
            if (cursor == "c1") late.await() else Page(bikes(20, 2), "d1")
        }

        vm.loadMore() // the second page of the "everything" query is on its way ...
        vm.toggleCategory("bmx") // ... when the condition changes
        late.complete(Page(bikes(3, 3), null))
        runCurrent()

        assertThat(names(vm)).containsExactly("Велосипед 20", "Велосипед 21").inOrder()
        assertThat(vm.state.value.nextCursor).isEqualTo("d1")
        assertThat(vm.state.value.loadingMore).isFalse()
    }

    @Test
    fun `categories add up as OR and toggle off, scope and text stay`() = runTest {
        val vm = BikesViewModel(repo, debounceMs = 0)
        vm.selectScope(BikeScope.Mine)
        vm.onSearchText("cube")
        runCurrent()

        vm.toggleCategory("mtb")
        vm.toggleCategory("bmx")
        assertThat(vm.state.value.query)
            .isEqualTo(BikeQuery(BikeScope.Mine, "cube", setOf("mtb", "bmx")))

        vm.toggleCategory("mtb")
        assertThat(vm.state.value.query.categories).containsExactly("bmx")
    }

    @Test
    fun `clearing the filters asks for everything in the scope again`() = runTest {
        val vm = BikesViewModel(repo, debounceMs = 0)
        vm.selectScope(BikeScope.Mine)
        vm.onSearchText("cube")
        vm.toggleCategory("mtb")
        runCurrent()

        vm.clearFilters()

        assertThat(vm.state.value.query).isEqualTo(BikeQuery(BikeScope.Mine))
        assertThat(vm.state.value.typed).isEmpty()
        assertThat(repo.calls.last().first).isEqualTo(BikeQuery(BikeScope.Mine))
    }

    @Test
    fun `no connection during a search is an error, not an empty catalogue`() = runTest {
        val vm = BikesViewModel(repo, debounceMs = 0)
        repo.nextError = DataError.Offline(java.io.IOException())

        vm.onSearchText("cube")
        runCurrent()

        assertThat(vm.state.value.error).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(vm.state.value.bikes).isEmpty()
        // Retrying asks for the same search, not for everything.
        vm.retry()
        assertThat(repo.calls.last().first).isEqualTo(BikeQuery(text = "cube"))
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `a failed next page of a search keeps what was found`() = runTest {
        repo.pages = mapOf(null to Page(bikes(0, 3), "c1"))
        val vm = BikesViewModel(repo, debounceMs = 0)
        vm.onSearchText("cube")
        runCurrent()
        repo.nextError = DataError.Server(502, "req-1")

        vm.loadMore()

        assertThat(vm.state.value.bikes).hasSize(3)
        assertThat(vm.state.value.moreError).isNotNull()
    }

    @Test
    fun `a like given elsewhere shows in the list without loading it again`() = runTest {
        val vm = BikesViewModel(repo, debounceMs = 0)
        val calls = repo.calls.size

        repo.setLiked(BikeId("b1"), true)
        runCurrent()

        val b1 = vm.state.value.bikes.first { it.id == BikeId("b1") }
        assertThat(b1.liked).isTrue()
        assertThat(repo.calls.size).isEqualTo(calls)
        // Another bike is untouched.
        assertThat(vm.state.value.bikes.first { it.id == BikeId("b2") }.likes)
            .isEqualTo(bikes(0, 3)[2].likes)
        // And a change with a count of its own is taken as it is.
        repo.pages = repo.pages
        val change = LikeChange(BikeId("b0"), LikeState(liked = false, likes = 99))
        assertThat(change.state.likes).isEqualTo(99)
    }
}
