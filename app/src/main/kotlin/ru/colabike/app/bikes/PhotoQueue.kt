package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Photo

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

/**
 * The pictures a person picked, on their way to the server: made into files, then sent one after
 * another with their progress. A picture that did not go stays in the list with the reason, to be
 * sent again under the same key (so that a lost answer never makes it twice) or to be dropped. The
 * files and the transfers live as long as [scope], so a turn of the phone does not stop them;
 * leaving the screen does. The queue knows nothing of what the pictures belong to: [upload] sends
 * one, [onArrived] is told when it is on the server, and [onChange] is given the whole list of what
 * is still on its way every time it changes.
 */
internal class PhotoQueue(
    private val scope: CoroutineScope,
    private val files: PhotoFiles,
    /** Whether a picture has to be as large as a bike's (the server checks that for bikes only). */
    private val checkMinimum: Boolean,
    private val upload: suspend (file: File, key: String, onProgress: (Float) -> Unit) -> Photo,
    private val onChange: (List<PendingPhoto>) -> Unit,
    private val onArrived: (Photo) -> Unit,
    /** The thing the pictures belong to is not there (any more). */
    private val onUnavailable: () -> Unit,
    /** The words for a refusal that is not the same for every screen. */
    private val explain: (DataError) -> UiText = { it.toUiText() },
) {
    private class Entry(val id: Int, val key: String = UUID.randomUUID().toString()) {
        var state: PendingState = PendingState.Preparing
        var file: File? = null
        var job: Job? = null
    }

    /** One transfer at a time: a phone's connection is better used by one than shared by six. */
    private val turn = Mutex()

    // The progress of a transfer comes from another thread than the screen's own.
    private val lock = Any()
    private val entries = LinkedHashMap<Int, Entry>()
    private var counter = 0

    /** What is still on its way, in the order the pictures were picked. */
    val pending: List<PendingPhoto>
        get() = synchronized(lock) { snapshot() }

    /** Sending now or waiting for a turn: what takes a place from the next picture. */
    val busyCount: Int
        get() = synchronized(lock) { entries.values.count { it.state !is PendingState.Failed } }

    private fun snapshot() = entries.values.map { PendingPhoto(it.id, it.state) }

    private fun change(entry: Entry, state: PendingState) {
        synchronized(lock) {
            if (entries[entry.id] !== entry) return
            entry.state = state
            onChange(snapshot())
        }
    }

    /** Starts to make files of [sources] and to send them. */
    fun add(sources: List<String>) {
        sources.forEach { source ->
            val entry =
                synchronized(lock) {
                    Entry(++counter).also {
                        entries[it.id] = it
                        onChange(snapshot())
                    }
                }
            entry.job = scope.launch {
                try {
                    entry.file = files.import(source, checkMinimum)
                    send(entry)
                } catch (e: PhotoImportException) {
                    fail(entry, UiText.Res(importMessage(e.reason)), retry = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Whatever else the platform throws at a file it cannot read is no crash.
                    fail(entry, UiText.Res(R.string.photos_import_unreadable), retry = false)
                }
            }
        }
    }

    /** Sends a picture that failed again, under the key it had. */
    fun retry(id: Int) {
        val entry = synchronized(lock) { entries[id] } ?: return
        val failed = entry.state as? PendingState.Failed ?: return
        if (!failed.retry || entry.file == null) return
        entry.job = scope.launch { send(entry) }
    }

    /** Stops the transfer of a picture, or drops one that did not go. */
    fun cancel(id: Int) {
        val entry = synchronized(lock) { entries[id] } ?: return
        entry.job?.cancel()
        forget(entry)
    }

    /** The screen is gone: what was not sent is of no use. */
    fun close() {
        val all = synchronized(lock) { entries.values.toList().also { entries.clear() } }
        all.forEach { entry -> entry.file?.let(files::discard) }
    }

    private suspend fun send(entry: Entry) {
        val file = entry.file ?: return
        change(entry, PendingState.Waiting)
        turn.withLock {
            change(entry, PendingState.Sending(0f))
            var last = 0f
            try {
                val photo =
                    upload(file, entry.key) { progress ->
                        // Hundreds of calls for a megabyte: the screen needs a step of a percent.
                        if (progress - last >= PROGRESS_STEP || progress >= 1f) {
                            last = progress
                            change(entry, PendingState.Sending(progress))
                        }
                    }
                forget(entry)
                onArrived(photo)
            } catch (e: CancellationException) {
                throw e
            } catch (_: DataError.NotFound) {
                forget(entry)
                onUnavailable()
            } catch (e: DataError.Rejected) {
                // A refusal of the picture itself is the same again; one of the person, or of the
                // moment (the address, too many requests), is worth a second try.
                fail(entry, explain(e), retry = e.status !in PICTURE_REFUSALS)
            } catch (e: DataError) {
                fail(entry, explain(e), retry = true)
            }
        }
    }

    private fun forget(entry: Entry) {
        synchronized(lock) {
            if (entries.remove(entry.id) == null) return
            onChange(snapshot())
        }
        entry.file?.let(files::discard)
        entry.file = null
    }

    private fun fail(entry: Entry, message: UiText, retry: Boolean) {
        // A picture that cannot be sent again is of no use to keep as a file.
        if (!retry) entry.file?.let(files::discard)
        change(entry, PendingState.Failed(message, retry))
    }

    private fun importMessage(reason: PhotoImportException.Reason): Int =
        when (reason) {
            PhotoImportException.Reason.TooLarge -> R.string.photos_import_too_large
            PhotoImportException.Reason.TooSmall -> R.string.photos_import_too_small
            PhotoImportException.Reason.Unreadable -> R.string.photos_import_unreadable
        }

    private companion object {
        const val PROGRESS_STEP = 0.01f

        /**
         * Too large (413), not a picture the server reads (415), unreadable (400), no room (409).
         */
        val PICTURE_REFUSALS = setOf(400, 409, 413, 415)
    }
}
