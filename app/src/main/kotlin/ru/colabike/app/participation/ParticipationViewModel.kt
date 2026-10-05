package ru.colabike.app.participation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ParticipationOutcome
import ru.colabike.core.model.ParticipationRepository
import ru.colabike.core.model.ParticipationResponse
import ru.colabike.core.model.RideParticipation

@Immutable
sealed interface ParticipationUiState {
    data object Loading : ParticipationUiState

    data class Failed(val message: UiText) : ParticipationUiState

    /**
     * Not there, not the person's to see, or withdrawn. The server answers all of these the same
     * (404), and so does the screen: nothing of what was shown before is kept.
     */
    data object Unavailable : ParticipationUiState

    data class Loaded(
        val participation: RideParticipation,
        /** The answer on its way; one at a time. */
        val sending: ParticipationResponse? = null,
        /** What the last answer came to, in words, announced to TalkBack. */
        val notice: UiText? = null,
        val problem: UiText? = null,
        /**
         * The answer was not taken because the date or the terms changed while the person chose:
         * [participation] is what stands now and the choice is theirs again.
         */
        val changedMeanwhile: Boolean = false,
    ) : ParticipationUiState
}

/**
 * The person's part in one date of a plan. What the server says now is what is shown: the terms,
 * what changed since the person answered, and which answers it will take. An answer is sent only
 * after a tap, for the date and the edition of terms on the screen; if they are no longer the
 * current ones the answer is not taken and the new state comes back for the person to decide again.
 * Nothing sends an answer by itself: not a rotation, a restart, a return or a new session.
 */
class ParticipationViewModel(
    private val repository: ParticipationRepository,
    private val rideId: String,
    private val occurrenceAt: Instant?,
) : ViewModel() {
    private val mutable = MutableStateFlow<ParticipationUiState>(ParticipationUiState.Loading)
    val state: StateFlow<ParticipationUiState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = ParticipationUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    ParticipationUiState.Loaded(repository.get(rideId, occurrenceAt))
                } catch (_: DataError.NotFound) {
                    ParticipationUiState.Unavailable
                } catch (e: DataError) {
                    ParticipationUiState.Failed(e.toUiText())
                }
        }
    }

    fun respond(response: ParticipationResponse) {
        val current = mutable.value as? ParticipationUiState.Loaded ?: return
        val p = current.participation
        // Only what the server said it will take, and only for a date there is.
        if (current.sending != null || response !in p.allowed) return
        val date = p.scheduledAt ?: return
        mutable.update { current.copy(sending = response, notice = null, problem = null) }
        viewModelScope.launch {
            try {
                when (
                    val outcome = repository.respond(rideId, response, date, p.agreement.revision)
                ) {
                    is ParticipationOutcome.Saved ->
                        mutable.value =
                            ParticipationUiState.Loaded(
                                outcome.participation,
                                notice = UiText.Res(saved(response)),
                            )
                    is ParticipationOutcome.Changed ->
                        mutable.value =
                            outcome.current?.let {
                                ParticipationUiState.Loaded(it, changedMeanwhile = true)
                            } ?: ParticipationUiState.Unavailable
                }
            } catch (_: DataError.NotFound) {
                mutable.value = ParticipationUiState.Unavailable
            } catch (e: DataError) {
                mutable.value = current.copy(sending = null, problem = e.toUiText())
            }
        }
    }

    private fun saved(response: ParticipationResponse) =
        when (response) {
            ParticipationResponse.Accepted -> R.string.participation_saved_accepted
            ParticipationResponse.Maybe -> R.string.participation_saved_maybe
            ParticipationResponse.Declined -> R.string.participation_saved_declined
        }
}
