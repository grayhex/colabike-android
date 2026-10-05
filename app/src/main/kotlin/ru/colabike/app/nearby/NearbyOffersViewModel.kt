package ru.colabike.app.nearby

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NearbyOffers
import ru.colabike.core.model.NearbyRepository

@Immutable
sealed interface NearbyOffersUiState {
    data object Loading : NearbyOffersUiState

    data class Failed(val message: UiText) : NearbyOffersUiState

    /** [offers] explain themselves: the state says why the list is empty and what to do. */
    data class Loaded(
        val offers: NearbyOffers,
        val refreshing: Boolean = false,
        /** A refresh that failed; the list that was there stays. */
        val refreshError: UiText? = null,
    ) : NearbyOffersUiState
}

/**
 * The rides on now in the person's area, on request: nothing is sent and nothing is recorded, and
 * the answer names no place and no distance. Not remembered between visits.
 */
class NearbyOffersViewModel(private val repository: NearbyRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<NearbyOffersUiState>(NearbyOffersUiState.Loading)
    val state: StateFlow<NearbyOffersUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = NearbyOffersUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    NearbyOffersUiState.Loaded(repository.offers())
                } catch (e: DataError) {
                    NearbyOffersUiState.Failed(e.toUiText())
                }
        }
    }

    fun refresh() {
        val current = state.value as? NearbyOffersUiState.Loaded ?: return load()
        if (current.refreshing) return
        mutableState.value = current.copy(refreshing = true, refreshError = null)
        viewModelScope.launch {
            mutableState.value =
                try {
                    NearbyOffersUiState.Loaded(repository.offers())
                } catch (e: DataError) {
                    current.copy(refreshing = false, refreshError = e.toUiText())
                }
        }
    }
}
