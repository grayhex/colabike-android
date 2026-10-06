package ru.colabike.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.bikes.ContentPhotoFiles
import ru.colabike.app.bikes.PhotoImportException
import ru.colabike.app.bikes.isServerFormat

/** The picker, as far as the app sees it: `content:` addresses that open to bytes. */
class PickerProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val bytes = pictures[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())
        val served = File.createTempFile("served", ".bin", context!!.cacheDir)
        served.writeBytes(bytes)
        return ParcelFileDescriptor.open(served, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ) = 0

    companion object {
        val pictures = mutableMapOf<String, ByteArray>()
    }
}

/** What is made of a picture the person picked, before it is sent (docs/adr/0022). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoFilesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun picture(
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 120, 200))
        return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
    }

    @Before
    fun picker() {
        PickerProvider.pictures.clear()
        File(context.cacheDir, "bike-photos").deleteRecursively()
        Robolectric.buildContentProvider(PickerProvider::class.java).create("media")
    }

    private fun pick(name: String, bytes: ByteArray): String {
        PickerProvider.pictures[name] = bytes
        return "content://media/picker/0/$name"
    }

    private fun files(maxBytes: Long = 10L * 1024 * 1024, maxSource: Long = 80L * 1024 * 1024) =
        ContentPhotoFiles(context, kotlinx.coroutines.Dispatchers.Unconfined, maxBytes, maxSource)

    private fun reason(block: suspend () -> Unit): PhotoImportException.Reason {
        var caught: PhotoImportException? = null
        kotlinx.coroutines.runBlocking {
            try {
                block()
            } catch (e: PhotoImportException) {
                caught = e
            }
        }
        return checkNotNull(caught) { "nothing was refused" }.reason
    }

    private fun leftovers() = File(context.cacheDir, "bike-photos").listFiles().orEmpty().toList()

    @Test
    fun `a jpeg the server reads is sent as it is, byte for byte`() = runTest {
        val bytes = picture(1200, 800)

        val file = files().import(pick("jpeg", bytes))

        assertThat(file.readBytes()).isEqualTo(bytes)
        assertThat(file.parentFile?.name).isEqualTo("bike-photos")
    }

    @Test
    fun `a png and a webp are kept as they are`() = runTest {
        val png = picture(900, 600, Bitmap.CompressFormat.PNG)
        val webp = picture(900, 600, Bitmap.CompressFormat.WEBP_LOSSY)

        assertThat(files().import(pick("png", png)).readBytes()).isEqualTo(png)
        assertThat(files().import(pick("webp", webp)).readBytes()).isEqualTo(webp)
    }

    @Test
    fun `a picture under 600 by 400 is refused here, not after the whole upload`() {
        val small = pick("small", picture(500, 300))
        val thin = pick("thin", picture(2000, 300))

        assertThat(reason { files().import(small) }).isEqualTo(PhotoImportException.Reason.TooSmall)
        assertThat(reason { files().import(thin) }).isEqualTo(PhotoImportException.Reason.TooSmall)
        assertThat(leftovers()).isEmpty()
    }

    @Test
    fun `the short side may be held either way`() = runTest {
        val upright = picture(400, 600)

        assertThat(files().import(pick("upright", upright)).readBytes()).isEqualTo(upright)
    }

    @Test
    fun `what is not a picture is refused and leaves nothing behind`() {
        val text = pick("text", "это не картинка".toByteArray())

        assertThat(reason { files().import(text) })
            .isEqualTo(PhotoImportException.Reason.Unreadable)
        assertThat(leftovers()).isEmpty()
    }

    @Test
    fun `only an address the picker gives is read`() {
        assertThat(reason { files().import("file:///data/data/ru.colabike/session") })
            .isEqualTo(PhotoImportException.Reason.Unreadable)
        assertThat(reason { files().import("https://example.test/photo.jpg") })
            .isEqualTo(PhotoImportException.Reason.Unreadable)
    }

    @Test
    fun `an address that cannot be opened is unreadable, not a crash`() {
        val uri = "content://media/picker/0/missing"

        assertThat(reason { files().import(uri) }).isEqualTo(PhotoImportException.Reason.Unreadable)
    }

    @Test
    fun `a source far beyond any photograph is not even copied`() {
        val big = pick("big", picture(1200, 800))

        assertThat(reason { files(maxSource = 1_000).import(big) })
            .isEqualTo(PhotoImportException.Reason.TooLarge)
        assertThat(leftovers()).isEmpty()
    }

    @Test
    fun `a picture over the size is remade as a jpeg that fits`() = runTest {
        // Bytes after the end of a PNG are ignored by readers: a small picture that weighs a lot.
        val heavy = picture(1200, 800, Bitmap.CompressFormat.PNG) + ByteArray(300_000)
        val limit = 100_000L

        val file = files(maxBytes = limit).import(pick("heavy", heavy))

        assertThat(file.length()).isAtMost(limit)
        assertThat(isServerFormat(file.readBytes().copyOf(12))).isTrue()
        assertThat(file.readBytes().take(3))
            .containsExactly(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        // Only the new file is left: the copy of the picked one is gone.
        assertThat(leftovers()).containsExactly(file)
    }

    @Test
    fun `a picture that stays over the size even remade is too large`() {
        val heavy = picture(1200, 800, Bitmap.CompressFormat.PNG) + ByteArray(300_000)

        assertThat(reason { files(maxBytes = 100).import(pick("heavier", heavy)) })
            .isEqualTo(PhotoImportException.Reason.TooLarge)
        assertThat(leftovers()).isEmpty()
    }

    @Test
    fun `only the files made here are ever deleted`() = runTest {
        val made = files().import(pick("made", picture(1200, 800)))
        val foreign = File(context.cacheDir, "not-ours.txt").also { it.writeText("x") }

        files().discard(foreign)
        files().discard(made)

        assertThat(foreign.exists()).isTrue()
        assertThat(made.exists()).isFalse()
    }

    @Test
    fun `the server's formats are told by the bytes, not by a name`() {
        fun bytes(vararg values: Int) = ByteArray(12) { values.getOrElse(it) { 0 }.toByte() }

        assertThat(isServerFormat(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isTrue()
        assertThat(isServerFormat(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))).isTrue()
        assertThat(
                isServerFormat(bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50))
            )
            .isTrue()
        // HEIC: "ftypheic" after a size.
        assertThat(
                isServerFormat(bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63))
            )
            .isFalse()
        // RIFF but not WebP (a WAV).
        assertThat(
                isServerFormat(bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x41, 0x56, 0x45))
            )
            .isFalse()
        assertThat(isServerFormat(ByteArray(0))).isFalse()
    }
}
