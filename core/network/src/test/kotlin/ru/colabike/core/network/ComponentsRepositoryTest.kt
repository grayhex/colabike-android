package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.ComponentQuery
import ru.colabike.core.model.ComponentSort
import ru.colabike.core.model.DataError

class ComponentsRepositoryTest {
    private val site = TestServer()
    private val components =
        NetworkComponentsRepository(site.api.components, site.media, Dispatchers.Unconfined)

    @After fun close() = site.close()

    private val id = "c0000000-0000-4000-8000-000000000001"

    @Test
    fun `the catalog asks for the filters, the sort and the cursor it was given`() = runTest {
        site.json(200, site.fixture("component-page.json"))

        val page =
            components.page(
                ComponentQuery(
                    text = "  кассета ",
                    category = "Трансмиссия",
                    brand = "Shimano",
                    sort = ComponentSort.Popular,
                ),
                cursor = "c0",
                limit = 12,
            )

        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/component-models")
        assertThat(request.queryParameter("q")).isEqualTo("кассета")
        assertThat(request.queryParameter("category")).isEqualTo("Трансмиссия")
        assertThat(request.queryParameter("brand")).isEqualTo("Shimano")
        assertThat(request.queryParameter("sort")).isEqualTo("popular")
        assertThat(request.queryParameter("cursor")).isEqualTo("c0")
        assertThat(request.queryParameter("limit")).isEqualTo("12")
        assertThat(page.nextCursor).isEqualTo("eyJ0IjoiMiJ9")
        val (cassette, brake) = page.items
        assertThat(cassette.id).isEqualTo(ComponentId(id))
        assertThat(cassette.builds).isEqualTo(42)
        assertThat(cassette.firstPublicAt).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"))
        assertThat(cassette.coverUrl).endsWith("/media/components/deore.jpg")
        assertThat(cassette.archived).isFalse()
        // No cover is none, not a blank address.
        assertThat(brake.coverUrl).isNull()
    }

    @Test
    fun `an empty query sends no filters at all and the newest first`() = runTest {
        site.json(200, site.fixture("component-page.json"))

        components.page(ComponentQuery())

        val request = site.server.takeRequest().url
        assertThat(request.queryParameter("q")).isNull()
        assertThat(request.queryParameter("category")).isNull()
        assertThat(request.queryParameter("brand")).isNull()
        assertThat(request.queryParameter("sort")).isEqualTo("new")
        assertThat(request.queryParameter("cursor")).isNull()
    }

    @Test
    fun `filters keep what the server lists, once, and drop blanks`() = runTest {
        site.json(200, site.fixture("component-filters.json"))

        val filters = components.filters()

        assertThat(filters.categories).containsExactly("Трансмиссия", "Тормоза").inOrder()
        assertThat(filters.brands).containsExactly("Shimano", "SRAM").inOrder()
    }

    @Test
    fun `a merged model answers with the canonical id, which is the one to use afterwards`() =
        runTest {
            site.json(200, site.fixture("component-model-merged.json"))

            val model = components.model(ComponentId("c0000000-0000-4000-8000-0000000000aa"))

            val request = site.server.takeRequest().url
            assertThat(request.encodedPath)
                .isEqualTo("/api/v1/component-models/c0000000-0000-4000-8000-0000000000aa")
            assertThat(model.id).isEqualTo(ComponentId(id))
        }

    @Test
    fun `an archived model is read and says so`() = runTest {
        site.json(200, site.fixture("component-model-archived.json"))

        val model = components.model(ComponentId("c0000000-0000-4000-8000-000000000009"))

        assertThat(model.archived).isTrue()
        assertThat(model.coverUrl).isNull()
    }

    @Test
    fun `an unpublished model is not found, and an id that is no UUID never leaves the app`() =
        runTest {
            site.json(404, """{"error":{"code":"not_found","message":"Нет такой модели"}}""")
            val missing = runCatching { components.model(ComponentId(id)) }.exceptionOrNull()
            assertThat(missing).isInstanceOf(DataError.NotFound::class.java)

            val garbage = runCatching {
                components.model(ComponentId("../../me"))
            }
                .exceptionOrNull()
            assertThat(garbage).isInstanceOf(DataError.NotFound::class.java)
            val photos = runCatching { components.photos(ComponentId("nope")) }.exceptionOrNull()
            assertThat(photos).isInstanceOf(DataError.NotFound::class.java)
            // One request: the 404 above. Nothing was sent for the two bad ids.
            assertThat(site.server.requestCount).isEqualTo(1)
        }

    @Test
    fun `photos keep the author, and the source with its terms only through https links`() =
        runTest {
            site.json(200, site.fixture("component-photos.json"))

            val photos = components.photos(ComponentId(id))

            assertThat(site.server.takeRequest().url.encodedPath)
                .isEqualTo("/api/v1/component-models/$id/photos")
            val (outside, uploaded, hostile) = photos
            assertThat(outside.isCover).isTrue()
            assertThat(outside.caption).isEqualTo("Кассета в сборе")
            assertThat(outside.source?.license).isEqualTo("CC BY-SA 4.0")
            assertThat(outside.source?.licenseUrl)
                .isEqualTo("https://creativecommons.org/licenses/by-sa/4.0/")
            assertThat(outside.source?.url)
                .isEqualTo("https://commons.wikimedia.org/wiki/File:Deore.jpg")
            assertThat(uploaded.source).isNull()
            assertThat(uploaded.author?.name).isEqualTo("Вторая Райдерша")
            assertThat(uploaded.author?.avatarUrl).endsWith("/media/avatars/second.jpg")
            // Neither a script nor a plain http address is offered as a link.
            assertThat(hostile.source?.url).isNull()
            assertThat(hostile.source?.licenseUrl).isNull()
            assertThat(hostile.source?.license).isEqualTo("CC0")
        }
}
