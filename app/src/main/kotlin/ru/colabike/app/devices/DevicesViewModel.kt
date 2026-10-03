package ru.colabike.app.devices

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
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.DataError

@Immutable
sealed interface DevicesUiState {
    data object Loading : DevicesUiState

    data class Failed(val message: UiText) : DevicesUiState

    data class Loaded(
        val sessions: List<AccountSession>,
        /** The session the person is asked to confirm ending; null when no question is open. */
        val confirming: AccountSession? = null,
        /** Sessions whose end was asked for and not answered yet. */
        val ending: Set<String> = emptySet(),
        /** The outcome of the last action in words, announced to TalkBack; null when none. */
        val notice: UiText? = null,
    ) : DevicesUiState
}

/**
 * The account's sessions and ending one of them. Ending the session of this very device is not
 * offered here: that is signing out, which also clears what the device holds.
 */
class DevicesViewModel(private val sessions: AccountSessionsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<DevicesUiState>(DevicesUiState.Loading)
    val state: StateFlow<DevicesUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = DevicesUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    DevicesUiState.Loaded(sessions.sessions())
                } catch (e: DataError) {
                    DevicesUiState.Failed(e.toUiText())
                }
        }
    }

    /** Asks the person to confirm; nothing is sent yet. This device cannot be ended here. */
    fun askToEnd(id: String) = updateLoaded { loaded ->
        val session = loaded.sessions.firstOrNull { it.id == id && !it.isCurrent }
        if (session == null || id in loaded.ending) loaded
        else loaded.copy(confirming = session, notice = null)
    }

    fun dismissQuestion() = updateLoaded { it.copy(confirming = null) }

    fun confirmEnd() {
        val loaded = state.value as? DevicesUiState.Loaded ?: return
        val session = loaded.confirming ?: return
        mutableState.value =
            loaded.copy(confirming = null, ending = loaded.ending + session.id, notice = null)
        viewModelScope.launch {
            val outcome =
                try {
                    sessions.revoke(session.id)
                    Outcome.Ended
                } catch (e: DataError.NotFound) {
                    // Gone already (ended elsewhere meanwhile): the list should say so too.
                    Outcome.AlreadyGone
                } catch (e: DataError) {
                    Outcome.Failed(e.toUiText())
                }
            updateLoaded { current ->
                val still = current.ending - session.id
                when (outcome) {
                    Outcome.Ended ->
                        current.copy(
                            sessions = current.sessions.filterNot { it.id == session.id },
                            ending = still,
                            notice = UiText.Res(R.string.devices_ended),
                        )
                    Outcome.AlreadyGone ->
                        current.copy(
                            sessions = current.sessions.filterNot { it.id == session.id },
                            ending = still,
                            notice = UiText.Res(R.string.devices_already_gone),
                        )
                    is Outcome.Failed -> current.copy(ending = still, notice = outcome.message)
                }
            }
        }
    }

    private fun updateLoaded(transform: (DevicesUiState.Loaded) -> DevicesUiState.Loaded) {
        mutableState.update { if (it is DevicesUiState.Loaded) transform(it) else it }
    }

    private sealed interface Outcome {
        data object Ended : Outcome

        data object AlreadyGone : Outcome

        data class Failed(val message: UiText) : Outcome
    }
}
