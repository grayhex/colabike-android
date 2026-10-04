package ru.colabike.app.journal

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
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalStatus

@Immutable
sealed interface JournalUiState {
    data object Loading : JournalUiState

    data class Loaded(
        val entry: JournalEntry,
        /** Saved by the viewer, as far as this session knows; null is "not known". */
        val saved: Boolean? = null,
        /** A save or un-save is on its way to the server: the bookmark already shows the result. */
        val saving: Boolean = false,
        /** The server refused the last change and the bookmark went back; say so. */
        val saveError: UiText? = null,
    ) : JournalUiState {
        /** Only a published public entry can be saved (the API says 404 to the rest). */
        val canSave: Boolean
            get() = entry.summary.status == JournalStatus.Published && entry.summary.isPublic
    }

    /** [notFound]: hidden, deleted, a draft or a private bike; a guest may own it. */
    data class Failed(val message: UiText, val notFound: Boolean = false) : JournalUiState
}

class JournalViewModel(
    private val repository: JournalRepository,
    private val id: JournalId,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<JournalUiState>(JournalUiState.Loading)
    val state: StateFlow<JournalUiState> = mutableState.asStateFlow()

    init {
        load()
        // A comment written or removed in the discussion changes the count here.
        viewModelScope.launch {
            commentChanges.collect { change ->
                if (change.target.kind == CommentKind.Journal && change.target.id == id.value) {
                    update { loaded ->
                        val summary = loaded.entry.summary
                        loaded.copy(
                            entry =
                                loaded.entry.copy(
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
        // Saved or un-saved elsewhere (a list), shown here without loading the entry again.
        viewModelScope.launch {
            repository.savedChanges.collect { change ->
                if (change.id == id) update { it.copy(saved = change.saved) }
            }
        }
    }

    fun load() {
        mutableState.value = JournalUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    JournalUiState.Loaded(repository.entry(id), saved = repository.isSaved(id))
                } catch (e: DataError) {
                    JournalUiState.Failed(e.toUiText(), notFound = e is DataError.NotFound)
                }
        }
    }

    /**
     * Saves or un-saves at once and lets the server have the last word: its answer sets the
     * bookmark, a refusal puts the old one back and says why.
     */
    fun toggleSaved() {
        val loaded = state.value as? JournalUiState.Loaded ?: return
        if (loaded.saving || !loaded.canSave) return
        val before = loaded.saved
        val target = before != true
        mutableState.value = loaded.copy(saved = target, saving = true, saveError = null)
        viewModelScope.launch {
            try {
                val answer = repository.setSaved(id, target)
                update { it.copy(saved = answer, saving = false) }
            } catch (e: DataError) {
                update { it.copy(saved = before, saving = false, saveError = e.toUiText()) }
            }
        }
    }

    private fun update(transform: (JournalUiState.Loaded) -> JournalUiState.Loaded) {
        mutableState.update { if (it is JournalUiState.Loaded) transform(it) else it }
    }
}
