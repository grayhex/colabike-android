package ru.colabike.app.rides

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.DataError
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RidesRepository

@Immutable
sealed interface RideUiState {
    data object Loading : RideUiState

    data class Loaded(val ride: RideDetail) : RideUiState

    /** [notFound]: private, cancelled, hidden or deleted; a guest may own it. */
    data class Failed(val message: UiText, val notFound: Boolean = false) : RideUiState
}

class RideViewModel(
    private val repository: RidesRepository,
    private val id: RideId,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<RideUiState>(RideUiState.Loading)
    val state: StateFlow<RideUiState> = mutableState.asStateFlow()

    init {
        load()
        // A comment written or removed in the discussion changes the count here.
        viewModelScope.launch {
            commentChanges.collect { change ->
                if (change.target.kind != CommentKind.Ride || change.target.id != id.value) {
                    return@collect
                }
                mutableState.update { current ->
                    if (current !is RideUiState.Loaded) current
                    else {
                        val summary = current.ride.summary
                        current.copy(
                            ride =
                                current.ride.copy(
                                    summary =
                                        summary.copy(
                                            comments =
                                                (summary.comments + change.delta).coerceAtLeast(0)
                                        )
                                )
                        )
                    }
                }
            }
        }
    }

    fun load() {
        mutableState.value = RideUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    RideUiState.Loaded(repository.ride(id))
                } catch (e: DataError) {
                    RideUiState.Failed(e.toUiText(), notFound = e is DataError.NotFound)
                }
        }
    }
}
