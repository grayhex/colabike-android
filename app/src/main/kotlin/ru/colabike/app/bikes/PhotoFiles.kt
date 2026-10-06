package ru.colabike.app.bikes

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.colabike.core.model.PhotoRules

/** Why a picked picture cannot be sent; the person is told, nothing goes to the server. */
class PhotoImportException(val reason: Reason, cause: Throwable? = null) :
    Exception(reason.name, cause) {
    enum class Reason {
        /** Even made smaller it is over what the server takes. */
        TooLarge,

        /** Under 600 × 400: the server would refuse it, after the whole upload. */
        TooSmall,

        /** Not a picture, or one that cannot be read. */
        Unreadable,
    }
}

/**
 * Makes a file of ours of what the person picked, ready to be sent as it is: a picture the server
 * reads (JPEG, PNG, WebP, within its size) stays the bytes it was, any other (HEIC, a very big
 * photo) is made a JPEG of up to 2400 px, which is all the server would keep of it.
 */
interface PhotoFiles {
    /** [source] is what the picker gave (a `content:` address). Throws [PhotoImportException]. */
    suspend fun import(source: String): File

    /** Forgets a file made by [import]; one that is already gone is no error. */
    fun discard(file: File)
}

/** [PhotoFiles] over the content resolver; the files live in the cache, never outside the app. */
class ContentPhotoFiles(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = PhotoRules.MAX_BYTES,
    /** What is copied at most from a source before it is given up as too large. */
    private val maxSource: Long = MAX_SOURCE_BYTES,
) : PhotoFiles {
    private val directory = File(context.cacheDir, DIRECTORY)

    override suspend fun import(source: String): File =
        withContext(dispatcher) {
            val uri = source.toUri()
            // The picker gives `content:` addresses; a `file:` one would read what is not ours.
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                throw PhotoImportException(PhotoImportException.Reason.Unreadable)
            }
            directory.mkdirs()
            purgeStale()
            val copy = File.createTempFile("pick-", ".bin", directory)
            var result: File? = null
            try {
                copyTo(uri, copy)
                val prepared = prepare(copy)
                result = prepared
                prepared
            } catch (e: IOException) {
                throw PhotoImportException(PhotoImportException.Reason.Unreadable, e)
            } catch (e: SecurityException) {
                // The picker's permission to read the picture is gone.
                throw PhotoImportException(PhotoImportException.Reason.Unreadable, e)
            } catch (e: OutOfMemoryError) {
                throw PhotoImportException(PhotoImportException.Reason.TooLarge)
            } finally {
                if (result !== copy) copy.delete()
            }
        }

    override fun discard(file: File) {
        // Only our own files: a path that is not under the directory is never deleted.
        if (file.parentFile?.canonicalPath == directory.canonicalPath) file.delete()
    }

    private suspend fun copyTo(uri: Uri, target: File) {
        val input =
            context.contentResolver.openInputStream(uri)
                ?: throw PhotoImportException(PhotoImportException.Reason.Unreadable)
        input.use { stream ->
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxSource) {
                        throw PhotoImportException(PhotoImportException.Reason.TooLarge)
                    }
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    /**
     * The file to send: [copy] itself when the server reads it as it is, else a JPEG made of it.
     */
    private fun prepare(copy: File): File {
        val bounds =
            BitmapFactory.Options()
                .apply { inJustDecodeBounds = true }
                .also {
                    BitmapFactory.decodeFile(copy.path, it)
                }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PhotoImportException(PhotoImportException.Reason.Unreadable)
        }
        if (PhotoRules.isTooSmall(bounds.outWidth, bounds.outHeight)) {
            throw PhotoImportException(PhotoImportException.Reason.TooSmall)
        }
        val readable =
            isServerFormat(
                copy.inputStream().use { stream -> ByteArray(HEADER).also { stream.read(it) } }
            ) &&
                copy.length() <= maxBytes &&
                PhotoRules.fitsPixels(bounds.outWidth, bounds.outHeight)
        return if (readable) copy else convert(copy)
    }

    private fun convert(copy: File): File {
        val out = File.createTempFile("photo-", ".jpg", directory)
        try {
            val bitmap =
                try {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(copy)) { decoder, info, _ ->
                        // Software memory: a hardware bitmap cannot be written out again.
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > PhotoRules.MAX_SIDE) {
                            val scale = PhotoRules.MAX_SIDE.toDouble() / longest
                            decoder.setTargetSize(
                                (info.size.width * scale).toInt().coerceAtLeast(1),
                                (info.size.height * scale).toInt().coerceAtLeast(1),
                            )
                        }
                    }
                } catch (e: ImageDecoder.DecodeException) {
                    throw PhotoImportException(PhotoImportException.Reason.Unreadable, e)
                }
            try {
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            } finally {
                bitmap.recycle()
            }
            if (out.length() > maxBytes) {
                throw PhotoImportException(PhotoImportException.Reason.TooLarge)
            }
            return out
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
    }

    /** Files an earlier run left behind (it ended before it could tidy up). */
    private fun purgeStale() {
        val limit = System.currentTimeMillis() - STALE_AFTER_MS
        directory.listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }

    private companion object {
        const val DIRECTORY = "bike-photos"
        const val BUFFER = 64 * 1024
        const val HEADER = 12
        const val QUALITY = 90

        /** More than this is not a photograph of a bike; it is not copied at all. */
        const val MAX_SOURCE_BYTES = 80L * 1024 * 1024
        const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * The server decides by the bytes, not by the name or the declared type: JPEG, PNG and WebP are the
 * only ones it reads (cola `images.ts`).
 */
internal fun isServerFormat(header: ByteArray): Boolean {
    fun starts(vararg bytes: Int) =
        header.size >= bytes.size && bytes.indices.all { header[it] == bytes[it].toByte() }
    val jpeg = starts(0xFF, 0xD8, 0xFF)
    val png = starts(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    // "RIFF" ... "WEBP"
    val webp =
        starts(0x52, 0x49, 0x46, 0x46) &&
            header.size >= 12 &&
            header.copyOfRange(8, 12).contentEquals(byteArrayOf(0x57, 0x45, 0x42, 0x50))
    return jpeg || png || webp
}
