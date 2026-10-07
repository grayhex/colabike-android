package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.rides.AnalysisUiState
import ru.colabike.app.rides.BikeRidesViewModel
import ru.colabike.app.rides.RideRow
import ru.colabike.app.rides.RideSegment
import ru.colabike.app.rides.RideUiState
import ru.colabike.app.rides.RideViewModel
import ru.colabike.app.rides.RidesViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.DataError
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Page
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideRole
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RideSummary

@OptIn(ExperimentalCoroutinesApi::class)
class RidesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRides()

    private fun vm() = RidesViewModel(repository, debounceMs = 400)

    private fun RidesViewModel.ids() = state.value.page.items.map { it.key }

    @Test
    fun `the section opens on the rides that took place and asks for nothing else`() = runTest {
        val vm = vm()

        assertThat(vm.state.value.segment).isEqualTo(RideSegment.Completed)
        assertThat(vm.ids()).containsExactly("ride-0", "ride-1", "ride-2").inOrder()
        assertThat(repository.completedCalls).containsExactly(null to null)
        assertThat(repository.upcomingCalls).isEmpty()
        assertThat(repository.myUpcomingCalls).isEqualTo(0)
    }

    @Test
    fun `an answer taken on a plan makes the list read again`() = runTest {
        val changes = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
        val vm = RidesViewModel(repository, debounceMs = 400, participationChanges = changes)
        vm.select(RideSegment.Upcoming)
        assertThat(repository.upcomingCalls).hasSize(1)

        changes.tryEmit("r2")

        assertThat(repository.upcomingCalls).hasSize(2)
        assertThat(vm.ids()).containsExactly("r2")
    }

    @Test
    fun `each segment is its own list and none is mixed with another`() = runTest {
        val vm = vm()
        assertThat(vm.ids()).containsExactly("ride-0", "ride-1", "ride-2").inOrder()

        vm.select(RideSegment.Upcoming)
        assertThat(vm.ids()).containsExactly("r2")
        assertThat(repository.upcomingCalls).hasSize(1)
    }

    @Test
    fun `choosing the segment that is chosen asks nothing`() = runTest {
        val vm = vm()
        val before = repository.completedCalls.size

        vm.select(RideSegment.Completed)

        assertThat(repository.completedCalls).hasSize(before)
    }

    @Test
    fun `completed rides page through the cursor without repeating one`() = runTest {
        repository.completedPages =
            mapOf(null to Page(rides(0, 2), "c1"), "c1" to Page(rides(1, 2), null))
        val vm = vm()
        vm.select(RideSegment.Completed)

        vm.loadMore()
        vm.loadMore() // the last page: nothing more to ask

        assertThat(vm.ids()).containsExactly("ride-0", "ride-1", "ride-2").inOrder()
        assertThat(repository.completedCalls).containsExactly(null to null, null to "c1").inOrder()
    }

    @Test
    fun `typing waits for a pause and asks once with the whole text, in the public lists`() =
        runTest {
            val vm = vm()
            repository.completedCalls.clear()

            vm.onSearchText("в")
            advanceTimeBy(200)
            vm.onSearchText("вечер ")
            assertThat(vm.state.value.typed).isEqualTo("вечер ")
            assertThat(repository.completedCalls).isEmpty()
            advanceTimeBy(401)
            runCurrent()

            assertThat(repository.completedCalls).containsExactly("вечер" to null)
            assertThat(vm.state.value.query).isEqualTo("вечер")
        }

    @Test
    fun `a new search cancels the request in flight and its late answer is not shown`() = runTest {
        val first = CompletableDeferred<Page<RideSummary>>()
        repository.answer = { query, _ ->
            if (query == null) first.await() else Page(rides(7, 1), null)
        }
        val vm = vm() // asks without text and waits

        vm.onSearchText("рама")
        advanceTimeBy(401)
        runCurrent()
        first.complete(Page(rides(0, 3), "stale"))
        runCurrent()

        assertThat(vm.ids()).containsExactly("ride-7")
        assertThat(vm.state.value.page.nextCursor).isNull()
    }

    @Test
    fun `the personal lists have no search and leaving the public ones forgets the text`() =
        runTest {
            val vm = vm()
            vm.onSearchText("рама")
            advanceTimeBy(401)
            runCurrent()

            vm.select(RideSegment.Mine)
            vm.onSearchText("ничего")
            advanceTimeBy(401)
            runCurrent()

            assertThat(vm.state.value.typed).isEmpty()
            assertThat(vm.state.value.query).isEmpty()
            assertThat(repository.mineCalls).containsExactly(null)
        }

    @Test
    fun `my plans are one request and never ask for a second page`() = runTest {
        repository.myPlans = listOf(plan(1, RideRole.Organizer), plan(2, RideRole.Invited))
        val vm = vm()

        vm.select(RideSegment.MyPlans)
        vm.loadMore()
        vm.loadMore()

        assertThat(vm.ids()).containsExactly("plan-1", "plan-2").inOrder()
        assertThat(repository.myUpcomingCalls).isEqualTo(1)
        assertThat(vm.state.value.page.nextCursor).isNull()
    }

    @Test
    fun `a private or cancelled own ride of mine has no page, a public one has`() = runTest {
        val open = OwnRide(rideSummary(1), isPublic = true, privacyRadiusM = null, pointCount = 10)
        val closed = open.copy(ride = rideSummary(2), isPublic = false)
        val cancelled =
            open.copy(ride = rideSummary(3, status = RideStatus.Cancelled), isPublic = true)
        repository.minePages = mapOf(null to Page(listOf(open, closed, cancelled), null))
        val vm = vm()

        vm.select(RideSegment.Mine)

        assertThat(vm.state.value.page.items.map { it.hasPage })
            .containsExactly(true, false, false)
            .inOrder()
    }

    @Test
    fun `an empty list is empty only when nothing more can be asked`() = runTest {
        repository.completedPages = mapOf(null to Page(emptyList(), null))
        val vm = vm()

        vm.select(RideSegment.Completed)

        assertThat(vm.state.value.page.isEmpty).isTrue()
    }

    @Test
    fun `a failed first page is a screen error and a retry asks for the same list`() = runTest {
        repository.nextError = DataError.Offline(java.io.IOException())
        val vm = vm()

        assertThat(vm.state.value.page.error).isEqualTo(UiText.Res(R.string.error_offline))
        vm.retry()
        assertThat(vm.state.value.page.error).isNull()
        assertThat(vm.ids()).hasSize(3)
    }

    @Test
    fun `a comment on a ride's page changes its count in the list, the others stay`() = runTest {
        val changes =
            kotlinx.coroutines.flow.MutableSharedFlow<CommentCountChange>(extraBufferCapacity = 4)
        val vm = RidesViewModel(repository, commentChanges = changes)
        vm.select(RideSegment.Completed)
        val before = vm.state.value.page.items[1].ride.comments

        changes.tryEmit(CommentCountChange(CommentTarget(CommentKind.Ride, "ride-1"), +1))
        changes.tryEmit(CommentCountChange(CommentTarget(CommentKind.Bike, "ride-0"), +1))
        runCurrent()

        val items = vm.state.value.page.items
        assertThat(items[1].ride.comments).isEqualTo(before + 1)
        assertThat(items[0].ride.comments).isEqualTo(PreviewData.ride.comments)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BikeRidesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRides()

    @Test
    fun `the rides of a bike page and search in their own list`() = runTest {
        repository.bikePages =
            mapOf(null to Page(rides(10, 2), "c1"), "c1" to Page(rides(11, 2), null))
        val vm = BikeRidesViewModel(repository, BikeId("b1"), debounceMs = 100)

        vm.loadMore()
        assertThat(vm.state.value.page.items.map { it.key })
            .containsExactly("ride-10", "ride-11", "ride-12")
            .inOrder()

        vm.onSearchText("вечер")
        advanceTimeBy(101)
        runCurrent()
        assertThat(repository.bikeCalls.last()).isEqualTo(Triple(BikeId("b1"), "вечер", null))
        assertThat(vm.state.value.page.items.first()).isInstanceOf(RideRow.Public::class.java)
    }

    @Test
    fun `a private bike is not found`() = runTest {
        repository.nextError = DataError.NotFound()

        val vm = BikeRidesViewModel(repository, BikeId("b1"))

        assertThat(vm.state.value.page.error).isEqualTo(UiText.Res(R.string.error_not_found))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RideViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRides()

    @Test
    fun `a ride loads`() = runTest {
        val loaded = RideViewModel(repository, RideId("ride-1")).state.value

        assertThat(loaded).isInstanceOf(RideUiState.Loaded::class.java)
        assertThat((loaded as RideUiState.Loaded).ride.summary.title).isEqualTo("Покатушка 1")
    }

    @Test
    fun `a private, cancelled or missing ride is not found, other failures are not`() = runTest {
        assertThat(RideViewModel(repository, RideId("nope")).state.value)
            .isEqualTo(RideUiState.Failed(UiText.Res(R.string.error_not_found), notFound = true))

        repository.nextError = DataError.Offline(java.io.IOException())
        assertThat(RideViewModel(repository, RideId("ride-1")).state.value)
            .isEqualTo(RideUiState.Failed(UiText.Res(R.string.error_offline), notFound = false))
    }

    @Test
    fun `loading again after a failure shows the ride`() = runTest {
        repository.nextError = DataError.Offline(java.io.IOException())
        val vm = RideViewModel(repository, RideId("ride-1"))

        vm.load()

        assertThat(vm.state.value).isInstanceOf(RideUiState.Loaded::class.java)
    }

    @Test
    fun `a comment on this ride changes its count, one on another does not`() = runTest {
        val changes =
            kotlinx.coroutines.flow.MutableSharedFlow<CommentCountChange>(extraBufferCapacity = 4)
        val vm = RideViewModel(repository, RideId("ride-1"), changes)
        val before = (vm.state.value as RideUiState.Loaded).ride.summary.comments

        changes.tryEmit(CommentCountChange(CommentTarget(CommentKind.Ride, "ride-1"), +1))
        changes.tryEmit(CommentCountChange(CommentTarget(CommentKind.Ride, "ride-2"), +1))
        runCurrent()

        assertThat((vm.state.value as RideUiState.Loaded).ride.summary.comments)
            .isEqualTo(before + 1)
    }

    // --- the charts' series: a request of their own ------------------------------------------

    @Test
    fun `the series are not asked for until the page asks`() = runTest {
        val vm = RideViewModel(repository, RideId("ride-0"))

        assertThat(vm.analysis.value).isEqualTo(AnalysisUiState.NotAsked)
        assertThat(repository.analysisCalls).isEmpty()

        vm.loadAnalysis()

        assertThat(vm.analysis.value).isInstanceOf(AnalysisUiState.Loaded::class.java)
        assertThat(repository.analysisCalls).containsExactly("ride-0")
    }

    @Test
    fun `asking again while they are in or on the way does not ask twice`() = runTest {
        val vm = RideViewModel(repository, RideId("ride-0"))

        vm.loadAnalysis()
        vm.loadAnalysis()

        assertThat(repository.analysisCalls).hasSize(1)
    }

    @Test
    fun `a plan has no route and no series are asked for`() = runTest {
        val vm = RideViewModel(repository, RideId("plan-1"))

        vm.loadAnalysis()

        assertThat(vm.analysis.value).isEqualTo(AnalysisUiState.NotAsked)
        assertThat(repository.analysisCalls).isEmpty()
    }

    @Test
    fun `a ride without an analysis is absent, not an error`() = runTest {
        // ride-1 has a route, and the server has no analysis for it (404).
        val vm = RideViewModel(repository, RideId("ride-1"))

        vm.loadAnalysis()

        assertThat(vm.analysis.value).isEqualTo(AnalysisUiState.Absent)
    }

    @Test
    fun `a failed request offers asking again and the page stays`() = runTest {
        val vm = RideViewModel(repository, RideId("ride-0"))
        repository.nextError = DataError.Offline(java.io.IOException())

        vm.loadAnalysis()

        assertThat(vm.analysis.value)
            .isEqualTo(AnalysisUiState.Failed(UiText.Res(R.string.error_offline)))
        assertThat(vm.state.value).isInstanceOf(RideUiState.Loaded::class.java)

        vm.loadAnalysis()

        assertThat(vm.analysis.value).isInstanceOf(AnalysisUiState.Loaded::class.java)
    }

    @Test
    fun `loading the page again forgets the series`() = runTest {
        val vm = RideViewModel(repository, RideId("ride-0"))
        vm.loadAnalysis()

        vm.load()

        assertThat(vm.analysis.value).isEqualTo(AnalysisUiState.NotAsked)
    }
}
