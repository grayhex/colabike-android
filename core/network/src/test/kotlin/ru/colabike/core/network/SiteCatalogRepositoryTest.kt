package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.colabike.core.model.CatalogRefresh
import ru.colabike.core.model.DataError

class SiteCatalogRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val site = TestServer()
    private var now = Instant.parse("2026-10-07T09:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): Instant = now
        }
    private val cacheFile by lazy { File(folder.root, "catalog/site-catalog.json") }

    private fun repository(cache: DocumentCache = DocumentCache(cacheFile)) =
        NetworkSiteCatalogRepository(site.api.bikes, cache, clock, Dispatchers.Unconfined)

    @After fun close() = site.close()

    private fun answer(etag: String) =
        site.json(200, site.fixture("site-catalog.json"), "ETag", etag)

    @Test
    fun `the dictionaries are read whole, in the order of the site`() = runTest {
        answer("\"v1\"")

        val result = repository().refresh() as CatalogRefresh.Updated
        val catalog = result.stored.catalog

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/catalog")
        assertThat(request.headers["If-None-Match"]).isNull()
        assertThat(result.stored.validatedAt).isEqualTo(now)
        assertThat(catalog.version).isEqualTo(7)
        assertThat(catalog.classification.categories.map { it.key })
            .containsExactly(
                "mtb",
                "road_gravel",
                "urban_touring",
                "bmx",
                "cargo_utility",
                "special",
            )
            .inOrder()
        // A category has its own subtypes and no others.
        assertThat(catalog.classification.subtypesOf("mtb").map { it.key })
            .containsExactly("xc", "trail", "enduro", "downhill", "dirt_jump")
            .inOrder()
        assertThat(catalog.classification.subtypesOf("nothing")).isEmpty()
        assertThat(catalog.classification.maxUses).isEqualTo(3)
        assertThat(catalog.sizes).containsExactly("XS", "S", "M", "L", "XL").inOrder()
        // One brand is one entry, its models once, over every kind of the site's lists.
        val canyon = catalog.brands.filter { it.name == "Canyon" }
        assertThat(canyon).hasSize(1)
        assertThat(canyon.single().models).containsAtLeast("Grizl", "Neuron")
        assertThat(catalog.components.groups.map { it.id }).contains("drivetrain")
        assertThat(catalog.components.namesOf("групсет")).contains("Shimano 105")
    }

    @Test
    fun `the dictionaries the site served on 7 October 2026 are read as they are`() = runTest {
        // A copy of the live answer of GET /catalog: the client's contract is the site's, not ours.
        site.json(200, site.fixture("site-catalog-production.json"), "ETag", "\"live\"")

        val catalog = (repository().refresh() as CatalogRefresh.Updated).stored.catalog

        assertThat(catalog.version).isAtLeast(1)
        assertThat(catalog.classification.categories).isNotEmpty()
        assertThat(catalog.brands).isNotEmpty()
        assertThat(catalog.brands.all { it.name.isNotBlank() }).isTrue()
        assertThat(catalog.components.groups).isNotEmpty()
        assertThat(catalog.components.groups.all { it.categories.isNotEmpty() }).isTrue()
        assertThat(catalog.sizes).isNotEmpty()
    }

    @Test
    fun `a kept copy is asked with its validator and confirmed when nothing changed`() = runTest {
        answer("\"v1\"")
        repository().refresh()
        site.server.takeRequest()
        site.json(304, "", "ETag", "\"v1\"")
        now = Instant.parse("2026-10-07T10:00:00Z")

        val result = repository().refresh() as CatalogRefresh.NotModified

        assertThat(site.server.takeRequest().headers["If-None-Match"]).isEqualTo("\"v1\"")
        assertThat(result.validatedAt).isEqualTo(now)
        // The kept copy is still readable, and confirmed as of now.
        assertThat(repository().cached()!!.validatedAt).isEqualTo(now)
    }

    @Test
    fun `a copy that was read is there for the next start, before the network is asked`() =
        runTest {
            answer("\"v1\"")
            repository().refresh()

            val kept = repository().cached()!!

            assertThat(kept.catalog.version).isEqualTo(7)
            assertThat(kept.catalog.brandSuggestions("can")).contains("Canyon")
        }

    @Test
    fun `a failure leaves what was kept as it was`() = runTest {
        answer("\"v1\"")
        repository().refresh()
        site.server.takeRequest()
        site.json(500, """{"error":{"code":"internal_error","message":"Сбой"}}""")

        val result = repository().refresh()

        assertThat(result).isInstanceOf(CatalogRefresh.Failed::class.java)
        assertThat((result as CatalogRefresh.Failed).error)
            .isInstanceOf(DataError.Server::class.java)
        assertThat(repository().cached()!!.catalog.version).isEqualTo(7)
    }

    @Test
    fun `no network is a failure that can be asked again, not a missing copy`() = runTest {
        site.server.close()

        val result = repository().refresh()

        assertThat((result as CatalogRefresh.Failed).error)
            .isInstanceOf(DataError.Offline::class.java)
        assertThat(repository().cached()).isNull()
    }

    @Test
    fun `a damaged file is no copy and is thrown away`() = runTest {
        cacheFile.parentFile!!.mkdirs()
        cacheFile.writeText("{ not json")

        assertThat(repository().cached()).isNull()
        assertThat(cacheFile.exists()).isFalse()
    }

    @Test
    fun `a kept body this build cannot read is no copy`() = runTest {
        DocumentCache(cacheFile)
            .write(DocumentCache.Entry("""{"version":"seven"}""", "\"x\"", now.toEpochMilli()))

        assertThat(repository().cached()).isNull()
        assertThat(cacheFile.exists()).isFalse()
    }

    @Test
    fun `values without a key or a name are left out and repeats are taken once`() = runTest {
        site.json(
            200,
            """
            {"version":2,
             "classification":{"categories":[{"key":"mtb","name":"MTB","subtypes":[
                {"key":"xc","name":"XC"},{"key":"xc","name":"Again"},{"key":"","name":"None"}]},
                {"key":"bmx","name":" ","subtypes":[]}],
              "suspensions":[],"constructions":[],"uses":[],"maxUses":0},
             "purposes":[{"id":"city","name":"Город"},{"id":"","name":"X"}],
             "brands":[{"name":" Giant ","models":["Talon"," ","Talon"]},{"name":"","models":[]}],
             "manufacturers":["Shimano","Shimano",""],
             "sizes":["S"," M ",""],
             "components":{"groups":[],"buildCategories":[],"accessoryCategories":[],"names":[]}}
            """
                .trimIndent(),
            "ETag",
            "\"v2\"",
        )

        val catalog = (repository().refresh() as CatalogRefresh.Updated).stored.catalog

        assertThat(catalog.classification.categories.map { it.key }).containsExactly("mtb")
        assertThat(catalog.classification.subtypesOf("mtb").map { it.name }).containsExactly("XC")
        assertThat(catalog.classification.maxUses).isEqualTo(1)
        assertThat(catalog.purposes.map { it.id }).containsExactly("city")
        assertThat(catalog.brands.single().name).isEqualTo("Giant")
        assertThat(catalog.brands.single().models).containsExactly("Talon")
        assertThat(catalog.manufacturers).containsExactly("Shimano")
        assertThat(catalog.sizes).containsExactly("S", "M").inOrder()
    }
}
