package ru.colabike.core.network

import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last valid app config on the device: the server's answer as the client read it, the `ETag` to
 * ask with, and when the server last confirmed it. The format has an explicit version, a new one
 * simply does not match the old, and anything unreadable (a half-written file, a format of another
 * build) is a missing cache and is deleted: the app goes on with its built-in defaults. Nothing of
 * a person is in it: the config is the same for everybody.
 */
class AppConfigCache(private val file: File) {
    /** [body] is the config's JSON as the generated client writes it. */
    class Entry(val body: String, val etag: String?, val validatedAtMillis: Long) {
        // The body is the same for everybody, but it is not a line for a log.
        override fun toString() = "AppConfigCache.Entry(validatedAt=$validatedAtMillis)"
    }

    @Serializable
    private class Stored(
        val version: Int,
        val etag: String? = null,
        val validatedAtMillis: Long,
        val body: String,
    )

    fun read(): Entry? {
        if (!file.exists()) return null
        return try {
            val stored = json.decodeFromString(Stored.serializer(), file.readText())
            if (stored.version != VERSION || stored.body.isBlank()) {
                clear()
                null
            } else {
                Entry(stored.body, stored.etag, stored.validatedAtMillis)
            }
        } catch (e: Exception) {
            // Corrupt or foreign: not a reason to stop, nor to keep the file.
            clear()
            null
        }
    }

    /**
     * Replaces the kept config as a whole: written aside and moved over the old file, so a reader
     * (or a crash) never sees half of a new config. A disk that refuses is not an error for the
     * app; the config is just not kept.
     */
    fun write(entry: Entry) {
        try {
            file.parentFile?.mkdirs()
            val aside = File(file.parentFile, file.name + ".tmp")
            aside.writeText(
                json.encodeToString(
                    Stored.serializer(),
                    Stored(VERSION, entry.etag, entry.validatedAtMillis, entry.body),
                )
            )
            if (!aside.renameTo(file)) {
                file.delete()
                if (!aside.renameTo(file)) aside.delete()
            }
        } catch (e: IOException) {
            // Not kept; the next start asks again.
        }
    }

    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    private companion object {
        const val VERSION = 1
        val json = Json { ignoreUnknownKeys = true }
    }
}
