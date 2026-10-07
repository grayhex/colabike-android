package ru.colabike.app.bikes

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
import ru.colabike.core.model.BikeChange
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.ComponentCatalog
import ru.colabike.core.model.ComponentDictionary
import ru.colabike.core.model.DataError

/** A group of the build that has a key of its own, in the order the page shows it. */
@Immutable data class PartGroup(val key: String, val title: String, val count: Int)

@Immutable
sealed interface BikePartsUiState {
    data object Loading : BikePartsUiState

    data class Failed(val message: UiText) : BikePartsUiState

    /** Not there or not the person's: there is nothing to change. */
    data object Unavailable : BikePartsUiState

    data class Ready(
        val bike: BikeDetail,
        /** The groups the owner can put in order (the ones with a key), in the page's order. */
        val groups: List<PartGroup>,
        /** A new order is on its way to the server. */
        val ordering: Boolean = false,
        /** The server's refusal or a failure, in words; null when there is none. */
        val problem: UiText? = null,
    ) : BikePartsUiState
}

/**
 * The build of one's own bike: its parts by section and group, and the order of the groups. A part
 * is added and changed on a screen of its own; this one reads the bike again whenever the build
 * changed, so it never shows what the server no longer holds.
 */
class BikePartsViewModel(
    private val repository: BikesRepository,
    private val id: BikeId,
    /** The site's dictionary of parts in force now: the names of the groups. */
    private val dictionary: () -> ComponentDictionary = { ComponentCatalog.dictionary },
) : ViewModel() {
    private val mutable = MutableStateFlow<BikePartsUiState>(BikePartsUiState.Loading)
    val state: StateFlow<BikePartsUiState> = mutable.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            repository.changes.collect { change ->
                when {
                    change is BikeChange.Parts && change.id == id -> refresh()
                    change is BikeChange.Saved && change.bike.summary.id == id ->
                        mutable.update {
                            if (it is BikePartsUiState.Ready) ready(change.bike) else it
                        }
                    change is BikeChange.Removed && change.id == id ->
                        mutable.value = BikePartsUiState.Unavailable
                }
            }
        }
    }

    fun load() {
        mutable.value = BikePartsUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    val bike = repository.bike(id)
                    // Only the owner changes a build.
                    if (bike.summary.isOwner) ready(bike) else BikePartsUiState.Unavailable
                } catch (_: DataError.NotFound) {
                    BikePartsUiState.Unavailable
                } catch (e: DataError) {
                    BikePartsUiState.Failed(e.toUiText())
                }
        }
    }

    /** Reads the bike again and keeps what is on screen if that fails. */
    private fun refresh() {
        viewModelScope.launch {
            try {
                val bike = repository.bike(id)
                mutable.update { if (it is BikePartsUiState.Ready) ready(bike) else it }
            } catch (_: DataError) {}
        }
    }

    /**
     * Moves a group one place up or down. The order that goes to the server is the whole list of
     * keys as the page shows it with this one move made: the server replaces the old order.
     */
    fun move(key: String, up: Boolean) {
        val current = mutable.value as? BikePartsUiState.Ready ?: return
        if (current.ordering) return
        val keys = current.groups.map { it.key }.toMutableList()
        val from = keys.indexOf(key)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in keys.indices) return
        keys[from] = keys[to].also { keys[to] = keys[from] }
        mutable.value = current.copy(ordering = true, problem = null)
        viewModelScope.launch {
            try {
                val bike = repository.setGroupOrder(id, keys)
                mutable.value = ready(bike)
            } catch (e: DataError.Rejected) {
                mutable.value = current.copy(ordering = false, problem = refusal(e))
            } catch (_: DataError.NotFound) {
                mutable.value = BikePartsUiState.Unavailable
            } catch (e: DataError) {
                mutable.value = current.copy(ordering = false, problem = e.toUiText())
            }
        }
    }

    private fun refusal(error: DataError.Rejected): UiText =
        if (error.code == EMAIL_NOT_VERIFIED) UiText.Res(R.string.part_needs_email)
        else error.toUiText()

    private fun ready(bike: BikeDetail) = BikePartsUiState.Ready(bike, groupsOf(bike, dictionary()))

    private companion object {
        const val EMAIL_NOT_VERIFIED = "email_verification_required"
    }
}

/**
 * The groups with a key, in the order the page shows them: its sections one after another, the
 * owner's order within. A group with no key (parts grouped by their own category) cannot be put in
 * order: the server takes keys only, so it is not listed.
 */
internal fun groupsOf(
    bike: BikeDetail,
    dictionary: ComponentDictionary = ComponentCatalog.dictionary,
): List<PartGroup> {
    val ordered = orderComponents(bike.components, bike.groupOrder).flatMap { it.components }
    return ordered
        .filter { it.groupId.isNotBlank() }
        .groupBy { it.groupId }
        .map { (key, parts) ->
            PartGroup(key, dictionary.groupName(key) ?: parts.first().category, parts.size)
        }
}
