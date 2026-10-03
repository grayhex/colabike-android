package ru.colabike.app.people

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
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.PersonSummary

/** Whose list: the people who follow a person, or the people a person follows. */
enum class PeopleListKind {
    Followers,
    Following,
}

@Immutable
data class PeopleListUiState(
    val people: List<PersonSummary> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: UiText? = null,
    val moreError: UiText? = null,
)

/** Keyset paging over `/users/{ref}/followers` or `/following`, one page at a time. */
class PeopleListViewModel(
    private val repository: PeopleRepository,
    private val ref: String,
    private val kind: PeopleListKind,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PeopleListUiState())
    val state: StateFlow<PeopleListUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = PeopleListUiState()
        viewModelScope.launch {
            try {
                val page = fetch(null)
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
                val page = fetch(cursor)
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

    private suspend fun fetch(cursor: String?) =
        when (kind) {
            PeopleListKind.Followers -> repository.followers(ref, cursor)
            PeopleListKind.Following -> repository.following(ref, cursor)
        }
}
