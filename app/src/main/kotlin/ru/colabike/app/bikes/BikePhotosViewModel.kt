package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Photo
import ru.colabike.core.model.PhotoRules

@Immutable
sealed interface BikePhotosUiState {
    data object Loading : BikePhotosUiState

    data class Failed(val message: UiText) : BikePhotosUiState

    /** Not there or not the person's: there is nothing to change. */
    data object Unavailable : BikePhotosUiState

    data class Ready(
        val bike: BikeDetail,
        val pending: List<PendingPhoto> = emptyList(),
        /** The photo being made the cover or removed; one change at a time. */
        val busy: String? = null,
        /** The photo the person is asked to confirm the removal of. */
        val confirmingDelete: String? = null,
        /** The server's refusal or a failure, in words; null when there is none. */
        val problem: UiText? = null,
        /** How many of what was picked did not fit, null when all did. */
        val skipped: Int? = null,
    ) : BikePhotosUiState {
        /** What the bike holds or is about to: the pictures that are on the way and may arrive. */
        val occupied: Int
            get() = bike.photos.size + pending.count { it.state !is PendingState.Failed }

        val slotsLeft: Int
            get() = (PhotoRules.MAX_PER_BIKE - occupied).coerceAtLeast(0)

        val sending: Boolean
            get() = pending.any { it.state !is PendingState.Failed }
    }
}

/**
 * The pictures of one's own bike: sent one after another with their progress ([PhotoQueue]), made
 * the cover, removed. The files and the transfer live here, so a turn of the phone does not stop
 * them; leaving the screen does.
 */
class BikePhotosViewModel(
    private val repository: BikesRepository,
    private val id: BikeId,
    private val files: PhotoFiles,
) : ViewModel() {
    private val mutable = MutableStateFlow<BikePhotosUiState>(BikePhotosUiState.Loading)
    val state: StateFlow<BikePhotosUiState> = mutable.asStateFlow()

    private var reading: Job? = null

    private val queue =
        PhotoQueue(
            scope = viewModelScope,
            files = files,
            // The server takes no bike picture under 600 x 400.
            checkMinimum = true,
            upload = { file, key, progress -> repository.uploadPhoto(id, file, key, progress) },
            onChange = { pending -> ready { it.copy(pending = pending) } },
            onArrived = ::arrived,
            onUnavailable = { mutable.value = BikePhotosUiState.Unavailable },
            explain = ::refusal,
        )

    init {
        load()
        viewModelScope.launch {
            repository.changes.collect { change ->
                when {
                    change is BikeChange.Photos && change.id == id -> refresh()
                    change is BikeChange.Saved && change.bike.summary.id == id ->
                        mutable.update {
                            if (it is BikePhotosUiState.Ready) it.copy(bike = change.bike) else it
                        }
                    change is BikeChange.Removed && change.id == id ->
                        mutable.value = BikePhotosUiState.Unavailable
                }
            }
        }
    }

    fun load() {
        mutable.value = BikePhotosUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    val bike = repository.bike(id)
                    // Only the owner changes the pictures.
                    if (bike.summary.isOwner) BikePhotosUiState.Ready(bike, queue.pending)
                    else BikePhotosUiState.Unavailable
                } catch (_: DataError.NotFound) {
                    BikePhotosUiState.Unavailable
                } catch (e: DataError) {
                    BikePhotosUiState.Failed(e.toUiText())
                }
        }
    }

    /** Reads the bike again and keeps what is on screen if that fails. */
    private fun refresh() {
        reading?.cancel()
        reading = viewModelScope.launch {
            try {
                val bike = repository.bike(id)
                ready { it.copy(bike = bike) }
            } catch (_: DataError) {}
        }
    }

    private fun ready(change: (BikePhotosUiState.Ready) -> BikePhotosUiState.Ready) {
        mutable.update { if (it is BikePhotosUiState.Ready) change(it) else it }
    }

    /**
     * Takes what the picker gave, as many as the bike has room for, and starts sending them. The
     * rest is not taken, and [BikePhotosUiState.Ready.skipped] says how many.
     */
    fun add(sources: List<String>) {
        val current = mutable.value as? BikePhotosUiState.Ready ?: return
        val taken = sources.take(current.slotsLeft)
        val skipped = sources.size - taken.size
        ready { it.copy(problem = null, skipped = skipped.takeIf { n -> n > 0 }) }
        queue.add(taken)
    }

    /** Sends a picture that failed again, under the key it had. */
    fun retry(item: Int) {
        ready { it.copy(problem = null) }
        queue.retry(item)
    }

    /** Stops the transfer of a picture, or drops one that did not go. */
    fun cancel(item: Int) = queue.cancel(item)

    /**
     * The picture is on the server: it is shown among the others at once, the list is read later.
     */
    private fun arrived(photo: Photo) {
        ready { current ->
            val bike = current.bike
            if (bike.photos.any { it.id == photo.id }) current
            else
                current.copy(
                    bike =
                        bike.copy(
                            photos = bike.photos + photo,
                            // The first picture is the cover, as the server makes it.
                            summary = bike.summary.copy(cover = bike.summary.cover ?: photo),
                        )
                )
        }
    }

    /** Makes a photo the cover; the bike comes back with its photos in order. */
    fun setCover(photoId: String) {
        val current = mutable.value as? BikePhotosUiState.Ready ?: return
        if (current.busy != null || current.bike.summary.cover?.id == photoId) return
        mutable.value = current.copy(busy = photoId, problem = null)
        viewModelScope.launch {
            try {
                val bike = repository.setCover(id, photoId)
                ready { it.copy(bike = bike, busy = null) }
            } catch (e: DataError.NotFound) {
                mutable.value = BikePhotosUiState.Unavailable
            } catch (e: DataError) {
                ready { it.copy(busy = null, problem = refusal(e)) }
            }
        }
    }

    fun askDelete(photoId: String) = ready {
        if (it.busy == null) it.copy(confirmingDelete = photoId, problem = null) else it
    }

    fun cancelDelete() = ready { it.copy(confirmingDelete = null) }

    fun confirmDelete() {
        val current = mutable.value as? BikePhotosUiState.Ready ?: return
        val photoId = current.confirmingDelete ?: return
        mutable.value = current.copy(confirmingDelete = null, busy = photoId, problem = null)
        viewModelScope.launch {
            try {
                repository.deletePhoto(id, photoId)
                ready { it.copy(bike = it.bike.without(photoId), busy = null) }
            } catch (e: DataError.NotFound) {
                mutable.value = BikePhotosUiState.Unavailable
            } catch (e: DataError) {
                ready { it.copy(busy = null, problem = refusal(e)) }
            }
        }
    }

    override fun onCleared() = queue.close()

    private fun refusal(error: DataError): UiText =
        if (error is DataError.Rejected && error.code == EMAIL_NOT_VERIFIED) {
            UiText.Res(R.string.photos_needs_email)
        } else error.toUiText()

    private companion object {
        const val EMAIL_NOT_VERIFIED = "email_verification_required"
    }
}

/** The bike without a photo; the cover goes to the earliest of the rest, as the server does it. */
private fun BikeDetail.without(photoId: String): BikeDetail {
    val rest = photos.filterNot { it.id == photoId }
    val cover = if (summary.cover?.id == photoId) rest.firstOrNull() else summary.cover
    return copy(photos = rest, summary = summary.copy(cover = cover))
}
