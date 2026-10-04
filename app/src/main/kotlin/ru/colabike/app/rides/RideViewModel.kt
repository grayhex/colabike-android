package ru.colabike.app.rides

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import ru.colabike.core.model.RideAnalysis
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

/**
 * The series for the charts, a request of their own after the page: the page never waits for them,
 * and a ride without a track or analysis ([Absent], the server's 404) simply has no section.
 */
@Immutable
sealed interface AnalysisUiState {
    /** Not asked yet: the ride has no route, or the section was not reached. */
    data object NotAsked : AnalysisUiState

    data object Loading : AnalysisUiState

    data class Loaded(val analysis: RideAnalysis) : AnalysisUiState

    data object Absent : AnalysisUiState

    data class Failed(val message: UiText) : AnalysisUiState
}

class RideViewModel(
    private val repository: RidesRepository,
    private val id: RideId,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<RideUiState>(RideUiState.Loading)
    val state: StateFlow<RideUiState> = mutableState.asStateFlow()

    private val mutableAnalysis = MutableStateFlow<AnalysisUiState>(AnalysisUiState.NotAsked)
    val analysis: StateFlow<AnalysisUiState> = mutableAnalysis.asStateFlow()
    private var analysisJob: Job? = null

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

    /**
     * Asks for the charts' series once the page shows a route: a repeat call while the answer is on
     * its way or in, or for a ride with no route, does nothing. After a failure it asks again.
     */
    fun loadAnalysis() {
        val ride = (mutableState.value as? RideUiState.Loaded)?.ride ?: return
        if (ride.route == null) return
        when (mutableAnalysis.value) {
            AnalysisUiState.NotAsked,
            is AnalysisUiState.Failed -> Unit
            else -> return
        }
        mutableAnalysis.value = AnalysisUiState.Loading
        analysisJob = viewModelScope.launch {
            mutableAnalysis.value =
                try {
                    repository.analysis(id)?.let { AnalysisUiState.Loaded(it) }
                        ?: AnalysisUiState.Absent
                } catch (e: DataError) {
                    AnalysisUiState.Failed(e.toUiText())
                }
        }
    }

    fun load() {
        analysisJob?.cancel()
        mutableAnalysis.value = AnalysisUiState.NotAsked
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
