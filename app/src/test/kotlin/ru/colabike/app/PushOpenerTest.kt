package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.colabike.app.navigation.Destination
import ru.colabike.app.push.PushOpener
import ru.colabike.app.push.PushTap
import ru.colabike.app.push.PushTarget
import ru.colabike.core.auth.AuthState
import ru.colabike.core.model.DataError

/** What a tap does: the destination goes on, and opening reads the notification. */
@OptIn(ExperimentalCoroutinesApi::class)
class PushOpenerTest {
    private val id = "00000000-0000-4000-8000-00000000000a"
    private val event = "00000000-0000-4000-9000-000000000066"
    private val tap = PushTap(event, "like", PushTarget("bike", id, null, null, null))

    @Test
    fun `the tap offers its destination and, signed in, marks the notification read`() = runTest {
        val pending = FakePending()
        val notifications = FakeNotifications()
        val opener =
            PushOpener(
                pending,
                notifications,
                FakeAuth(AuthState.SignedIn(account)),
                backgroundScope,
            )

        opener.opened(tap)
        runCurrent()

        assertThat(pending.destination.value).isEqualTo(Destination.Bike(id))
        assertThat(notifications.marks).containsExactly("one" to listOf(event))
    }

    @Test
    fun `while the session is read the mark waits, and goes once there is one`() = runTest {
        val auth = FakeAuth(AuthState.Restoring)
        val notifications = FakeNotifications()
        val opener = PushOpener(FakePending(), notifications, auth, backgroundScope)

        opener.opened(tap)
        runCurrent()
        assertThat(notifications.marks).isEmpty()

        auth.state.value = AuthState.SignedIn(account)
        runCurrent()

        assertThat(notifications.marks).containsExactly("one" to listOf(event))
    }

    @Test
    fun `a signed-out person is taken to the destination but nothing is marked`() = runTest {
        val pending = FakePending()
        val notifications = FakeNotifications()
        val opener =
            PushOpener(pending, notifications, FakeAuth(AuthState.SignedOut), backgroundScope)

        opener.opened(tap)
        runCurrent()

        assertThat(pending.destination.value).isEqualTo(Destination.Bike(id))
        assertThat(notifications.marks).isEmpty()
    }

    @Test
    fun `a session that never comes is not waited for for ever`() = runTest {
        val auth = FakeAuth(AuthState.Restoring)
        val notifications = FakeNotifications()
        val opener =
            PushOpener(
                FakePending(),
                notifications,
                auth,
                backgroundScope,
                waitForSessionMs = 1_000,
            )

        opener.opened(tap)
        advanceTimeBy(2_000)
        auth.state.value = AuthState.SignedIn(account)
        runCurrent()

        assertThat(notifications.marks).isEmpty()
    }

    @Test
    fun `a mark the server refuses leaves the notification unread and is no crash`() = runTest {
        val notifications = FakeNotifications()
        notifications.nextError = DataError.Offline(java.io.IOException())
        val opener =
            PushOpener(
                FakePending(),
                notifications,
                FakeAuth(AuthState.SignedIn(account)),
                backgroundScope,
            )

        opener.opened(tap)
        runCurrent()

        assertThat(notifications.marks).containsExactly("one" to listOf(event))
    }
}
