package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.intents.IntentEditorUiState
import ru.colabike.app.intents.IntentEditorViewModel
import ru.colabike.app.nearby.CoarseFix
import ru.colabike.app.nearby.CoarseLocation
import ru.colabike.app.nearby.CoarseResult
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.RideAreaPoint

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IntentAreaViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeIntents()
    private val clock = Clock.fixed(Instant.parse("2026-10-08T08:00:00Z"), ZoneOffset.UTC)
    private val fix = CoarseResult.Located(CoarseFix(37.61743, 55.75582))

    private class Location(var result: CoarseResult) : CoarseLocation {
        var calls = 0
        var pending: CompletableDeferred<CoarseResult>? = null

        override fun granted() = true

        override suspend fun current(): CoarseResult {
            calls++
            // Deliberately non-cooperative: verifies generation protection, not just cancellation.
            return pending?.let { withContext(NonCancellable) { it.await() } } ?: result
        }
    }

    private fun model(location: CoarseLocation, id: String? = null) =
        IntentEditorViewModel(repository, clock, ZoneOffset.UTC, id, location = location)

    private fun state(vm: IntentEditorViewModel) = vm.state.value as IntentEditorUiState.Editing

    private fun locate(vm: IntentEditorViewModel) {
        vm.locationPermission(checkNotNull(vm.requestLocation()), true)
    }

    @Test
    fun `a provider that does not respond times out and can be cancelled without losing form`() =
        runTest {
            val location =
                object : CoarseLocation {
                    override fun granted() = true

                    override suspend fun current(): CoarseResult = awaitCancellation()
                }
            val vm = model(location)
            vm.setAreaLabel("Парк")
            val original = state(vm).form
            vm.openArea()
            locate(vm)
            testScheduler.advanceTimeBy(25001)
            assertThat(state(vm).areaPicker?.locating).isFalse()
            assertThat(state(vm).areaPicker?.problem)
                .isEqualTo(UiText.Res(R.string.intent_area_unavailable))
            assertThat(state(vm).form).isEqualTo(original)
            locate(vm)
            vm.abandonLocation()
            assertThat(state(vm).areaPicker?.locating).isFalse()
        }

    @Test
    fun `opening editor and picker never reads location or supplies a default centre`() = runTest {
        val location = Location(fix)
        val vm = model(location)
        vm.openArea()
        vm.confirmArea()
        assertThat(location.calls).isEqualTo(0)
        assertThat(state(vm).areaPicker?.point).isNull()
        assertThat(state(vm).areaPicker?.canConfirm).isFalse()
        assertThat(repository.created).isEmpty()
    }

    @Test
    fun `coarse location is rounded and label geometry radius are committed only together`() =
        runTest {
            val vm = model(Location(fix))
            vm.setAreaLabel("Старый район")
            vm.openArea()
            locate(vm)
            assertThat(state(vm).areaPicker?.point).isEqualTo(RideAreaPoint(37.62, 55.76, 5000))
            assertThat(state(vm).form.base.area).isNull()
            vm.setAreaDraftLabel("Новый район")
            vm.setAreaRadius(12000)
            vm.save()
            assertThat(repository.created).isEmpty()
            vm.confirmArea()
            vm.save()
            val draft = repository.created.single().first
            assertThat(draft.passport.areaLabel).isEqualTo("Новый район")
            assertThat(draft.passport.area).isEqualTo(RideAreaPoint(37.62, 55.76, 12000))
            assertThat(draft.visibility).isEqualTo(IntentVisibility.Private)
            assertThat(draft.allowSuggestions).isFalse()
        }

    @Test
    fun `denial and provider failures retain all form fields and manual entry works`() = runTest {
        for ((result, message) in
            listOf(
                CoarseResult.NoPermission to R.string.intent_area_denied,
                CoarseResult.ServiceOff to R.string.intent_area_off,
                CoarseResult.Unavailable to R.string.intent_area_unavailable,
            )) {
            val vm = model(Location(result))
            vm.setAreaLabel("Парк")
            vm.setPurpose("social")
            val original = state(vm).form
            vm.openArea()
            locate(vm)
            assertThat(state(vm).form).isEqualTo(original)
            assertThat(state(vm).areaPicker?.problem).isEqualTo(UiText.Res(message))
            vm.setAreaCenter(GeoPoint(55.73, 37.6))
            vm.confirmArea()
            assertThat(state(vm).form.base.area).isEqualTo(RideAreaPoint(37.6, 55.73, 5000))
        }
    }

    @Test
    fun `declined permission never calls provider and text-only form can still save`() = runTest {
        val location = Location(fix)
        val vm = model(location)
        vm.setAreaLabel("Парк")
        vm.openArea()
        vm.locationPermission(checkNotNull(vm.requestLocation()), false)
        vm.cancelArea()
        vm.save()
        assertThat(location.calls).isEqualTo(0)
        assertThat(repository.created.single().first.passport.area).isNull()
    }

    @Test
    fun `late permission result cannot start a new or closed picker request`() = runTest {
        val location = Location(fix)
        val vm = model(location)
        vm.openArea()
        val old = checkNotNull(vm.requestLocation())
        vm.cancelArea()
        vm.openArea()
        val fresh = checkNotNull(vm.requestLocation())
        vm.locationPermission(old, true)
        assertThat(location.calls).isEqualTo(0)
        vm.locationPermission(fresh, true)
        assertThat(location.calls).isEqualTo(1)
    }

    @Test
    fun `late fix after cancel or manual replacement never overwrites chosen centre`() = runTest {
        val location = Location(fix).apply { pending = CompletableDeferred() }
        val vm = model(location)
        vm.openArea()
        locate(vm)
        assertThat(vm.requestLocation()).isNull()
        vm.cancelArea()
        vm.openArea()
        vm.setAreaCenter(GeoPoint(59.94, 30.31))
        location.pending!!.complete(fix)
        assertThat(state(vm).areaPicker?.point).isEqualTo(RideAreaPoint(30.31, 59.94, 5000))
        assertThat(state(vm).form.base.area).isNull()
    }

    @Test
    fun `editing keeps existing geometry and visibility until area name actually changes`() =
        runTest {
            val own =
                sampleIntent(3, own = true, visibility = IntentVisibility.Private).let {
                    it.copy(passport = it.passport.copy(area = RideAreaPoint(37.6, 55.73, 2000)))
                }
            repository.mine = listOf(own)
            val vm = model(Location(fix), own.id)
            vm.setAreaLabel(" ${own.passport.areaLabel} ")
            assertThat(state(vm).form.base.area).isEqualTo(own.passport.area)
            vm.openArea()
            locate(vm)
            vm.setAreaDraftLabel("Другая область")
            vm.cancelArea()
            assertThat(state(vm).form.base.area).isEqualTo(own.passport.area)
            vm.setAreaLabel("Другой парк")
            vm.save()
            assertThat(repository.replaced.single().second.passport.area).isNull()
            assertThat(repository.replaced.single().second.visibility)
                .isEqualTo(IntentVisibility.Private)
        }

    @Test
    fun `manual centre rejects invalid numbers and empty names cannot be confirmed`() = runTest {
        val vm = model(Location(fix))
        vm.openArea()
        vm.setAreaCenter(GeoPoint(Double.NaN, 37.6))
        vm.setAreaCenter(GeoPoint(91.0, 37.6))
        assertThat(state(vm).areaPicker?.point).isNull()
        vm.setAreaCenter(GeoPoint(-33.87, 151.21))
        vm.setAreaRadius(1000000)
        assertThat(state(vm).areaPicker?.point?.radiusM).isEqualTo(100000)
        assertThat(state(vm).areaPicker?.canConfirm).isFalse()
        vm.setAreaDraftLabel("   ")
        vm.confirmArea()
        assertThat(state(vm).form.base.area).isNull()
        vm.setAreaDraftLabel("Парк")
        vm.confirmArea()
        assertThat(state(vm).form.base.area).isEqualTo(RideAreaPoint(151.21, -33.87, 100000))
        vm.removeAreaGeometry()
        assertThat(state(vm).form.areaLabel).isEqualTo("Парк")
        assertThat(state(vm).form.base.area).isNull()
    }
}
