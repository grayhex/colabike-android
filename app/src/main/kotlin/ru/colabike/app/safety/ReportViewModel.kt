package ru.colabike.app.safety

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ReportReason
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.SafetyRepository

@Immutable
data class ReportUiState(
    val reason: ReportReason? = null,
    val sending: Boolean = false,
    /** The report is with the moderators; the dialog thanks the person and closes on a tap. */
    val sent: Boolean = false,
    val error: UiText? = null,
) {
    val canSend: Boolean
        get() = reason != null && !sending && !sent
}

/**
 * A report of one thing (cola #354, docs/adr/0021): the reason, and the report. Sending it again is
 * not an error on the server (the moderators get it once), so a retry after a lost answer is safe.
 */
class ReportViewModel(
    private val safety: SafetyRepository,
    private val target: ReportTarget,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = mutableState.asStateFlow()

    fun choose(reason: ReportReason) = mutableState.update {
        if (it.sending || it.sent) it else it.copy(reason = reason, error = null)
    }

    fun send() {
        val current = state.value
        val reason = current.reason ?: return
        if (!current.canSend) return
        mutableState.value = current.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                safety.report(target, reason)
                mutableState.update { it.copy(sending = false, sent = true) }
            } catch (e: DataError) {
                mutableState.update { it.copy(sending = false, error = e.toUiText()) }
            }
        }
    }

    /** The dialog was closed: the next one starts from nothing. */
    fun reset() {
        mutableState.value = ReportUiState()
    }
}
