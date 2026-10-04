package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.colabike.app.config.AppConfigController
import ru.colabike.core.model.AppConfig
import ru.colabike.core.model.AppConfigRefresh
import ru.colabike.core.model.AppConfigRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Feature
import ru.colabike.core.model.StoredAppConfig

/** The config at the start of the app and afterwards: kept first, asked for in the background. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppConfigControllerTest {
    private var now = Instant.parse("2026-10-04T12:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): Instant = now
        }

    private fun config(revision: Int, vararg off: Feature) =
        AppConfig.Builtin.copy(revision = revision, features = featuresOff(*off))

    private class Repository(
        var kept: StoredAppConfig? = null,
        var onCached: suspend () -> StoredAppConfig? = { kept },
    ) : AppConfigRepository {
        val answers = ArrayDeque<suspend () -> AppConfigRefresh>()
        var refreshes = 0

        override suspend fun cached(): StoredAppConfig? = onCached()

        override suspend fun refresh(): AppConfigRefresh {
            refreshes++
            return (answers.removeFirstOrNull()
                ?: {
                    AppConfigRefresh.Failed(DataError.NotFound())
                })()
        }
    }

    /** Lets everything that is due run, the controller's own coroutines included. */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.controller(repository: Repository) =
        AppConfigController(repository, backgroundScope, clock, staleAfter = Duration.ofMinutes(10))

    @Test
    fun `it starts on the built-in config, with nothing known`() = runTest {
        val controller = controller(Repository())

        assertThat(controller.state.value.loaded).isFalse()
        assertThat(controller.state.value.config).isEqualTo(AppConfig.Builtin)
        assertThat(controller.state.value.validatedAt).isNull()
    }

    @Test
    fun `the kept config is in force before the server has been asked`() = runTest {
        val kept = StoredAppConfig(config(3, Feature.Market), now.minusSeconds(3600))
        val gate = CompletableDeferred<AppConfigRefresh>()
        val repository = Repository(kept)
        repository.answers += { gate.await() }
        val controller = controller(repository)

        controller.start()
        runCurrent()

        // The app can start on this while the request is on its way.
        assertThat(controller.state.value.loaded).isTrue()
        assertThat(controller.state.value.config.revision).isEqualTo(3)
        assertThat(controller.state.value.features.isEnabled(Feature.Market)).isFalse()
        assertThat(controller.state.value.validatedAt).isEqualTo(kept.validatedAt)
        assertThat(repository.refreshes).isEqualTo(1)
    }

    @Test
    fun `a new config replaces the kept one as a whole`() = runTest {
        val repository = Repository(StoredAppConfig(config(3), now.minusSeconds(3600)))
        val fresh = StoredAppConfig(config(4, Feature.Rides), now)
        repository.answers += { AppConfigRefresh.Updated(fresh) }
        val controller = controller(repository)

        controller.start()
        settle()

        assertThat(controller.state.value.config).isEqualTo(fresh.config)
        assertThat(controller.state.value.validatedAt).isEqualTo(now)
    }

    @Test
    fun `with nothing kept the first answer is the config`() = runTest {
        val repository = Repository()
        repository.answers += { AppConfigRefresh.Updated(StoredAppConfig(config(1), now)) }
        val controller = controller(repository)

        controller.start()
        settle()

        assertThat(controller.state.value.config.revision).isEqualTo(1)
    }

    @Test
    fun `an unchanged config is confirmed, not replaced`() = runTest {
        val kept = StoredAppConfig(config(3, Feature.Chat), now.minusSeconds(3600))
        val repository = Repository(kept)
        repository.answers += { AppConfigRefresh.NotModified(now) }
        val controller = controller(repository)

        controller.start()
        settle()

        assertThat(controller.state.value.config).isEqualTo(kept.config)
        assertThat(controller.state.value.validatedAt).isEqualTo(now)
    }

    @Test
    fun `a failure of any kind changes nothing and is no reason to stop`() = runTest {
        val kept = StoredAppConfig(config(3, Feature.Chat), now.minusSeconds(3600))
        val repository = Repository(kept)
        repository.answers += { AppConfigRefresh.Failed(DataError.Offline(IOException("x"))) }
        val controller = controller(repository)

        controller.start()
        settle()

        assertThat(controller.state.value.loaded).isTrue()
        assertThat(controller.state.value.config).isEqualTo(kept.config)
        assertThat(controller.state.value.validatedAt).isEqualTo(kept.validatedAt)
    }

    @Test
    fun `a repository that fails to read what is kept still lets the app start`() = runTest {
        val repository = Repository(onCached = { throw IllegalStateException("disk") })
        val controller = controller(repository)

        controller.start()
        settle()

        assertThat(controller.state.value.loaded).isTrue()
        assertThat(controller.state.value.config).isEqualTo(AppConfig.Builtin)
    }

    @Test
    fun `a read that never comes back does not hold the app longer than two seconds`() = runTest {
        val repository =
            Repository(
                onCached = {
                    delay(60_000)
                    null
                }
            )
        val controller = controller(repository)

        controller.start()
        advanceTimeBy(1_900)
        runCurrent()
        assertThat(controller.state.value.loaded).isFalse()
        advanceTimeBy(200)
        runCurrent()

        assertThat(controller.state.value.loaded).isTrue()
        assertThat(controller.state.value.config).isEqualTo(AppConfig.Builtin)
    }

    @Test
    fun `coming back soon asks nothing, coming back later asks again, and never in parallel`() =
        runTest {
            val gate = CompletableDeferred<AppConfigRefresh>()
            val repository = Repository()
            repository.answers += { AppConfigRefresh.NotModified(now) }
            repository.answers += { gate.await() }
            val controller = controller(repository)
            controller.start()
            settle()
            assertThat(repository.refreshes).isEqualTo(1)

            // A few minutes later: still fresh enough.
            now = now.plusSeconds(300)
            controller.refreshIfStale()
            settle()
            assertThat(repository.refreshes).isEqualTo(1)

            now = now.plusSeconds(400)
            controller.refreshIfStale()
            runCurrent()
            assertThat(repository.refreshes).isEqualTo(2)
            // The request is on its way: another resume is the same request.
            now = now.plusSeconds(1200)
            controller.refreshIfStale()
            runCurrent()
            assertThat(repository.refreshes).isEqualTo(2)
            gate.complete(AppConfigRefresh.NotModified(now))
            settle()
        }

    @Test
    fun `before the kept config is read nothing is asked`() = runTest {
        val repository = Repository()
        val controller = controller(repository)

        controller.refreshIfStale()
        settle()

        assertThat(repository.refreshes).isEqualTo(0)
    }
}
