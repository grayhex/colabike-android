package ru.colabike.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import ru.colabike.app.bikes.PhotoViewer
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Photo

/**
 * The full-screen photo against a server that behaves like the real one: it serves the photo at the
 * sizes it knows (160, 320, 640 and 1280: cola `lib/media-sizes.ts`) and answers any other size
 * with 400 "wrong photo size". The viewer used to ask for 1600 and showed "Photo unavailable" for
 * every photo (issue #56).
 */
@OptIn(ExperimentalCoilApi::class, DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class PhotoViewerTest {
    @get:Rule val compose = createComposeRule()

    private enum class Server {
        /** As production: the known sizes are served, the others are refused. */
        Real,
        /** The photo is gone. */
        Gone,
        /** The server is not reached. */
        Down,
    }

    private var server = Server.Real
    private val requests = CopyOnWriteArrayList<String>()

    // A real one-pixel picture: the viewer decodes it, so a served photo is a photo.
    private val pixel =
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
            )

    @Before
    fun serve() {
        val client =
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val request = chain.request()
                        requests += request.url.toString()
                        when (server) {
                            Server.Down -> throw IOException("no network")
                            Server.Gone -> answer(request, 404, """{"error":"Фото не найдено"}""")
                            Server.Real ->
                                if (request.url.queryParameter("width") in KnownSizes) {
                                    Response.Builder()
                                        .request(request)
                                        .protocol(Protocol.HTTP_1_1)
                                        .code(200)
                                        .message("OK")
                                        .body(pixel.toResponseBody("image/png".toMediaType()))
                                        .build()
                                } else {
                                    answer(
                                        request,
                                        400,
                                        """{"error":"Неверный размер фотографии"}""",
                                    )
                                }
                        }
                    }
                )
                .build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(ApplicationProvider.getApplicationContext<Context>())
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                // A cache would answer for the server, and the next run would find the last one's
                // photo.
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        )
    }

    @After fun reset() = SingletonImageLoader.reset()

    private fun answer(request: okhttp3.Request, code: Int, body: String) =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("answer")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun open() {
        compose.setContent {
            ColaBikeTheme {
                PhotoViewer(
                    listOf(Photo("p1", "https://colabike.test/api/photos/p1")),
                    startAt = 0,
                    onDismiss = {},
                )
            }
        }
        until { requests.isNotEmpty() }
    }

    /** Coil answers off the main thread and comes back to it: the looper is idled while waiting. */
    private fun until(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            ShadowLooper.idleMainLooper()
            compose.waitForIdle()
            Thread.sleep(25)
        }
        error("the condition did not come true in 5 s")
    }

    private fun shown(text: String) =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Lets the answer arrive and be drawn: Coil works off the main thread. */
    private fun settle() =
        repeat(10) {
            Thread.sleep(100)
            compose.waitForIdle()
        }

    @Test
    fun `the photo is asked at a size the server serves, and no failure is shown`() {
        open()
        settle()

        assertThat(requests).isNotEmpty()
        assertThat(requests.map { it.substringAfter("width=", "") }.toSet())
            .isEqualTo(setOf("1280"))
        compose.onNodeWithText("Фото недоступно").assertDoesNotExist()
        compose.onNodeWithText("Не удалось загрузить фото").assertDoesNotExist()
    }

    @Test
    fun `a photo that is gone says so and offers nothing to repeat`() {
        server = Server.Gone
        open()

        until { shown("Фото недоступно") }
        compose.onNodeWithText("Фото недоступно").assertIsDisplayed()
        compose.onNodeWithText("Повторить").assertDoesNotExist()
    }

    @Test
    fun `a transfer that failed is told apart from a photo that is gone, and can be repeated`() {
        server = Server.Down
        open()
        until { shown("Не удалось загрузить фото") }
        compose.onNodeWithText("Фото недоступно").assertDoesNotExist()
        val first = requests.size

        server = Server.Real
        compose.onNodeWithText("Повторить").performClick()

        until { requests.size > first }
        settle()
        compose.onNodeWithText("Не удалось загрузить фото").assertDoesNotExist()
    }

    private companion object {
        val KnownSizes = setOf("160", "320", "640", "1280")
    }
}
