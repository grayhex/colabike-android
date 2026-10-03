package ru.colabike.app.bikes

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
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
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

class BikeDetailViewModel(private val repository: BikesRepository, private val id: BikeId) :
    ViewModel() {
    private val mutableState = MutableStateFlow<BikeDetailUiState>(BikeDetailUiState.Loading)
    val state: StateFlow<BikeDetailUiState> = mutableState.asStateFlow()

    init {
        load()
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
