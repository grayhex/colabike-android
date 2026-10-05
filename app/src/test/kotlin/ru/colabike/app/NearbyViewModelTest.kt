package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.nearby.CoarseFix
import ru.colabike.app.nearby.CoarseResult
import ru.colabike.app.nearby.NearbyGroup
import ru.colabike.app.nearby.NearbyUiState
import ru.colabike.app.nearby.NearbyViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NearbyChange
import ru.colabike.core.model.NearbySource

class NearbyViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeNearby()
    private val location = FakeCoarseLocation()

    private fun viewModel() = NearbyViewModel(repository, location)

    private fun loaded(vm: NearbyViewModel) = vm.state.value as NearbyUiState.Loaded

    @Test
    fun `it shows what the server holds, with the version a change will name`() = runTest {
        val vm = viewModel()

        assertThat(loaded(vm).settings).isEqualTo(defaultNearbySettings)
        assertThat(loaded(vm).busy).isFalse()
    }

    @Test
    fun `a failed load says why and loading again recovers`() = runTest {
        repository.loadError = DataError.Offline(IOException())
        val vm = viewModel()

        assertThat(vm.state.value)
            .isEqualTo(NearbyUiState.Failed(UiText.Res(R.string.error_offline)))

        repository.loadError = null
        vm.load()

        assertThat(vm.state.value).isInstanceOf(NearbyUiState.Loaded::class.java)
    }

    @Test
    fun `turning it on is its own consent and shows what the server took`() = runTest {
        val vm = viewModel()

        vm.setEnabled(true)

        assertThat(repository.changes).containsExactly(NearbyChange(enabled = true))
        assertThat(loaded(vm).settings.enabled).isTrue()
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.nearby_saved))
    }

    @Test
    fun `an operator's switch allows only turning it off`() = runTest {
        repository.current = activeNearbySettings.copy(available = false)
        val vm = viewModel()

        vm.setEnabled(true)
        assertThat(repository.changes).isEmpty()

        vm.setEnabled(false)
        assertThat(loaded(vm).settings.enabled).isFalse()
    }

    @Test
    fun `a kind is added and taken out, and none chosen means any`() = runTest {
        val vm = viewModel()

        vm.toggle(NearbyGroup.Paces, "relaxed")
        vm.toggle(NearbyGroup.Paces, "sporty")
        vm.toggle(NearbyGroup.Paces, "relaxed")

        assertThat(repository.changes.map { it.paces })
            .containsExactly(setOf("relaxed"), setOf("relaxed", "sporty"), setOf("sporty"))
            .inOrder()
        assertThat(loaded(vm).settings.preferences.paces).containsExactly("sporty")
    }

    @Test
    fun `the horizon is sent as it is`() = runTest {
        val vm = viewModel()

        vm.setHorizon(14)

        assertThat(repository.changes).containsExactly(NearbyChange(horizonDays = 14))
        assertThat(loaded(vm).settings.horizonDays).isEqualTo(14)
    }

    // --- the area from this phone ------------------------------------------------------------

    @Test
    fun `the phone's place is rounded to the centre of a grid cell before anything else sees it`() =
        runTest {
            location.result = CoarseResult.Located(CoarseFix(37.6173, 55.7558))
            val vm = viewModel()

            vm.locate()

            val draft = loaded(vm).draft!!
            assertThat(draft.longitude).isEqualTo(37.625)
            assertThat(draft.latitude).isEqualTo(55.755)
            assertThat(draft.radiusM).isEqualTo(10_000)
            // Nothing is sent until the person says yes, and no state prints a coordinate.
            assertThat(repository.confirmed).isEmpty()
            assertThat(draft.toString()).doesNotContain("37.6")
            assertThat(draft.toString()).doesNotContain("55.7")
            assertThat(CoarseFix(37.6173, 55.7558).toString()).doesNotContain("37.6")
        }

    @Test
    fun `an area is saved with the radius the person chose, and the draft is gone`() = runTest {
        val vm = viewModel()
        vm.locate()

        vm.setDraftRadius(20_000)
        vm.confirmDraft()

        assertThat(repository.confirmed).containsExactly(Triple(37.625, 55.755, 20_000))
        assertThat(repository.replaced).isFalse()
        assertThat(loaded(vm).draft).isNull()
        assertThat(loaded(vm).settings.source).isEqualTo(NearbySource.Device)
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.nearby_area_saved))
    }

    @Test
    fun `a radius the server would not take is not offered`() = runTest {
        val vm = viewModel()
        vm.locate()

        // The operator's limit in the fake is 50 km; 75 km is not on offer, nor is 7 km.
        vm.setDraftRadius(75_000)
        vm.setDraftRadius(7_000)

        assertThat(loaded(vm).draft!!.radiusM).isEqualTo(10_000)
    }

    @Test
    fun `an area from the site is replaced only after a yes of its own`() = runTest {
        repository.current = activeNearbySettings
        val vm = viewModel()
        vm.locate()

        vm.confirmDraft()

        assertThat(loaded(vm).askReplace).isTrue()
        assertThat(repository.confirmed).isEmpty()

        vm.cancelReplace()
        assertThat(loaded(vm).askReplace).isFalse()
        assertThat(loaded(vm).draft).isNotNull()

        vm.confirmDraft(replaceManual = true)

        assertThat(repository.replaced).isTrue()
        assertThat(loaded(vm).settings.source).isEqualTo(NearbySource.Device)
    }

    @Test
    fun `a site area that appeared meanwhile is asked about, not overwritten`() = runTest {
        // The phone read defaults (no area), then the site chose one: the server holds to its 409.
        val vm = viewModel()
        vm.locate()
        repository.current = activeNearbySettings

        vm.confirmDraft()

        assertThat(loaded(vm).askReplace).isTrue()
        assertThat(loaded(vm).draft).isNotNull()
        assertThat(repository.current.source).isEqualTo(NearbySource.Manual)

        vm.confirmDraft(replaceManual = true)

        assertThat(repository.replaced).isTrue()
        assertThat(repository.current.source).isEqualTo(NearbySource.Device)
        assertThat(loaded(vm).draft).isNull()
    }

    @Test
    fun `no permission, a switched off location and no provider each say so`() = runTest {
        val vm = viewModel()

        location.result = CoarseResult.NoPermission
        vm.locate()
        assertThat(loaded(vm).problem).isEqualTo(UiText.Res(R.string.nearby_location_denied))
        assertThat(loaded(vm).draft).isNull()

        location.result = CoarseResult.ServiceOff
        vm.locate()
        assertThat(loaded(vm).problem).isEqualTo(UiText.Res(R.string.nearby_location_off))

        location.result = CoarseResult.Unavailable
        vm.locate()
        assertThat(loaded(vm).problem).isEqualTo(UiText.Res(R.string.nearby_location_unavailable))
        assertThat(repository.confirmed).isEmpty()
    }

    @Test
    fun `a no to the system's question is an answer`() = runTest {
        val vm = viewModel()

        vm.permissionDenied()

        assertThat(loaded(vm).problem).isEqualTo(UiText.Res(R.string.nearby_location_denied))
        assertThat(location.reads).isEqualTo(0)
    }

    @Test
    fun `the phone is not asked while the operator has it off`() = runTest {
        repository.current = defaultNearbySettings.copy(available = false)
        val vm = viewModel()

        vm.locate()

        assertThat(location.reads).isEqualTo(0)
    }

    // --- conflicts and removal -----------------------------------------------------------------

    @Test
    fun `a change another device got in first is a conflict to read again, not an overwrite`() =
        runTest {
            val vm = viewModel()
            repository.failNext = DataError.Rejected(412, "precondition_failed", "Изменено")
            repository.current = activeNearbySettings

            vm.setEnabled(true)

            assertThat(loaded(vm).settings).isEqualTo(activeNearbySettings)
            assertThat(loaded(vm).problem).isEqualTo(UiText.Res(R.string.nearby_changed_elsewhere))
            assertThat(loaded(vm).saving).isFalse()
        }

    @Test
    fun `an operator who switched it off meanwhile is told so`() = runTest {
        val vm = viewModel()
        repository.failNext = DataError.Rejected(503, "unavailable", "Выключено")
        repository.current = defaultNearbySettings.copy(available = false)

        vm.setEnabled(true)

        assertThat(loaded(vm).settings.available).isFalse()
        assertThat(loaded(vm).problem).isEqualTo(UiText.Res(R.string.nearby_unavailable_now))
    }

    @Test
    fun `a failed change keeps what was there and says why`() = runTest {
        val vm = viewModel()
        repository.failNext = DataError.Offline(IOException())

        vm.setHorizon(30)

        assertThat(loaded(vm).settings.horizonDays).isEqualTo(7)
        assertThat(loaded(vm).problem).isEqualTo(UiText.Res(R.string.error_offline))
    }

    @Test
    fun `removing the area keeps the switch and the kinds`() = runTest {
        repository.current = activeNearbySettings
        val vm = viewModel()

        vm.removeArea()

        assertThat(repository.removed).isEqualTo(1)
        assertThat(loaded(vm).settings.area).isNull()
        assertThat(loaded(vm).settings.enabled).isTrue()
        assertThat(loaded(vm).settings.preferences.purposes).containsExactly("leisure")
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.nearby_area_removed))
    }

    @Test
    fun `there is nothing to remove without an area`() = runTest {
        val vm = viewModel()

        vm.removeArea()

        assertThat(repository.removed).isEqualTo(0)
    }

    @Test
    fun `forgetting removes the area, the switch and the kinds, and shows the empty state`() =
        runTest {
            repository.current = activeNearbySettings
            val vm = viewModel()
            vm.locate()

            vm.forget()

            assertThat(repository.forgotten).isEqualTo(1)
            assertThat(loaded(vm).settings.enabled).isFalse()
            assertThat(loaded(vm).settings.area).isNull()
            assertThat(loaded(vm).settings.preferences.purposes).isEmpty()
            assertThat(loaded(vm).draft).isNull()
            assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.nearby_forgotten))
        }
}
