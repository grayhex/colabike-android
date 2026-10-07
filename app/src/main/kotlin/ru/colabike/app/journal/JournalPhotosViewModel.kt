package ru.colabike.app.journal

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
import ru.colabike.app.bikes.PendingPhoto
import ru.colabike.app.bikes.PendingState
import ru.colabike.app.bikes.PhotoFiles
import ru.colabike.app.bikes.PhotoQueue
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalChange
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.Photo
import ru.colabike.core.model.PhotoRules

@Immutable
sealed interface JournalPhotosUiState {
    data object Loading : JournalPhotosUiState

    data class Failed(val message: UiText) : JournalPhotosUiState

    /** Not there or not the person's: there is nothing to change. */
    data object Unavailable : JournalPhotosUiState

    data class Ready(
        val entry: JournalEntry,
        val pending: List<PendingPhoto> = emptyList(),
        /** The photo being removed; one change at a time. */
        val busy: String? = null,
        /** The photo the person is asked to confirm the removal of. */
        val confirmingDelete: String? = null,
        /** The server's refusal or a failure, in words; null when there is none. */
        val problem: UiText? = null,
        /** How many of what was picked did not fit, null when all did. */
        val skipped: Int? = null,
    ) : JournalPhotosUiState {
        /** What the entry holds or is about to: the pictures that are on the way and may arrive. */
        val occupied: Int
            get() = entry.photos.size + pending.count { it.state !is PendingState.Failed }

        val slotsLeft: Int
            get() = (PhotoRules.MAX_PER_ENTRY - occupied).coerceAtLeast(0)

        val sending: Boolean
            get() = pending.any { it.state !is PendingState.Failed }
    }
}

/**
 * The pictures of one's own journal entry: sent one after another with their progress
 * ([PhotoQueue]) and removed. There is no cover: an entry shows its pictures in the order they were
 * added. A draft may have pictures as well.
 */
class JournalPhotosViewModel(
    private val repository: JournalRepository,
    private val id: JournalId,
    files: PhotoFiles,
) : ViewModel() {
    private val mutable = MutableStateFlow<JournalPhotosUiState>(JournalPhotosUiState.Loading)
    val state: StateFlow<JournalPhotosUiState> = mutable.asStateFlow()

    private var reading: Job? = null

    private val queue =
        PhotoQueue(
            scope = viewModelScope,
            files = files,
            // The server sets no floor for the size of an entry's picture.
            checkMinimum = false,
            upload = { file, key, progress -> repository.uploadPhoto(id, file, key, progress) },
            onChange = { pending -> ready { it.copy(pending = pending) } },
            onArrived = ::arrived,
            onUnavailable = { mutable.value = JournalPhotosUiState.Unavailable },
            explain = ::refusal,
        )

    init {
        load()
        viewModelScope.launch {
            repository.changes.collect { change ->
                when {
                    change is JournalChange.Photos && change.id == id -> refresh()
                    change is JournalChange.Saved && change.entry.summary.id == id ->
                        ready { it.copy(entry = change.entry) }
                    change is JournalChange.Removed && change.id == id ->
                        mutable.value = JournalPhotosUiState.Unavailable
                }
            }
        }
    }

    fun load() {
        mutable.value = JournalPhotosUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    val entry = repository.entry(id)
                    // Only the author is given the version a change names, and only they change it.
                    if (entry.version != null) JournalPhotosUiState.Ready(entry, queue.pending)
                    else JournalPhotosUiState.Unavailable
                } catch (_: DataError.NotFound) {
                    JournalPhotosUiState.Unavailable
                } catch (e: DataError) {
                    JournalPhotosUiState.Failed(e.toUiText())
                }
        }
    }

    /** Reads the entry again and keeps what is on screen if that fails. */
    private fun refresh() {
        reading?.cancel()
        reading = viewModelScope.launch {
            try {
                val entry = repository.entry(id)
                ready { it.copy(entry = entry) }
            } catch (_: DataError) {}
        }
    }

    private fun ready(change: (JournalPhotosUiState.Ready) -> JournalPhotosUiState.Ready) {
        mutable.update { if (it is JournalPhotosUiState.Ready) change(it) else it }
    }

    /**
     * Takes what the picker gave, as many as the entry has room for, and starts sending them. The
     * rest is not taken, and [JournalPhotosUiState.Ready.skipped] says how many.
     */
    fun add(sources: List<String>) {
        val current = mutable.value as? JournalPhotosUiState.Ready ?: return
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

    /** The picture is on the server: it is shown among the others at once. */
    private fun arrived(photo: Photo) {
        ready { current ->
            val entry = current.entry
            if (entry.photos.any { it.id == photo.id }) current
            else current.copy(entry = entry.copy(photos = entry.photos + photo))
        }
    }

    fun askDelete(photoId: String) = ready {
        if (it.busy == null) it.copy(confirmingDelete = photoId, problem = null) else it
    }

    fun cancelDelete() = ready { it.copy(confirmingDelete = null) }

    fun confirmDelete() {
        val current = mutable.value as? JournalPhotosUiState.Ready ?: return
        val photoId = current.confirmingDelete ?: return
        mutable.value = current.copy(confirmingDelete = null, busy = photoId, problem = null)
        viewModelScope.launch {
            try {
                repository.deletePhoto(id, photoId)
                ready {
                    it.copy(
                        entry =
                            it.entry.copy(
                                photos = it.entry.photos.filterNot { p -> p.id == photoId }
                            ),
                        busy = null,
                    )
                }
            } catch (_: DataError.NotFound) {
                mutable.value = JournalPhotosUiState.Unavailable
            } catch (e: DataError) {
                ready { it.copy(busy = null, problem = refusal(e)) }
            }
        }
    }

    override fun onCleared() = queue.close()

    private fun refusal(error: DataError): UiText =
        if (error is DataError.Rejected && error.code == EMAIL_NOT_VERIFIED) {
            UiText.Res(R.string.journal_photos_needs_email)
        } else error.toUiText()

    private companion object {
        const val EMAIL_NOT_VERIFIED = "email_verification_required"
    }
}
