package ru.colabike.app.intents

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.IntentsRepository
import ru.colabike.core.model.RideIntent

@Immutable
sealed interface IntentUiState {
    data object Loading : IntentUiState

    data class Failed(val message: UiText) : IntentUiState

    /**
     * Gone, closed, or never the person's to see. The server answers all of these the same (404),
     * and so does the screen: nothing of what was there is kept or guessed.
     */
    data object Unavailable : IntentUiState

    data class Loaded(
        val intent: RideIntent,
        /** A cancel or a delete is on its way. */
        val busy: Boolean = false,
        val problem: UiText? = null,
        /** The person deleted it: the screen closes. */
        val deleted: Boolean = false,
    ) : IntentUiState
}

/**
 * One intention. Someone else's is shown only while it is open and published; the person's own, in
 * any state. Cancelling closes it (it stays in the person's list), deleting removes it.
 */
class IntentViewModel(private val repository: IntentsRepository, private val id: String) :
    ViewModel() {
    private val mutable = MutableStateFlow<IntentUiState>(IntentUiState.Loading)
    val state: StateFlow<IntentUiState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = IntentUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    IntentUiState.Loaded(repository.get(id))
                } catch (_: DataError.NotFound) {
                    IntentUiState.Unavailable
                } catch (e: DataError) {
                    IntentUiState.Failed(e.toUiText())
                }
        }
    }

    private var seen = false

    /**
     * The page is in front again. The first time it is already loading; later (back from the form)
     * it is read again without a spinner, and a failure keeps what is shown.
     */
    fun resumed() {
        if (!seen) {
            seen = true
            return
        }
        if (mutable.value !is IntentUiState.Loaded) return
        viewModelScope.launch {
            try {
                val fresh = repository.get(id)
                mutable.update {
                    if (it is IntentUiState.Loaded && !it.busy) it.copy(intent = fresh) else it
                }
            } catch (_: DataError.NotFound) {
                mutable.value = IntentUiState.Unavailable
            } catch (_: DataError) {
                // What is shown stays; the next visit reads again.
            }
        }
    }

    fun cancel() = act { repository.cancel(id).let { Outcome.Changed(it) } }

    fun delete() = act {
        repository.delete(id)
        Outcome.Deleted
    }

    private fun act(block: suspend () -> Outcome) {
        val current = mutable.value as? IntentUiState.Loaded ?: return
        if (current.busy) return
        mutable.update { current.copy(busy = true, problem = null) }
        viewModelScope.launch {
            try {
                when (val outcome = block()) {
                    is Outcome.Changed -> mutable.value = IntentUiState.Loaded(outcome.intent)
                    Outcome.Deleted -> mutable.value = current.copy(busy = false, deleted = true)
                }
            } catch (_: DataError.NotFound) {
                // It was removed meanwhile (on the site, or by a repeat): nothing is left to show.
                mutable.value = IntentUiState.Unavailable
            } catch (e: DataError) {
                mutable.value =
                    current.copy(
                        busy = false,
                        problem =
                            if (e is DataError.Rejected && e.status == 409) {
                                UiText.Res(R.string.intent_conflict)
                            } else {
                                e.toUiText()
                            },
                    )
            }
        }
    }

    private sealed interface Outcome {
        data class Changed(val intent: RideIntent) : Outcome

        data object Deleted : Outcome
    }
}
