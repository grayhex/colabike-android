package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileConfigAssetsTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val site = server.url("/").toString().trimEnd('/')
    private val dir by lazy { folder.newFolder("assets") }
    private val assets by lazy {
        FileConfigAssets(dir, OkHttpClient(), site, Dispatchers.Unconfined)
    }

    @After fun close() = server.close()

    private fun image(
        bytes: ByteArray = ByteArray(2048) { it.toByte() },
        type: String = "image/webp",
    ) =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", type)
            .body(Buffer().write(bytes))
            .build()

    private fun url(path: String) = "$site$path"

    @Test
    fun `a picture of the site is kept whole and found again, and asked for once`() = runTest {
        val bytes = ByteArray(4096) { (it * 7).toByte() }
        server.enqueue(image(bytes))

        assertThat(assets.prefetch(listOf(url("/api/assets/a1?width=1920")))).isTrue()
        assertThat(assets.prefetch(listOf(url("/api/assets/a1?width=1920")))).isTrue()

        assertThat(server.requestCount).isEqualTo(1)
        assertThat(assets.fileOf(url("/api/assets/a1?width=1920"))!!.readBytes()).isEqualTo(bytes)
        // Another size of the same picture is another picture.
        assertThat(assets.fileOf(url("/api/assets/a1?width=1280"))).isNull()
    }

    @Test
    fun `nothing of a person goes with the request`() = runTest {
        server.enqueue(image())

        assets.prefetch(listOf(url("/api/assets/a1")))

        val request = server.takeRequest()
        assertThat(request.headers["Authorization"]).isNull()
        assertThat(request.headers["Cookie"]).isNull()
    }

    @Test
    fun `what is not an image, is too large, is missing or comes from another host is not kept`() =
        runTest {
            server.enqueue(image(type = "text/html"))
            server.enqueue(MockResponse.Builder().code(404).body("no").build())
            server.enqueue(image(bytes = ByteArray((FileConfigAssets.MAX_BYTES + 1).toInt())))
            server.enqueue(image(bytes = ByteArray(0)))

            assertThat(assets.prefetch(listOf(url("/html")))).isFalse()
            assertThat(assets.prefetch(listOf(url("/gone")))).isFalse()
            assertThat(assets.prefetch(listOf(url("/huge")))).isFalse()
            assertThat(assets.prefetch(listOf(url("/empty")))).isFalse()
            // Another host is not even asked.
            val before = server.requestCount
            assertThat(assets.prefetch(listOf("https://evil.example/a.webp"))).isFalse()
            assertThat(server.requestCount).isEqualTo(before)

            for (path in listOf("/html", "/gone", "/huge", "/empty")) {
                assertThat(assets.fileOf(url(path))).isNull()
            }
            // And no half-written file is left lying about.
            assertThat(dir.listFiles().orEmpty().toList()).isEmpty()
        }

    @Test
    fun `one missing picture makes the set not whole`() = runTest {
        server.enqueue(image())
        server.enqueue(MockResponse.Builder().code(500).build())

        val whole = assets.prefetch(listOf(url("/a"), url("/b")))

        assertThat(whole).isFalse()
    }

    @Test
    fun `a connection that breaks is not whole either`() = runTest {
        server.close()

        assertThat(assets.prefetch(listOf(url("/a")))).isFalse()
    }

    @Test
    fun `what the config no longer names is deleted and what it names stays`() = runTest {
        server.enqueue(image())
        server.enqueue(image())
        assets.prefetch(listOf(url("/old"), url("/new")))

        assets.retainOnly(listOf(url("/new")))

        assertThat(assets.fileOf(url("/old"))).isNull()
        assertThat(assets.fileOf(url("/new"))).isNotNull()
    }

    @Test
    fun `an unknown picture has no file`() {
        assertThat(assets.fileOf(url("/nothing"))).isNull()
    }
}
