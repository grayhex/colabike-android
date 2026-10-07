package ru.colabike.core.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalChange
import ru.colabike.core.model.JournalId

class JournalPhotosRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val site = TestServer()
    private val journal =
        NetworkJournalRepository(
            site.api.journal,
            site.api.personal,
            site.media,
            Dispatchers.IO,
            site.api::journalWithNulls,
            site.api::journalUploading,
        )
    private val entry = JournalId("c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f")
    private val photo = "0b7e6a52-1c2d-4e3f-8a9b-1c2d3e4f5a6b"
    private val key = "0c7d9a1e-5b2f-4c3d-8e4a-1f2b3c4d5e6f"

    @After fun close() = site.close()

    private val photoJson = """{"id":"$photo","url":"/api/photos/0b7e6a52?width=640"}"""

    private fun picture(bytes: Int = 300_000): File =
        folder.newFile("p.jpg").also { it.writeBytes(ByteArray(bytes) { i -> (i % 251).toByte() }) }

    @Test
    fun `a picture goes as the raw file with its key, and its progress is told`() = runBlocking {
        site.json(201, photoJson)
        val file = picture()
        val progress = mutableListOf<Float>()

        journal.changes.test {
            val sent = journal.uploadPhoto(entry, file, key) { progress += it }

            assertThat(sent.id).isEqualTo(photo)
            assertThat(sent.url).startsWith(site.config.siteUrl)
            assertThat(awaitItem()).isEqualTo(JournalChange.Photos(entry))
        }

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/journal/${entry.value}/photos")
        assertThat(request.headers["Idempotency-Key"]).isEqualTo(key)
        assertThat(request.headers["Content-Type"]).startsWith("application/octet-stream")
        assertThat(request.body!!.toByteArray()).isEqualTo(file.readBytes())
        assertThat(progress).isNotEmpty()
        assertThat(progress.last()).isEqualTo(1f)
        assertThat(progress).isInOrder()
    }

    @Test
    fun `a refusal is explained and told to nobody`() = runBlocking {
        site.json(403, error("email_verification_required"))

        journal.changes.test {
            val result = runCatching { journal.uploadPhoto(entry, picture(), key) }

            val refused = result.exceptionOrNull() as DataError.Rejected
            assertThat(refused.code).isEqualTo("email_verification_required")
            expectNoEvents()
        }
    }

    @Test
    fun `too many photos is a conflict with the server's words`() = runBlocking {
        site.json(409, error("conflict", "Не больше 8 фото в записи."))

        val result = runCatching { journal.uploadPhoto(entry, picture(), key) }

        val refused = result.exceptionOrNull() as DataError.Rejected
        assertThat(refused.status).isEqualTo(409)
        assertThat(refused.userMessage).isEqualTo("Не больше 8 фото в записи.")
    }

    @Test
    fun `a cancelled upload stops the transfer instead of waiting for the answer`() = runBlocking {
        site.server.enqueue(
            MockResponse.Builder().code(201).headersDelay(30, TimeUnit.SECONDS).build()
        )
        val file = picture(2_000_000)
        val upload =
            launch(Dispatchers.Default, CoroutineStart.UNDISPATCHED) {
                journal.uploadPhoto(entry, file, key)
            }
        // The request has reached the server: the transfer is on its way.
        assertThat(site.server.takeRequest(10, TimeUnit.SECONDS)).isNotNull()
        val before = System.nanoTime()

        upload.cancelAndJoin()

        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)
        assertThat(tookMs).isLessThan(5_000)
    }

    @Test
    fun `removing a photo is DELETE and the entry's photos are announced as changed`() = runTest {
        site.json(204, "")

        journal.changes.test {
            journal.deletePhoto(entry, photo)

            assertThat(awaitItem()).isEqualTo(JournalChange.Photos(entry))
        }
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath)
            .isEqualTo("/api/v1/journal/${entry.value}/photos/$photo")
    }

    @Test
    fun `a malformed id is not found without a request`() = runTest {
        val result = runCatching { journal.deletePhoto(entry, "nope") }

        assertThat(result.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }
}
