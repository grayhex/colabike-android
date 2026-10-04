package ru.colabike.core.network

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import ru.colabike.core.model.ConfigAssets

/**
 * The pictures of the app config in a folder of their own. They are fetched only from the site
 * itself, only as images and not larger than [MAX_BYTES], by a client that carries no session (the
 * pictures are public, so nothing of a person goes with the request), and written aside before they
 * are moved into place, so a picture is whole or absent. The file name is a digest of the address,
 * which changes with every new picture.
 */
class FileConfigAssets(
    private val folder: File,
    private val calls: Call.Factory,
    siteUrl: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ConfigAssets {
    private val site: HttpUrl = requireNotNull(siteUrl.toHttpUrlOrNull()) { "site address" }

    override suspend fun prefetch(urls: List<String>): Boolean =
        withContext(dispatcher) {
            urls.all { url ->
                try {
                    keep(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }
        }

    override suspend fun retainOnly(urls: List<String>) {
        withContext(dispatcher) {
            val wanted = urls.map(::nameOf).toSet()
            folder.listFiles()?.forEach { if (it.name !in wanted) it.delete() }
        }
    }

    override fun fileOf(url: String): File? =
        File(folder, nameOf(url)).takeIf { it.isFile && it.length() > 0 }

    private fun keep(url: String): Boolean {
        val target = File(folder, nameOf(url))
        if (target.isFile && target.length() > 0) return true
        val address = url.toHttpUrlOrNull() ?: return false
        // Only the site's own pictures, however the config came to name another.
        if (
            address.scheme != site.scheme ||
                !address.host.equals(site.host, ignoreCase = true) ||
                address.port != site.port
        ) {
            return false
        }
        folder.mkdirs()
        val aside = File(folder, nameOf(url) + ".part")
        try {
            calls.newCall(Request.Builder().url(address).get().build()).execute().use { response ->
                if (!response.isSuccessful) return false
                val type = response.header("Content-Type").orEmpty().substringBefore(';').trim()
                if (!type.startsWith("image/", ignoreCase = true)) return false
                val body = response.body
                val declared = body.contentLength()
                if (declared > MAX_BYTES) return false
                var total = 0L
                aside.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) return false
                            out.write(buffer, 0, read)
                        }
                    }
                }
                if (total == 0L) return false
            }
            return aside.renameTo(target)
        } catch (e: IOException) {
            return false
        } finally {
            aside.delete()
        }
    }

    private fun nameOf(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    companion object {
        /** A picture of the config is a screen's worth of WebP; more than this is not one. */
        const val MAX_BYTES = 8L * 1024 * 1024
        private const val BUFFER = 16 * 1024
    }
}
