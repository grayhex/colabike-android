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
import ru.colabike.core.model.AppConfigRefresh
import ru.colabike.core.model.ConfigAssets
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Feature
import ru.colabike.core.model.LaunchFill
import ru.colabike.core.model.NoticeKind
import ru.colabike.core.model.UpdateMode

class AppConfigRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val site = TestServer()
    private val fetched = mutableListOf<List<String>>()
    private val retained = mutableListOf<String>()
    private var assetsFine = true
    private var now = Instant.parse("2026-10-04T09:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): Instant = now
        }

    private val cacheFile by lazy { File(folder.root, "config/app-config.json") }

    private fun repository(
        cache: AppConfigCache = AppConfigCache(cacheFile)
    ): NetworkAppConfigRepository =
        NetworkAppConfigRepository(
            api = site.api.app,
            media = site.media,
            cache = cache,
            assets =
                object : ConfigAssets {
                    override suspend fun prefetch(urls: List<String>): Boolean {
                        fetched += urls
                        return assetsFine
                    }

                    override suspend fun retainOnly(urls: List<String>) {
                        retained += urls
                    }

                    override fun fileOf(url: String): File? = null
                },
            clock = clock,
            dispatcher = Dispatchers.Unconfined,
        )

    @After fun close() = site.close()

    private fun etagged(fixture: String, etag: String) =
        site.json(200, site.fixture(fixture), "ETag", etag)

    // --- reading -----------------------------------------------------------------------------

    @Test
    fun `a config is read whole, with the launch screen, onboarding, notice, links, flags and policy`() =
        runTest {
            etagged("app-config.json", "\"v7\"")

            val result = repository().refresh() as AppConfigRefresh.Updated
            val config = result.stored.config

            val request = site.server.takeRequest()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.url.encodedPath).isEqualTo("/api/v1/app-config")
            // The first request has no validator.
            assertThat(request.headers["If-None-Match"]).isNull()
            assertThat(config.revision).isEqualTo(7)
            assertThat(result.stored.validatedAt).isEqualTo(now)
            with(config.launch) {
                assertThat(enabled).isTrue()
                assertThat(fill).isEqualTo(LaunchFill.Fit)
                assertThat(title).isEqualTo("Сезон открыт")
                // A picture of the site's own, in the size a screen needs.
                assertThat(imageUrl)
                    .isEqualTo(
                        site.media.resolve(
                            "/api/assets/a0000000-0000-4000-8000-000000000001?width=1920"
                        )
                    )
            }
            with(config.onboarding) {
                assertThat(enabled).isTrue()
                assertThat(revision).isEqualTo(3)
                assertThat(items.map { it.title }).containsExactly("Гараж", "Покатушки").inOrder()
                assertThat(items[0].imageUrl)
                    .contains("/api/assets/a0000000-0000-4000-8000-000000000002")
                assertThat(items[1].body).isNull()
            }
            with(config.notice!!) {
                assertThat(revision).isEqualTo(5)
                assertThat(kind).isEqualTo(NoticeKind.Service)
                assertThat(action?.url).isEqualTo("https://colabike.ru/about#status")
            }
            assertThat(config.links.support).isEqualTo("https://help.example.ru/colabike")
            assertThat(config.features.isEnabled(Feature.Market)).isFalse()
            assertThat(config.features.isEnabled(Feature.Chat)).isTrue()
            assertThat(config.features.isEnabled(Feature.NativeYandexSignIn)).isFalse()
            with(config.compatibility) {
                assertThat(minimumSupportedVersionCode).isEqualTo(3)
                assertThat(latestVersionCode).isEqualTo(10)
                assertThat(mode).isEqualTo(UpdateMode.Hard)
                assertThat(updateUrl)
                    .isEqualTo("https://www.rustore.ru/catalog/app/ru.colabike.app")
            }
        }

    @Test
    fun `a quiet config changes nothing`() = runTest {
        etagged("app-config-quiet.json", "\"q1\"")

        val config = (repository().refresh() as AppConfigRefresh.Updated).stored.config

        assertThat(config.launch.enabled).isFalse()
        assertThat(config.onboarding.enabled).isFalse()
        assertThat(config.notice).isNull()
        assertThat(config.links.support).isNull()
        assertThat(config.compatibility.minimumSupportedVersionCode).isNull()
        assertThat(Feature.entries.all { config.features.isEnabled(it) }).isTrue()
        // Nothing to keep on the device.
        assertThat(fetched.single()).isEmpty()
    }

    @Test
    fun `what the app cannot use is left out, not guessed at`() = runTest {
        etagged("app-config-hostile.json", "\"h\"")

        val config = (repository().refresh() as AppConfigRefresh.Updated).stored.config

        // A launch screen needs a picture of the site's own: this one is on another host.
        assertThat(config.launch.enabled).isFalse()
        assertThat(config.launch.imageUrl).isNull()
        assertThat(config.launch.fill).isEqualTo(LaunchFill.Crop)
        assertThat(config.launch.title).isNull()
        assertThat(config.revision).isEqualTo(0)
        // A page with no title is dropped, a picture off the site is dropped, six at most.
        val items = config.onboarding.items
        assertThat(items.map { it.title }).containsExactly("1", "3", "4", "5", "6").inOrder()
        assertThat(items.first().imageUrl).isNull()
        assertThat(items[1].imageUrl).contains("/api/assets/a0000000-0000-4000-8000-000000000009")
        // An unknown kind is a promo; a button to an intent is no button; a path out of the site is
        // no picture.
        with(config.notice!!) {
            assertThat(kind).isEqualTo(NoticeKind.Promo)
            assertThat(action).isNull()
            assertThat(imageUrl).isNull()
            assertThat(body).isNull()
        }
        // Only plain https addresses without credentials are links.
        assertThat(config.links.help).isNull()
        assertThat(config.links.privacy).isNull()
        assertThat(config.links.terms).isNull()
        assertThat(config.links.about).isNull()
        assertThat(config.links.support).isEqualTo("https://help.example.ru/x")
        // A key the app has no function for is dropped; one it has is kept.
        assertThat(config.features.flags.keys).containsExactly("chat", "market")
        assertThat(config.features.isEnabled(Feature.Chat)).isFalse()
        // A block without a way to update is not a block.
        assertThat(config.compatibility.mode).isEqualTo(UpdateMode.Soft)
        assertThat(config.compatibility.updateUrl).isNull()
        assertThat(config.compatibility.message).isEqualTo("Обновитесь")
    }

    // --- the validator and the cache ----------------------------------------------------------

    @Test
    fun `the kept validator is sent, and a 304 confirms the kept config as of now`() = runTest {
        val repository = repository()
        etagged("app-config.json", "\"v7\"")
        repository.refresh()
        site.server.takeRequest()
        now = now.plusSeconds(3600)
        site.json(304, "", "ETag", "\"v7\"")

        val result = repository.refresh() as AppConfigRefresh.NotModified

        assertThat(site.server.takeRequest().headers["If-None-Match"]).isEqualTo("\"v7\"")
        assertThat(result.validatedAt).isEqualTo(now)
        val cached = repository.cached()!!
        assertThat(cached.config.revision).isEqualTo(7)
        assertThat(cached.validatedAt).isEqualTo(now)
        // No pictures were asked for again.
        assertThat(fetched).hasSize(1)
    }

    @Test
    fun `a new revision replaces the kept one as a whole, with a new validator`() = runTest {
        val repository = repository()
        etagged("app-config.json", "\"v7\"")
        repository.refresh()
        etagged("app-config-next.json", "\"v8\"")

        val result = repository.refresh() as AppConfigRefresh.Updated

        assertThat(result.stored.config.revision).isEqualTo(8)
        assertThat(result.stored.config.notice).isNull()
        assertThat(repository.cached()!!.config.revision).isEqualTo(8)
        site.json(304, "", "ETag", "\"v8\"")
        repository.refresh()
        site.server.takeRequest()
        site.server.takeRequest()
        assertThat(site.server.takeRequest().headers["If-None-Match"]).isEqualTo("\"v8\"")
    }

    @Test
    fun `a config is kept for the next start and read from the file by a new repository`() =
        runTest {
            etagged("app-config.json", "\"v7\"")
            repository().refresh()

            val cached = repository().cached()!!

            assertThat(cached.config.revision).isEqualTo(7)
            assertThat(cached.config.links.support).isEqualTo("https://help.example.ru/colabike")
            assertThat(cached.validatedAt).isEqualTo(now)
        }

    @Test
    fun `the pictures of what is shown are kept before the config is, and only those`() = runTest {
        etagged("app-config.json", "\"v7\"")

        repository().refresh()

        val urls = fetched.single()
        assertThat(urls).hasSize(2)
        assertThat(urls[0]).contains("/api/assets/a0000000-0000-4000-8000-000000000001?width=1920")
        assertThat(urls[1]).contains("/api/assets/a0000000-0000-4000-8000-000000000002?width=1280")
        // And once the config is the kept one, only these stay on the device.
        assertThat(retained).containsExactlyElementsIn(urls)
    }

    @Test
    fun `a revision whose pictures cannot be kept does not replace the whole one`() = runTest {
        val repository = repository()
        etagged("app-config.json", "\"v7\"")
        repository.refresh()
        assetsFine = false
        etagged("app-config-next.json", "\"v8\"")

        val result = repository.refresh()

        assertThat(result).isInstanceOf(AppConfigRefresh.Failed::class.java)
        // The old config is still the kept one, with its old validator: the next start asks again.
        assertThat(repository.cached()!!.config.revision).isEqualTo(7)
        site.json(304, "", "ETag", "\"v7\"")
        assetsFine = true
        repository.refresh()
        site.server.takeRequest()
        site.server.takeRequest()
        assertThat(site.server.takeRequest().headers["If-None-Match"]).isEqualTo("\"v7\"")
    }

    // --- when it goes wrong --------------------------------------------------------------------

    @Test
    fun `no network keeps the old config and says so`() = runTest {
        val repository = repository()
        etagged("app-config.json", "\"v7\"")
        repository.refresh()
        site.server.close()

        val result = repository.refresh() as AppConfigRefresh.Failed

        assertThat(result.error).isInstanceOf(DataError.Offline::class.java)
        assertThat(repository.cached()!!.config.revision).isEqualTo(7)
    }

    @Test
    fun `an old server without the endpoint, a fault and a limit are failures, not crashes`() =
        runTest {
            val repository = repository()
            site.json(404, error("not_found"))
            site.json(500, error("internal"))
            site.json(429, error("rate_limited"), "Retry-After", "30")

            val missing = repository.refresh() as AppConfigRefresh.Failed
            val fault = repository.refresh() as AppConfigRefresh.Failed
            val limited = repository.refresh() as AppConfigRefresh.Failed

            assertThat(missing.error).isInstanceOf(DataError.NotFound::class.java)
            assertThat(fault.error).isInstanceOf(DataError.Server::class.java)
            assertThat((limited.error as DataError.RateLimited).retryAfterSeconds).isEqualTo(30)
            assertThat(repository.cached()).isNull()
        }

    @Test
    fun `an answer that is not a config is a failure and leaves the kept one`() = runTest {
        val repository = repository()
        etagged("app-config.json", "\"v7\"")
        repository.refresh()
        site.json(200, """{"revision":"seven"}""")
        site.json(200, "<html>captive portal</html>")

        val first = repository.refresh()
        val second = repository.refresh()

        assertThat(first).isInstanceOf(AppConfigRefresh.Failed::class.java)
        assertThat(second).isInstanceOf(AppConfigRefresh.Failed::class.java)
        assertThat(repository.cached()!!.config.revision).isEqualTo(7)
    }

    @Test
    fun `a cache that is damaged, empty or of another format is no cache and is deleted`() =
        runTest {
            val repository = repository()
            cacheFile.parentFile!!.mkdirs()
            for (content in
                listOf(
                    "{not json",
                    "",
                    """{"version":99,"validatedAtMillis":1,"body":"{}"}""",
                    """{"version":1,"validatedAtMillis":1,"body":"{\"revision\":1}"}""",
                )) {
                cacheFile.writeText(content)

                assertThat(repository.cached()).isNull()
                assertThat(cacheFile.exists()).isFalse()
            }
        }

    @Test
    fun `a cache that cannot be written is not an error`() = runTest {
        // The "directory" is a file: nothing can be created in it.
        val blocker = folder.newFile("blocker")
        val repository = repository(AppConfigCache(File(blocker, "app-config.json")))
        etagged("app-config.json", "\"v7\"")

        val result = repository.refresh()

        assertThat(result).isInstanceOf(AppConfigRefresh.Updated::class.java)
        assertThat(repository.cached()).isNull()
    }

    @Test
    fun `the cache holds nothing of a person and its text form says nothing of the config`() =
        runTest {
            etagged("app-config.json", "\"v7\"")
            repository().refresh()

            val raw = cacheFile.readText()
            val entry = AppConfigCache(cacheFile).read()!!

            assertThat(raw).doesNotContain("Authorization")
            assertThat(raw).doesNotContain("cola_at_")
            assertThat(entry.toString()).doesNotContain("Гараж")
        }

    @Test
    fun `a request for the config never carries a session`() = runTest {
        etagged("app-config.json", "\"v7\"")

        repository().refresh()

        val request = site.server.takeRequest()
        assertThat(request.headers["Authorization"]).isNull()
        assertThat(request.headers["Cookie"]).isNull()
    }
}
