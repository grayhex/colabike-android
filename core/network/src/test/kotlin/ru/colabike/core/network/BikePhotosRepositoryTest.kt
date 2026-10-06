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
import ru.colabike.core.model.BikeChange
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError

class BikePhotosRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val site = TestServer()
    private val bikes =
        NetworkBikesRepository(
            site.api.bikes,
            site.api.search,
            site.media,
            Dispatchers.IO,
            site.api::bikesWithNulls,
            site.api::bikesUploading,
        )
    private val bike = BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
    private val photo = "0b7e6a52-1c2d-4e3f-8a9b-1c2d3e4f5a6b"
    private val key = "0c7d9a1e-5b2f-4c3d-8e4a-1f2b3c4d5e6f"

    @After fun close() = site.close()

    private val photoJson =
        """{"id":"$photo","isCover":true,"url":"/api/photos/0b7e6a52?width=640","sourcePageUrl":null}"""

    private fun picture(bytes: Int = 300_000): File =
        folder.newFile("p.jpg").also { it.writeBytes(ByteArray(bytes) { i -> (i % 251).toByte() }) }

    @Test
    fun `a picture goes as the raw file with its key, and its progress is told`() = runBlocking {
        site.json(201, photoJson)
        val file = picture()
        val progress = mutableListOf<Float>()

        bikes.changes.test {
            val sent = bikes.uploadPhoto(bike, file, key) { progress += it }

            assertThat(sent.id).isEqualTo(photo)
            assertThat(sent.url).startsWith(site.config.siteUrl)
            assertThat(awaitItem()).isEqualTo(BikeChange.Photos(bike))
        }

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/${bike.value}/photos")
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

        bikes.changes.test {
            val result = runCatching { bikes.uploadPhoto(bike, picture(), key) }

            val refused = result.exceptionOrNull() as DataError.Rejected
            assertThat(refused.code).isEqualTo("email_verification_required")
            expectNoEvents()
        }
    }

    @Test
    fun `too many photos is a conflict with the server's words`() = runBlocking {
        site.json(409, error("conflict", "Не больше 12 фото на велосипед."))

        val result = runCatching { bikes.uploadPhoto(bike, picture(), key) }

        val refused = result.exceptionOrNull() as DataError.Rejected
        assertThat(refused.status).isEqualTo(409)
        assertThat(refused.userMessage).isEqualTo("Не больше 12 фото на велосипед.")
    }

    @Test
    fun `a cancelled upload stops the transfer instead of waiting for the answer`() = runBlocking {
        site.server.enqueue(
            MockResponse.Builder().code(201).headersDelay(30, TimeUnit.SECONDS).build()
        )
        val file = picture(2_000_000)
        val upload =
            launch(Dispatchers.Default, CoroutineStart.UNDISPATCHED) {
                bikes.uploadPhoto(bike, file, key)
            }
        // The request has reached the server: the transfer is on its way.
        assertThat(site.server.takeRequest(10, TimeUnit.SECONDS)).isNotNull()
        val before = System.nanoTime()

        upload.cancelAndJoin()

        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)
        assertThat(tookMs).isLessThan(5_000)
    }

    @Test
    fun `a cover is a PUT without a body and the bike comes back`() = runTest {
        site.json(200, site.fixture("bike.json"), "ETag", "\"bike-v4\"")

        bikes.changes.test {
            val saved = bikes.setCover(bike, photo)

            assertThat(saved.version).isEqualTo("\"bike-v4\"")
            assertThat(awaitItem()).isEqualTo(BikeChange.Saved(saved))
        }
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.url.encodedPath)
            .isEqualTo("/api/v1/bikes/${bike.value}/photos/$photo/cover")
    }

    @Test
    fun `removing a photo is DELETE and the bike's photos are announced as changed`() = runTest {
        site.json(204, "")

        bikes.changes.test {
            bikes.deletePhoto(bike, photo)

            assertThat(awaitItem()).isEqualTo(BikeChange.Photos(bike))
        }
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/${bike.value}/photos/$photo")
    }

    @Test
    fun `a malformed id is not found without a request`() = runTest {
        val result = runCatching { bikes.deletePhoto(bike, "nope") }

        assertThat(result.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }
}
