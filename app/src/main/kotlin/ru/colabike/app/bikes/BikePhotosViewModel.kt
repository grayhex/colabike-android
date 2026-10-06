package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** Where a picture that is not on the server yet is. */
@Immutable
sealed interface PendingState {
    /** The file is being made of what the person picked. */
    data object Preparing : PendingState

    /** Ready; the pictures go one after another, this one waits for its turn. */
    data object Waiting : PendingState

    data class Sending(val progress: Float) : PendingState

    /** Not on the server. [retry] is false for a refusal that would be the same again. */
    data class Failed(val message: UiText, val retry: Boolean) : PendingState
}

/** A picture on its way to the server; [id] is ours, the list's order is what the person sees. */
@Immutable data class PendingPhoto(val id: Int, val state: PendingState)

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
 * The pictures of one's own bike: sent one after another with their progress, made the cover,
 * removed. A picture that did not go stays in the list with the reason, to be sent again under the
 * same key (so that a lost answer never makes it twice) or to be dropped. The files and the
 * transfer live here, so a turn of the phone does not stop them; leaving the screen does.
 */
class BikePhotosViewModel(
    private val repository: BikesRepository,
    private val id: BikeId,
    private val files: PhotoFiles,
) : ViewModel() {
    private val mutable = MutableStateFlow<BikePhotosUiState>(BikePhotosUiState.Loading)
    val state: StateFlow<BikePhotosUiState> = mutable.asStateFlow()

    /** One transfer at a time: a phone's connection is better used by one than shared by six. */
    private val turn = Mutex()

    private class Entry(val key: String = UUID.randomUUID().toString()) {
        var file: File? = null
        var job: Job? = null
    }

    private val entries = mutableMapOf<Int, Entry>()
    private var counter = 0
    private var reading: Job? = null

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
                    if (bike.summary.isOwner) BikePhotosUiState.Ready(bike)
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
        taken.forEach { start(it) }
    }

    private fun start(source: String) {
        val item = ++counter
        val entry = Entry().also { entries[item] = it }
        ready {
            it.copy(pending = it.pending + PendingPhoto(item, PendingState.Preparing))
        }
        entry.job = viewModelScope.launch {
            try {
                entry.file = files.import(source)
                send(item, entry)
            } catch (e: PhotoImportException) {
                fail(item, UiText.Res(importMessage(e.reason)), retry = false)
            }
        }
    }

    /** Sends a picture that failed again, under the key it had. */
    fun retry(item: Int) {
        val entry = entries[item] ?: return
        val failed =
            (mutable.value as? BikePhotosUiState.Ready)
                ?.pending
                ?.firstOrNull { it.id == item }
                ?.state as? PendingState.Failed
        if (failed == null || !failed.retry || entry.file == null) return
        ready { it.copy(problem = null) }
        entry.job = viewModelScope.launch { send(item, entry) }
    }

    private suspend fun send(item: Int, entry: Entry) {
        val file = entry.file ?: return
        setPending(item, PendingState.Waiting)
        turn.withLock {
            setPending(item, PendingState.Sending(0f))
            var last = 0f
            try {
                val photo =
                    repository.uploadPhoto(id, file, entry.key) { progress ->
                        // Hundreds of calls for a megabyte: the screen needs a step of a percent.
                        if (progress - last >= PROGRESS_STEP || progress >= 1f) {
                            last = progress
                            setPending(item, PendingState.Sending(progress))
                        }
                    }
                arrived(item, entry, photo)
            } catch (e: CancellationException) {
                throw e
            } catch (_: DataError.NotFound) {
                forget(item, entry)
                mutable.value = BikePhotosUiState.Unavailable
            } catch (e: DataError.Rejected) {
                // A refusal of the picture itself is the same again; one of the person, or of the
                // moment (the address, too many requests), is worth a second try.
                fail(item, refusal(e), retry = e.status !in PICTURE_REFUSALS)
            } catch (e: DataError) {
                fail(item, e.toUiText(), retry = true)
            }
        }
    }

    /**
     * The picture is on the server: it is shown among the others at once, the list is read later.
     */
    private fun arrived(item: Int, entry: Entry, photo: Photo) {
        forget(item, entry)
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

    private fun forget(item: Int, entry: Entry) {
        entries.remove(item)
        entry.file?.let(files::discard)
        entry.file = null
        ready { it.copy(pending = it.pending.filterNot { p -> p.id == item }) }
    }

    private fun setPending(item: Int, state: PendingState) {
        ready { current ->
            current.copy(
                pending = current.pending.map { if (it.id == item) it.copy(state = state) else it }
            )
        }
    }

    private fun fail(item: Int, message: UiText, retry: Boolean) {
        // A picture that cannot be sent again is of no use to keep as a file.
        if (!retry) entries[item]?.let { entry -> entry.file?.let(files::discard) }
        setPending(item, PendingState.Failed(message, retry))
    }

    /** Stops the transfer of a picture, or drops one that did not go. */
    fun cancel(item: Int) {
        val entry = entries[item] ?: return
        entry.job?.cancel()
        forget(item, entry)
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

    override fun onCleared() {
        entries.values.forEach { entry -> entry.file?.let(files::discard) }
        entries.clear()
    }

    private fun refusal(error: DataError): UiText =
        if (error is DataError.Rejected && error.code == EMAIL_NOT_VERIFIED) {
            UiText.Res(R.string.photos_needs_email)
        } else error.toUiText()

    private fun importMessage(reason: PhotoImportException.Reason): Int =
        when (reason) {
            PhotoImportException.Reason.TooLarge -> R.string.photos_import_too_large
            PhotoImportException.Reason.TooSmall -> R.string.photos_import_too_small
            PhotoImportException.Reason.Unreadable -> R.string.photos_import_unreadable
        }

    private companion object {
        const val EMAIL_NOT_VERIFIED = "email_verification_required"
        const val PROGRESS_STEP = 0.01f

        /**
         * Too large (413), not a picture the server reads (415), unreadable (400), no room (409).
         */
        val PICTURE_REFUSALS = setOf(400, 409, 413, 415)
    }
}

/** The bike without a photo; the cover goes to the earliest of the rest, as the server does it. */
private fun BikeDetail.without(photoId: String): BikeDetail {
    val rest = photos.filterNot { it.id == photoId }
    val cover = if (summary.cover?.id == photoId) rest.firstOrNull() else summary.cover
    return copy(photos = rest, summary = summary.copy(cover = cover))
}
