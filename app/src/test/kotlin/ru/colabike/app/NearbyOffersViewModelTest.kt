package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.nearby.NearbyOffersUiState
import ru.colabike.app.nearby.NearbyOffersViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NearbyOffer
import ru.colabike.core.model.NearbyOffers
import ru.colabike.core.model.NearbyOffersState
import ru.colabike.core.model.NearbyReason

class NearbyOffersViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeNearby()
    private val offer = NearbyOffer(PreviewData.plannedRide, setOf(NearbyReason.Nearby))

    private fun loaded(vm: NearbyOffersViewModel) = vm.state.value as NearbyOffersUiState.Loaded

    @Test
    fun `the offers are read when the screen opens, and the state says why a list is empty`() =
        runTest {
            repository.offers = NearbyOffers(NearbyOffersState.NoArea, emptyList())

            val vm = NearbyOffersViewModel(repository)

            assertThat(loaded(vm).offers.state).isEqualTo(NearbyOffersState.NoArea)
            assertThat(loaded(vm).offers.items).isEmpty()
            assertThat(repository.offerCalls).isEqualTo(1)
        }

    @Test
    fun `a failed read says why and reading again recovers`() = runTest {
        repository.offersError = DataError.Offline(IOException())

        val vm = NearbyOffersViewModel(repository)
        assertThat(vm.state.value)
            .isEqualTo(NearbyOffersUiState.Failed(UiText.Res(R.string.error_offline)))

        repository.offersError = null
        repository.offers = NearbyOffers(NearbyOffersState.Ready, listOf(offer))
        vm.load()

        assertThat(loaded(vm).offers.items).containsExactly(offer)
    }

    @Test
    fun `a refresh that fails keeps the list that was there`() = runTest {
        repository.offers = NearbyOffers(NearbyOffersState.Ready, listOf(offer))
        val vm = NearbyOffersViewModel(repository)

        repository.offersError = DataError.Offline(IOException())
        vm.refresh()

        assertThat(loaded(vm).offers.items).containsExactly(offer)
        assertThat(loaded(vm).refreshError).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(loaded(vm).refreshing).isFalse()
    }

    @Test
    fun `a refresh replaces the list`() = runTest {
        val vm = NearbyOffersViewModel(repository)
        assertThat(loaded(vm).offers.items).isEmpty()

        repository.offers = NearbyOffers(NearbyOffersState.Ready, listOf(offer))
        vm.refresh()

        assertThat(loaded(vm).offers.items).containsExactly(offer)
        assertThat(loaded(vm).refreshError).isNull()
    }
}
