package ru.colabike.app.messages

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Person
import ru.colabike.core.model.UserId

/** The server's limits on a group: two to seven other people, a name of up to 80 characters. */
internal const val GROUP_MIN = 2
internal const val GROUP_MAX = 7
internal const val GROUP_NAME_MAX = 80

@Immutable
data class NewConversationUiState(
    val typed: String = "",
    /** Whether the list is the result of a query (not the people the person follows). */
    val searching: Boolean = false,
    val people: List<Person> = emptyList(),
    val loading: Boolean = true,
    val error: UiText? = null,
    val group: Boolean = false,
    val selected: List<Person> = emptyList(),
    val groupName: String = "",
    /** A channel is being made. */
    val opening: Boolean = false,
    /** Why the last attempt to open one failed; it stays until the next attempt. */
    val openError: UiText? = null,
) {
    val canCreateGroup: Boolean
        get() =
            group &&
                !opening &&
                selected.size in GROUP_MIN..GROUP_MAX &&
                groupName.trim().isNotEmpty()
}

/**
 * Who to write to: the people the person follows, or a search (from two characters, after a pause),
 * and either one dialogue (a tap) or a named group of two to seven. The channel is made by the
 * bridge, which checks again who may write to whom; its refusal is shown in words and nothing is
 * kept locally. A repeated request for the same group carries the same key, so a lost answer does
 * not make a second group.
 */
class NewConversationViewModel(
    private val repository: ChatRepository,
    private val debounceMs: Long = 400,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutable = MutableStateFlow(NewConversationUiState())
    val state: StateFlow<NewConversationUiState> = mutable.asStateFlow()

    private var searchJob: Job? = null
    private var typing: Job? = null
    private var openJob: Job? = null

    /** The key of the group being made: kept while its members and name are the same. */
    private var groupKey: Pair<Pair<String, String>, String>? = null

    private val mutableOpened = MutableStateFlow<ChannelCid?>(null)

    /** The channel that was made or opened; the screen goes there and then [consumed] it. */
    val opened: StateFlow<ChannelCid?> = mutableOpened.asStateFlow()

    init {
        search("")
    }

    fun onQuery(text: String) {
        mutable.update { it.copy(typed = text) }
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(debounceMs)
            search(text)
        }
    }

    fun clearQuery() = onQuery("")

    fun retry() = search(mutable.value.typed)

    fun setGroup(group: Boolean) {
        mutable.update { it.copy(group = group, selected = emptyList(), openError = null) }
    }

    fun onGroupName(name: String) {
        mutable.update { it.copy(groupName = name.take(GROUP_NAME_MAX), openError = null) }
    }

    /** A tap on a person: in a group it selects, otherwise it opens the dialogue. */
    fun onPerson(person: Person) {
        val current = mutable.value
        if (current.opening) return
        if (!current.group) {
            open(ChatChannelKind.Dm, listOf(person), name = null)
            return
        }
        mutable.update {
            val chosen =
                if (it.selected.any { p -> p.id == person.id }) {
                    it.selected.filterNot { p -> p.id == person.id }
                } else if (it.selected.size < GROUP_MAX) {
                    it.selected + person
                } else {
                    it.selected
                }
            it.copy(selected = chosen, openError = null)
        }
    }

    fun createGroup() {
        val current = mutable.value
        if (!current.canCreateGroup) return
        open(ChatChannelKind.Group, current.selected, current.groupName.trim())
    }

    fun consumed() {
        mutableOpened.value = null
    }

    private fun search(text: String) {
        searchJob?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                val answer = repository.people(text)
                mutable.update {
                    it.copy(
                        people = answer.people,
                        searching = answer.searching,
                        loading = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataError) {
                mutable.update { it.copy(loading = false, error = e.chatText()) }
            }
        }
    }

    private fun open(kind: ChatChannelKind, members: List<Person>, name: String?) {
        mutable.update { it.copy(opening = true, openError = null) }
        val key =
            if (kind == ChatChannelKind.Group) {
                val signature = members.joinToString(",") { it.id.value } to name.orEmpty()
                val previous = groupKey
                if (previous != null && previous.first == signature) previous.second
                else newKey().also { groupKey = signature to it }
            } else null
        openJob?.cancel()
        openJob = viewModelScope.launch {
            try {
                val cid =
                    repository.open(
                        kind = kind,
                        members = members.map { UserId(it.id.value) },
                        name = name,
                        key = key,
                    )
                mutable.update { it.copy(opening = false) }
                mutableOpened.value = cid
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataError) {
                mutable.update { it.copy(opening = false, openError = e.openText()) }
            }
        }
    }
}

/** A refusal to make a channel, in words: a 404 means the person cannot be written to. */
internal fun DataError.openText(): UiText =
    when (this) {
        is DataError.NotFound -> UiText.Res(R.string.chat_cannot_write)
        else -> chatText()
    }
