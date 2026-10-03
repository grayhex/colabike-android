package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError

@Immutable
sealed interface BikeDetailUiState {
    data object Loading : BikeDetailUiState

    data class Loaded(val bike: BikeDetail) : BikeDetailUiState

    /** [notFound]: the bike is hidden, deleted or private to someone else; a guest may own it. */
    data class Failed(val message: UiText, val notFound: Boolean = false) : BikeDetailUiState
}

class BikeDetailViewModel(private val repository: BikesRepository, private val id: BikeId) :
    ViewModel() {
    private val mutableState = MutableStateFlow<BikeDetailUiState>(BikeDetailUiState.Loading)
    val state: StateFlow<BikeDetailUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = BikeDetailUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    BikeDetailUiState.Loaded(repository.bike(id))
                } catch (e: DataError) {
                    BikeDetailUiState.Failed(e.toUiText(), notFound = e is DataError.NotFound)
                }
        }
    }
}
