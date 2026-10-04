package ru.colabike.app.messages

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.UserId

@Immutable
sealed interface WriteState {
    data object Idle : WriteState

    data object Opening : WriteState

    data class Failed(val message: UiText) : WriteState
}

/**
 * "Write" on a person's page: asks the bridge for a dialogue with that person (the same one every
 * time) and hands over the channel to open. The refusal comes in words: the person may not be
 * written to, the e-mail is unconfirmed, the chat is off.
 */
class WriteViewModel(private val chat: ChatRepository) : ViewModel() {
    private val mutable = MutableStateFlow<WriteState>(WriteState.Idle)
    val state: StateFlow<WriteState> = mutable.asStateFlow()

    private val mutableOpened = MutableStateFlow<ChannelCid?>(null)
    val opened: StateFlow<ChannelCid?> = mutableOpened.asStateFlow()

    fun write(person: UserId) {
        if (mutable.value == WriteState.Opening) return
        mutable.value = WriteState.Opening
        viewModelScope.launch {
            try {
                mutableOpened.value = chat.open(ChatChannelKind.Dm, listOf(person))
                mutable.value = WriteState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataError) {
                mutable.value = WriteState.Failed(e.openText())
            }
        }
    }

    fun consumed() {
        mutableOpened.value = null
    }
}
