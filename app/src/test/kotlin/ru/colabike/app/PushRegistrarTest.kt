package ru.colabike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.app.push.PushAvailability
import ru.colabike.app.push.PushProvider
import ru.colabike.app.push.PushRegistrar
import ru.colabike.app.push.PushSyncResult
import ru.colabike.app.push.StoredPushBinding
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PushDeviceBinding
import ru.colabike.core.model.PushDeviceRegistration
import ru.colabike.core.model.PushDeviceRepository

/**
 * Keeping the phone's registration at the server true to the person's choice (cola#342): when it is
 * made, when it is repeated, when it is taken back, and what a late or refused answer changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PushRegistrarTest {
    private class Provider(
        var availability: PushAvailability = PushAvailability.Available,
        var token: String? = "secret-token-1",
        override val projectId: String = "project-1",
    ) : PushProvider {
        var deleted = 0
        var started = 0

        /** What the provider says before it is started, if that differs. */
        var beforeStart: PushAvailability? = null

        override fun start() {
            started++
        }

        override suspend fun availability() =
            if (started == 0) beforeStart ?: availability else availability

        override suspend fun token() = token

        override suspend fun deleteToken() {
            deleted++
        }
    }

    private class Devices : PushDeviceRepository {
        var binding: PushDeviceBinding? = null
        val registrations = mutableListOf<PushDeviceRegistration>()
        var revokes = 0
        var currentCalls = 0
        val registerFailures = ArrayDeque<DataError>()
        var revokeFailure: DataError? = null
        var generation = 1

        override suspend fun current(): PushDeviceBinding? {
            currentCalls++
            return binding
        }

        override suspend fun register(registration: PushDeviceRegistration): PushDeviceBinding {
            registrations += registration
            registerFailures.removeFirstOrNull()?.let { throw it }
            val made =
                PushDeviceBinding(
                    provider = "rustore",
                    projectId = registration.projectId,
                    generation = generation,
                    registeredAt = Instant.parse("2026-10-05T09:00:00Z"),
                    updatedAt = Instant.parse("2026-10-05T09:00:00Z"),
                    lastSeenAt = Instant.parse("2026-10-05T09:00:00Z"),
                )
            binding = made
            return made
        }

        override suspend fun revoke() {
            revokes++
            revokeFailure?.let { throw it }
            binding = null
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val provider = Provider()
    private val devices = Devices()
    private val settings =
        FakeNotificationSettings(
            defaultNotificationSettings.copy(
                channels =
                    defaultNotificationSettings.channels.copy(
                        pushAvailable = true,
                        pushEnabled = true,
                    )
            )
        )
    private val phone = FakeDeviceNotifications(readyPhone)
    private var accountId: String? = "account-1"
    private var now = Instant.parse("2026-10-05T09:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): Instant = now
        }
    private lateinit var store: StoredPushBinding

    @Before
    fun setUp() {
        val prefs = context.getSharedPreferences("push-binding-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = StoredPushBinding(prefs)
    }

    private fun registrar(scope: TestScope) =
        PushRegistrar(
            provider = provider,
            devices = devices,
            settings = settings,
            phone = phone,
            store = store,
            account = { accountId },
            scope = scope,
            clock = clock,
        )

    @Test
    fun `a phone that shows notifications for an account that said yes is registered`() = runTest {
        val result = registrar(this).sync()

        assertThat(result).isEqualTo(PushSyncResult.Registered(1))
        assertThat(devices.registrations).hasSize(1)
        assertThat(devices.registrations.single().projectId).isEqualTo("project-1")
        assertThat(devices.registrations.single().expectedGeneration).isNull()
        assertThat(store.current()?.accountId).isEqualTo("account-1")
        assertThat(store.current()?.generation).isEqualTo(1)
    }

    @Test
    fun `the provider's library starts only after the account's yes`() = runTest {
        settings.current =
            settings.current.copy(channels = settings.current.channels.copy(pushEnabled = false))
        val registrar = registrar(this)

        registrar.sync()
        assertThat(provider.started).isEqualTo(0)

        settings.current =
            settings.current.copy(channels = settings.current.channels.copy(pushEnabled = true))
        registrar.sync(force = true)
        assertThat(provider.started).isEqualTo(1)
    }

    @Test
    fun `a phone that does not show notifications never starts the library`() = runTest {
        phone.state = deniedPhone

        registrar(this).sync()

        assertThat(provider.started).isEqualTo(0)
    }

    @Test
    fun `what the library says after it starts is what counts`() = runTest {
        provider.beforeStart = PushAvailability.Available
        provider.availability = PushAvailability.NoDistributor

        val result = registrar(this).sync()

        assertThat(result).isEqualTo(PushSyncResult.ProviderUnavailable)
        assertThat(provider.started).isEqualTo(1)
        assertThat(devices.registrations).isEmpty()
    }

    @Test
    fun `the address is neither kept on the phone nor printed`() = runTest {
        registrar(this).sync()

        val kept = store.record()
        assertThat(kept.toString()).doesNotContain("secret-token-1")
        assertThat(kept?.fingerprint).doesNotContain("secret-token-1")
        assertThat(devices.registrations.single().toString()).doesNotContain("secret-token-1")
    }

    @Test
    fun `a whole and fresh registration is left alone without any request`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        val before = devices.registrations.size

        val result = registrar.sync()

        assertThat(result).isEqualTo(PushSyncResult.Unchanged)
        assertThat(devices.registrations).hasSize(before)
    }

    @Test
    fun `a forced check with the same address and project does not repeat the registration`() =
        runTest {
            val registrar = registrar(this)
            registrar.sync()

            val result = registrar.sync(force = true)

            assertThat(result).isEqualTo(PushSyncResult.Unchanged)
            assertThat(devices.registrations).hasSize(1)
        }

    @Test
    fun `a registration older than a week is made again with the generation the phone holds`() =
        runTest {
            val registrar = registrar(this)
            registrar.sync()

            now = now.plusSeconds(8 * 24 * 3600L)
            devices.generation = 1
            val result = registrar.sync()

            assertThat(result).isEqualTo(PushSyncResult.Registered(1))
            assertThat(devices.registrations).hasSize(2)
            assertThat(devices.registrations.last().expectedGeneration).isEqualTo(1)
        }

    @Test
    fun `a new address from the provider is sent at once`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        devices.generation = 2

        val result = registrar.sync(newToken = "secret-token-2")

        assertThat(result).isEqualTo(PushSyncResult.Registered(2))
        assertThat(devices.registrations.last().token).isEqualTo("secret-token-2")
        assertThat(devices.registrations.last().expectedGeneration).isEqualTo(1)
        assertThat(store.current()?.generation).isEqualTo(2)
    }

    @Test
    fun `a changed address found by the provider is registered even when the record is fresh`() =
        runTest {
            val registrar = registrar(this)
            registrar.sync()
            provider.token = "secret-token-3"
            devices.generation = 2

            val result = registrar.sync(force = true)

            assertThat(result).isEqualTo(PushSyncResult.Registered(2))
        }

    @Test
    fun `without a yes from the account nothing is registered`() = runTest {
        settings.current =
            settings.current.copy(channels = settings.current.channels.copy(pushEnabled = false))

        val result = registrar(this).sync()

        assertThat(result).isEqualTo(PushSyncResult.NotWanted)
        assertThat(devices.registrations).isEmpty()
        assertThat(devices.revokes).isEqualTo(0)
    }

    @Test
    fun `a server that cannot carry push registers nothing`() = runTest {
        settings.current =
            settings.current.copy(channels = settings.current.channels.copy(pushAvailable = false))

        assertThat(registrar(this).sync()).isEqualTo(PushSyncResult.NotWanted)
        assertThat(devices.registrations).isEmpty()
    }

    @Test
    fun `a phone that does not show notifications registers nothing`() = runTest {
        phone.state = deniedPhone

        assertThat(registrar(this).sync()).isEqualTo(PushSyncResult.NotWanted)
        assertThat(devices.registrations).isEmpty()
    }

    @Test
    fun `a withdrawn consent takes the registration back`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        settings.current =
            settings.current.copy(channels = settings.current.channels.copy(pushEnabled = false))

        val result = registrar.sync(force = true)

        assertThat(result).isEqualTo(PushSyncResult.Revoked)
        assertThat(devices.revokes).isEqualTo(1)
        assertThat(store.current()).isNull()
    }

    @Test
    fun `a refused permission takes the registration back`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        phone.state = deniedPhone

        val result = registrar.sync()

        assertThat(result).isEqualTo(PushSyncResult.Revoked)
        assertThat(store.current()).isNull()
    }

    @Test
    fun `a provider that left takes the registration back even if the request fails`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        provider.availability = PushAvailability.NoDistributor
        devices.revokeFailure = DataError.Offline(RuntimeException())

        val result = registrar.sync()

        assertThat(result).isEqualTo(PushSyncResult.ProviderUnavailable)
        assertThat(store.current()).isNull()
    }

    @Test
    fun `a build without a project or a provider offers nothing`() = runTest {
        provider.availability = PushAvailability.NotConfigured

        assertThat(registrar(this).sync()).isEqualTo(PushSyncResult.ProviderUnavailable)
        assertThat(devices.registrations).isEmpty()
    }

    @Test
    fun `no address yet is told apart and waits for the provider`() = runTest {
        provider.token = null

        assertThat(registrar(this).sync()).isEqualTo(PushSyncResult.NoToken)
        assertThat(devices.registrations).isEmpty()
    }

    @Test
    fun `without an account nothing is done and what was kept is forgotten`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        accountId = null

        assertThat(registrar.sync()).isEqualTo(PushSyncResult.NotSignedIn)
        assertThat(store.current()).isNull()
    }

    @Test
    fun `a record of another account is not mine to refresh`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        accountId = "account-2"
        devices.generation = 1

        registrar.sync()

        // The second account registers from the start: no generation of the first is claimed.
        assertThat(devices.registrations.last().expectedGeneration).isNull()
        assertThat(store.current()?.accountId).isEqualTo("account-2")
    }

    @Test
    fun `a conflicting generation is read and the registration tried once more`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        devices.binding = devices.binding!!.copy(generation = 5)
        devices.generation = 6
        devices.registerFailures += DataError.Rejected(409, "stale_generation", "conflict")

        val result = registrar.sync(newToken = "secret-token-2")

        assertThat(result).isEqualTo(PushSyncResult.Registered(6))
        assertThat(devices.currentCalls).isEqualTo(1)
        assertThat(devices.registrations.last().expectedGeneration).isEqualTo(5)
    }

    @Test
    fun `a second conflict is reported and does not loop`() = runTest {
        val registrar = registrar(this)
        devices.registerFailures += DataError.Rejected(409, "stale_generation", "conflict")
        devices.registerFailures += DataError.Rejected(409, "stale_generation", "conflict")

        val result = registrar.sync()

        assertThat(result).isInstanceOf(PushSyncResult.Failed::class.java)
        assertThat(devices.registrations).hasSize(2)
        assertThat(store.current()).isNull()
    }

    @Test
    fun `an offline phone changes nothing and the next trigger tries again`() = runTest {
        val registrar = registrar(this)
        devices.registerFailures += DataError.Offline(RuntimeException())

        assertThat(registrar.sync()).isInstanceOf(PushSyncResult.Failed::class.java)
        assertThat(store.current()).isNull()

        assertThat(registrar.sync()).isEqualTo(PushSyncResult.Registered(1))
    }

    @Test
    fun `settings that cannot be read are not taken for a no`() = runTest {
        val registrar = registrar(this)
        registrar.sync()
        settings.loadError = DataError.Offline(RuntimeException())

        val result = registrar.sync(force = true)

        assertThat(result).isInstanceOf(PushSyncResult.Failed::class.java)
        assertThat(devices.revokes).isEqualTo(0)
        assertThat(store.current()).isNotNull()
    }

    @Test
    fun `a sign-out drops the binding at once and asks the provider to forget the address`() =
        runTest {
            val registrar = registrar(this)
            registrar.sync()

            registrar.onSignedOut()
            testScheduler.advanceUntilIdle()

            assertThat(store.current()).isNull()
            assertThat(provider.deleted).isEqualTo(1)
            assertThat(devices.revokes).isEqualTo(0)
        }

    @Test
    fun `the install keeps its id through a sign-out`() = runTest {
        val registrar = registrar(this)
        val first = store.installationId()
        registrar.sync()

        registrar.onSignedOut()

        assertThat(store.installationId()).isEqualTo(first)
        assertThat(first).matches("[0-9a-f-]{36}")
    }
}
