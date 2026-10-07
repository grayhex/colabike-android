package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalChange
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalSummary

/** The journal as the page of a bike shows it: the latest entry, not the whole list. */
@Immutable
sealed interface JournalPreviewUiState {
    data object Loading : JournalPreviewUiState

    /** [latest] is null when the bike has no entry that this viewer may see. */
    data class Ready(val latest: JournalSummary?) : JournalPreviewUiState

    /** The section failed; the rest of the page stays as it is. */
    data class Failed(val message: UiText) : JournalPreviewUiState
}

/**
 * The latest entry of a bike's journal for the page of the bike. It asks for one entry only (the
 * owner's list includes drafts, everyone else's what is published and public) and asks again when
 * an entry of this bike is written, changed or deleted through the repository, so a record added
 * from the page shows at once on coming back.
 */
class BikeJournalPreviewViewModel(
    private val repository: JournalRepository,
    private val bike: BikeId,
) : ViewModel() {
    private val mutable = MutableStateFlow<JournalPreviewUiState>(JournalPreviewUiState.Loading)
    val state: StateFlow<JournalPreviewUiState> = mutable.asStateFlow()

    private var request: Job? = null

    init {
        load()
        viewModelScope.launch {
            repository.changes.collect { change ->
                when (change) {
                    is JournalChange.Saved -> if (change.entry.summary.bike.id == bike) refresh()
                    // The change names no bike: asking again is one small request.
                    is JournalChange.Removed -> refresh()
                    is JournalChange.Photos -> Unit
                }
            }
        }
    }

    /** Starts over with the spinner: the first load, or "try again" after a failure. */
    fun load() {
        mutable.value = JournalPreviewUiState.Loading
        ask(keepOnFailure = false)
    }

    /** Asks again without the spinner; a failure leaves what is shown as it was. */
    private fun refresh() = ask(keepOnFailure = true)

    private fun ask(keepOnFailure: Boolean) {
        request?.cancel()
        request = viewModelScope.launch {
            try {
                val page = repository.ofBike(bike, limit = 1)
                mutable.value = JournalPreviewUiState.Ready(page.items.firstOrNull())
            } catch (e: DataError) {
                if (!keepOnFailure) mutable.value = JournalPreviewUiState.Failed(e.toUiText())
            }
        }
    }
}
