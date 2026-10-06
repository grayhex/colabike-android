package ru.colabike.app.bikes

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
import ru.colabike.core.model.BikeChange
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.DataError
import ru.colabike.core.model.LikeState

@Immutable
sealed interface BikeDetailUiState {
    data object Loading : BikeDetailUiState

    data class Loaded(
        val bike: BikeDetail,
        /** A like is on its way to the server: the heart already shows the new state. */
        val liking: Boolean = false,
        /** The server refused the last like and the heart went back; say so. */
        val likeError: UiText? = null,
    ) : BikeDetailUiState

    /** [notFound]: the bike is hidden, deleted or private to someone else; a guest may own it. */
    data class Failed(val message: UiText, val notFound: Boolean = false) : BikeDetailUiState
}

class BikeDetailViewModel(
    private val repository: BikesRepository,
    private val id: BikeId,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<BikeDetailUiState>(BikeDetailUiState.Loading)
    val state: StateFlow<BikeDetailUiState> = mutableState.asStateFlow()

    init {
        load()
        // A comment written or removed in the discussion changes the count here.
        viewModelScope.launch {
            commentChanges.collect { change ->
                if (change.target.kind == CommentKind.Bike && change.target.id == id.value) {
                    update { loaded ->
                        val summary = loaded.bike.summary
                        loaded.copy(
                            bike =
                                loaded.bike.copy(
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
        // The bike saved in the editor is the page now; one deleted there is no longer here.
        viewModelScope.launch {
            repository.changes.collect { change ->
                when {
                    change is BikeChange.Saved && change.bike.summary.id == id ->
                        mutableState.value = BikeDetailUiState.Loaded(change.bike)
                    // The build changed: read the page again, and keep it on screen meanwhile.
                    change is BikeChange.Parts && change.id == id -> refresh()
                    change is BikeChange.Removed && change.id == id ->
                        mutableState.value =
                            BikeDetailUiState.Failed(
                                DataError.NotFound().toUiText(),
                                notFound = true,
                            )
                }
            }
        }
        // A like given elsewhere (the list) shows here without loading the page again.
        viewModelScope.launch {
            repository.likeChanges.collect { change ->
                if (change.id == id) update { it.withLike(change.state) }
            }
        }
    }

    fun load() {
        mutableState.value = BikeDetailUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    BikeDetailUiState.Loaded(repository.bike(id))
                } catch (e: DataError) {
                    BikeDetailUiState.Failed(e.toUiText(), notFound = e is DataError.NotFound)
                }
        }
    }

    /** Reads the page again without the spinner; a failure leaves the page as it was. */
    private fun refresh() {
        viewModelScope.launch {
            try {
                val bike = repository.bike(id)
                update { it.copy(bike = bike) }
            } catch (_: DataError) {}
        }
    }

    /**
     * Likes or unlikes at once and lets the server have the last word: its answer sets the heart
     * and the count, a refusal puts the old ones back and says why. Not for one's own bike or a
     * private one, which the server does not let be liked.
     */
    fun toggleLike() {
        val loaded = state.value as? BikeDetailUiState.Loaded ?: return
        val summary = loaded.bike.summary
        if (loaded.liking || summary.isOwner || !summary.isPublic) return
        val before = LikeState(summary.liked, summary.likes)
        val target = !before.liked
        mutableState.value =
            loaded
                .withLike(
                    LikeState(target, (before.likes + if (target) 1 else -1).coerceAtLeast(0))
                )
                .copy(liking = true, likeError = null)
        viewModelScope.launch {
            try {
                val answer = repository.setLiked(id, target)
                update { it.withLike(answer).copy(liking = false) }
            } catch (e: DataError) {
                update { it.withLike(before).copy(liking = false, likeError = e.toUiText()) }
            }
        }
    }

    private fun update(transform: (BikeDetailUiState.Loaded) -> BikeDetailUiState.Loaded) {
        mutableState.update { if (it is BikeDetailUiState.Loaded) transform(it) else it }
    }

    private fun BikeDetailUiState.Loaded.withLike(like: LikeState) =
        copy(bike = bike.copy(summary = bike.summary.copy(liked = like.liked, likes = like.likes)))
}
