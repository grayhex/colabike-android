package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.colabike.app.catalog.BuiltinCatalog
import ru.colabike.app.catalog.CatalogController
import ru.colabike.core.model.CatalogRefresh
import ru.colabike.core.model.DataError
import ru.colabike.core.model.SiteCatalog
import ru.colabike.core.model.SiteCatalogRepository
import ru.colabike.core.model.StoredCatalog

/** The site's dictionaries in the app: kept first, asked for behind it, never in the way. */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogControllerTest {
    private var now = Instant.parse("2026-10-07T12:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): Instant = now
        }

    private fun catalog(version: Int): SiteCatalog =
        SiteCatalogFixtures.catalog.copy(version = version)

    private class Repository(var kept: StoredCatalog? = null) : SiteCatalogRepository {
        val answers = ArrayDeque<suspend () -> CatalogRefresh>()
        var refreshes = 0
        var reads = 0

        override suspend fun cached(): StoredCatalog? {
            reads++
            return kept
        }

        override suspend fun refresh(): CatalogRefresh {
            refreshes++
            return (answers.removeFirstOrNull()
                ?: {
                    CatalogRefresh.Failed(DataError.NotFound())
                })()
        }
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.controller(repository: Repository) =
        CatalogController(repository, backgroundScope, clock, staleAfter = Duration.ofMinutes(10))

    @Test
    fun `nothing is asked until a screen needs the dictionaries, and then there is a stand-in`() =
        runTest {
            val repository = Repository()

            val controller = controller(repository)
            settle()

            assertThat(repository.reads).isEqualTo(0)
            assertThat(repository.refreshes).isEqualTo(0)
            assertThat(controller.state.value.catalog).isSameInstanceAs(BuiltinCatalog.value)
            assertThat(controller.state.value.fromSite).isFalse()
        }

    @Test
    fun `the stand-in has the keys a form cannot do without and nothing of the site's lists`() {
        val builtin = BuiltinCatalog.value

        assertThat(builtin.version).isEqualTo(0)
        assertThat(builtin.classification.categories.map { it.key })
            .containsExactly(
                "mtb",
                "road_gravel",
                "urban_touring",
                "bmx",
                "cargo_utility",
                "special",
            )
            .inOrder()
        assertThat(builtin.classification.subtypesOf("mtb").map { it.key })
            .containsExactly("xc", "trail", "enduro", "downhill", "dirt_jump")
            .inOrder()
        assertThat(builtin.classification.category("mtb")?.name).isEqualTo("MTB")
        assertThat(builtin.classification.maxUses).isEqualTo(3)
        assertThat(builtin.brands).isEmpty()
        assertThat(builtin.components.groups).isNotEmpty()
    }

    @Test
    fun `the kept copy is in force before the server answers`() = runTest {
        val kept = StoredCatalog(catalog(3), now.minusSeconds(3600))
        val gate = CompletableDeferred<CatalogRefresh>()
        val repository = Repository(kept).apply { answers += { gate.await() } }
        val controller = controller(repository)

        controller.load()
        settle()

        assertThat(controller.state.value.catalog.version).isEqualTo(3)
        assertThat(controller.state.value.fromSite).isTrue()
        assertThat(controller.state.value.refreshing).isTrue()
        gate.complete(CatalogRefresh.NotModified(now))
        settle()
        assertThat(controller.state.value.refreshing).isFalse()
        assertThat(controller.state.value.validatedAt).isEqualTo(now)
    }

    @Test
    fun `a new copy arrives as one value`() = runTest {
        val repository =
            Repository(StoredCatalog(catalog(3), now.minusSeconds(3600))).apply {
                answers += { CatalogRefresh.Updated(StoredCatalog(catalog(4), now)) }
            }
        val controller = controller(repository)

        controller.load()
        settle()

        assertThat(controller.state.value.catalog.version).isEqualTo(4)
        assertThat(controller.state.value.validatedAt).isEqualTo(now)
        assertThat(controller.state.value.refreshFailed).isFalse()
    }

    @Test
    fun `a failure changes nothing that is shown and says so`() = runTest {
        val repository =
            Repository(StoredCatalog(catalog(3), now.minusSeconds(3600))).apply {
                answers += { CatalogRefresh.Failed(DataError.Offline(java.io.IOException())) }
            }
        val controller = controller(repository)

        controller.load()
        settle()

        assertThat(controller.state.value.catalog.version).isEqualTo(3)
        assertThat(controller.state.value.refreshFailed).isTrue()
        assertThat(controller.state.value.refreshing).isFalse()
    }

    @Test
    fun `without a kept copy a failure leaves the stand-in, and trying again is possible`() =
        runTest {
            val repository =
                Repository().apply {
                    answers += { CatalogRefresh.Failed(DataError.Offline(java.io.IOException())) }
                    answers += { CatalogRefresh.Updated(StoredCatalog(catalog(1), now)) }
                }
            val controller = controller(repository)
            controller.load()
            settle()
            assertThat(controller.state.value.fromSite).isFalse()
            assertThat(controller.state.value.refreshFailed).isTrue()

            controller.refreshNow()
            settle()

            assertThat(controller.state.value.fromSite).isTrue()
            assertThat(controller.state.value.catalog.version).isEqualTo(1)
            assertThat(controller.state.value.refreshFailed).isFalse()
        }

    @Test
    fun `a screen opened again soon asks nothing, one opened much later does`() = runTest {
        val repository =
            Repository(StoredCatalog(catalog(3), now)).apply {
                answers += { CatalogRefresh.NotModified(now) }
                answers += { CatalogRefresh.NotModified(now) }
            }
        val controller = controller(repository)
        controller.load()
        settle()
        assertThat(repository.refreshes).isEqualTo(1)

        now = now.plusSeconds(60)
        controller.load()
        settle()
        assertThat(repository.refreshes).isEqualTo(1)
        // The copy on the device is read once, not on every screen.
        assertThat(repository.reads).isEqualTo(1)

        now = now.plusSeconds(3600)
        controller.load()
        settle()
        assertThat(repository.refreshes).isEqualTo(2)
    }

    @Test
    fun `two screens at once are one request`() = runTest {
        val gate = CompletableDeferred<CatalogRefresh>()
        val repository = Repository().apply { answers += { gate.await() } }
        val controller = controller(repository)

        controller.load()
        controller.load()
        settle()
        controller.refreshNow()
        settle()

        assertThat(repository.refreshes).isEqualTo(1)
        gate.complete(CatalogRefresh.Updated(StoredCatalog(catalog(2), now)))
        settle()
        assertThat(controller.state.value.catalog.version).isEqualTo(2)
    }
}
