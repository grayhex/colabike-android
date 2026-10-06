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
import ru.colabike.core.model.PersonSummary
import ru.colabike.core.model.SafetyRepository
import ru.colabike.core.model.UserId

@Immutable
data class BlockedUiState(
    val people: List<PersonSummary> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    val moreError: UiText? = null,
    /** People whose unblocking was asked for and not answered yet. */
    val unblocking: Set<String> = emptySet(),
    /** The outcome of the last unblocking that failed, announced to TalkBack. */
    val notice: UiText? = null,
)

/** The people the viewer blocked, page by page, and unblocking them (docs/adr/0021). */
class BlockedViewModel(private val safety: SafetyRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(BlockedUiState())
    val state: StateFlow<BlockedUiState> = mutableState.asStateFlow()

    init {
        load()
        // A person blocked or unblocked on their own page shows here when the list is next opened;
        // one unblocked from elsewhere while this screen is open leaves it at once.
        viewModelScope.launch {
            safety.blockChanges.collect { change ->
                if (!change.blocked)
                    mutableState.update {
                        it.copy(people = it.people.filterNot { p -> p.person.id == change.id })
                    }
            }
        }
    }

    fun load() {
        mutableState.value = BlockedUiState()
        viewModelScope.launch {
            try {
                val page = safety.blocked(null)
                mutableState.update {
                    it.copy(people = page.items, nextCursor = page.nextCursor, loading = false)
                }
            } catch (e: DataError) {
                mutableState.update { it.copy(loading = false, error = e.toUiText()) }
            }
        }
    }

    fun loadMore() {
        val current = state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        mutableState.update { it.copy(loadingMore = true, moreError = null) }
        viewModelScope.launch {
            try {
                val page = safety.blocked(cursor)
                mutableState.update {
                    it.copy(
                        people = (it.people + page.items).distinctBy { p -> p.person.id },
                        nextCursor = page.nextCursor,
                        loadingMore = false,
                    )
                }
            } catch (e: DataError) {
                mutableState.update { it.copy(loadingMore = false, moreError = e.toUiText()) }
            }
        }
    }

    fun unblock(id: UserId) {
        if (id.value in state.value.unblocking) return
        mutableState.update { it.copy(unblocking = it.unblocking + id.value, notice = null) }
        viewModelScope.launch {
            try {
                safety.setBlocked(id, blocked = false)
                mutableState.update {
                    it.copy(
                        people = it.people.filterNot { p -> p.person.id == id },
                        unblocking = it.unblocking - id.value,
                    )
                }
            } catch (e: DataError) {
                mutableState.update {
                    it.copy(unblocking = it.unblocking - id.value, notice = e.toUiText())
                }
            }
        }
    }
}
