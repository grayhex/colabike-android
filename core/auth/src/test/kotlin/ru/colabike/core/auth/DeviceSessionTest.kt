package ru.colabike.core.auth

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.DataError
import ru.colabike.core.network.NetworkAccountRepository
import ru.colabike.core.network.apiCall

class DeviceSessionTest {
    private val api = FakeApi()

    @After fun close() = api.close()

    private fun requests() = generateSequence {
        api.server.takeRequest(0, TimeUnit.SECONDS)
    }
        .toList()

    @Test
    fun `sign-in keeps the refresh token in the store and the access token in memory only`() =
        runBlocking {
            val store = MemoryStore()
            val session = api.session(store)

            val account = session.signIn(" rider@example.test ", "right-password")

            assertThat(account.username).isEqualTo("test-rider")
            assertThat(session.state.value).isEqualTo(AuthState.SignedIn(account))
            assertThat(store.value).isEqualTo("cola_rt_A")
            val body = api.server.takeRequest().body!!.utf8()
            assertThat(body).contains("\"email\":\"rider@example.test\"")
            assertThat(body).contains("\"platform\":\"android\"")
            assertThat(body).doesNotContain("code")
        }

    @Test
    fun `a wrong password is a rejection, not a session`() = runBlocking {
        val session = api.session()
        val failure = runCatching {
            session.signIn("rider@example.test", "wrong")
        }
            .exceptionOrNull()
        assertThat((failure as DataError.Rejected).code).isEqualTo("invalid_credentials")
        assertThat(session.state.value).isEqualTo(AuthState.Restoring)
    }

    @Test
    fun `the Bearer token goes to the API host only`() = runBlocking {
        val session = api.session().apply { signIn("a@b.c", "right-password") }
        val authed = api.authed(session)

        authed.account.getMe()
        // Same server under another host name: an image or a link elsewhere gets no token.
        val elsewhere = api.server.url("/api/v1/bikes").newBuilder().host("127.0.0.1").build()
        okhttp3.OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session, api.server.hostName))
            .build()
            .newCall(okhttp3.Request.Builder().url(elsewhere).build())
            .execute()
            .close()

        val (signIn, me, other) = requests()
        assertThat(signIn.headers["Authorization"]).isNull()
        assertThat(me.headers["Authorization"]).isEqualTo("Bearer cola_at_A")
        assertThat(other.headers["Authorization"]).isNull()
    }

    @Test
    fun `token_expired refreshes once and retries the request once`() = runBlocking {
        val session = api.session().apply { signIn("a@b.c", "right-password") }
        api.expiredAccess += "cola_at_A"

        val me = apiCall { api.authed(session).account.getMe() }

        assertThat(me.username).isEqualTo("test-rider")
        assertThat(api.refreshCount.get()).isEqualTo(1)
        val paths = requests().map { it.url.encodedPath to it.headers["Authorization"] }
        assertThat(paths)
            .containsExactly(
                "/api/v1/auth/sessions" to null,
                "/api/v1/me" to "Bearer cola_at_A",
                "/api/v1/auth/sessions/refresh" to null,
                "/api/v1/me" to "Bearer cola_at_B",
            )
            .inOrder()
    }

    @Test
    fun `parallel requests with an expired token share one refresh`() = runBlocking {
        val session = api.session().apply { signIn("a@b.c", "right-password") }
        api.expiredAccess += "cola_at_A"
        api.refreshDelayMs = 200
        val authed = api.authed(session)
        val pool = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)

        val results =
            (1..6).map {
                pool.submit<String> {
                    start.await()
                    authed.account.getMe().username
                }
            }
        start.countDown()

        assertThat(results.map { it.get(10, TimeUnit.SECONDS) })
            .containsExactlyElementsIn(List(6) { "test-rider" })
        assertThat(api.refreshCount.get()).isEqualTo(1)
        pool.shutdown()
    }

    @Test
    fun `a refused refresh ends the session and the caller is sent to sign-in`() = runBlocking {
        val store = MemoryStore()
        val session = api.session(store).apply { signIn("a@b.c", "right-password") }
        api.expiredAccess += "cola_at_A"
        api.refreshes.clear() // the server revoked the session (logout elsewhere, reuse detected)

        val failure = runCatching {
            NetworkAccountRepository(api.authed(session).account, api.media).me()
        }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.SignedOut::class.java)
        assertThat(session.state.value).isEqualTo(AuthState.SignedOut)
        assertThat(store.value).isNull()
    }

    @Test
    fun `invalid_token on any request ends the session`() = runBlocking {
        val store = MemoryStore()
        val session = api.session(store).apply { signIn("a@b.c", "right-password") }
        api.validAccess.clear() // revoked on the server

        val failure = runCatching {
            NetworkAccountRepository(api.authed(session).account, api.media).me()
        }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.SignedOut::class.java)
        assertThat(session.state.value).isEqualTo(AuthState.SignedOut)
        assertThat(store.value).isNull()
        assertThat(api.refreshCount.get()).isEqualTo(0)
    }

    @Test
    fun `a lost refresh answer is retried with the same token`() = runBlocking {
        val session = api.session().apply { signIn("a@b.c", "right-password") }
        api.expiredAccess += "cola_at_A"
        api.dropNextRefreshAnswer = true

        val me = apiCall { api.authed(session).account.getMe() }

        assertThat(me.username).isEqualTo("test-rider")
        assertThat(api.refreshCount.get()).isEqualTo(2)
        assertThat(session.state.value).isInstanceOf(AuthState.SignedIn::class.java)
    }

    @Test
    fun `a server fault on refresh keeps the credentials and the next request recovers`() =
        runBlocking {
            val store = MemoryStore()
            val session = api.session(store).apply { signIn("a@b.c", "right-password") }
            api.expiredAccess += "cola_at_A"
            api.refreshStatus = 503

            val failure = runCatching {
                NetworkAccountRepository(api.authed(session).account, api.media).me()
            }

            // Not "signed out": the server was unwell, nothing proved the session over.
            assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.Offline::class.java)
            assertThat(session.state.value).isInstanceOf(AuthState.SignedIn::class.java)
            assertThat(store.value).isEqualTo("cola_rt_A")

            api.refreshStatus = null
            val me = NetworkAccountRepository(api.authed(session).account, api.media).me()
            assertThat(me.username).isEqualTo("test-rider")
            assertThat(store.value).isEqualTo("cola_rt_B")
        }

    @Test
    fun `a throttled refresh keeps the credentials too`() = runBlocking {
        val store = MemoryStore()
        val session = api.session(store).apply { signIn("a@b.c", "right-password") }
        api.expiredAccess += "cola_at_A"
        api.refreshStatus = 429

        val failure = runCatching {
            NetworkAccountRepository(api.authed(session).account, api.media).me()
        }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.Offline::class.java)
        assertThat(session.state.value).isInstanceOf(AuthState.SignedIn::class.java)
        assertThat(store.value).isEqualTo("cola_rt_A")
    }

    @Test
    fun `a connection that fails twice in a row keeps the credentials`() = runBlocking {
        val store = MemoryStore()
        val session = api.session(store).apply { signIn("a@b.c", "right-password") }
        api.expiredAccess += "cola_at_A"
        // Every answer is lost, however often OkHttp itself repeats a call on a broken connection.
        api.lostRefreshAnswers.set(Int.MAX_VALUE)

        val failure = runCatching {
            NetworkAccountRepository(api.authed(session).account, api.media).me()
        }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.Offline::class.java)
        assertThat(session.state.value).isInstanceOf(AuthState.SignedIn::class.java)
        assertThat(store.value).isEqualTo("cola_rt_A")
        // The call was repeated once with the same token, and then given up on.
        assertThat(api.refreshCount.get()).isAtLeast(2)

        // The connection is back: the very same token still works.
        api.lostRefreshAnswers.set(0)
        val me = NetworkAccountRepository(api.authed(session).account, api.media).me()
        assertThat(me.username).isEqualTo("test-rider")
    }

    @Test
    fun `sign-out ends the session once and a late invalid_token cannot revive it`() = runBlocking {
        val store = MemoryStore()
        val session = api.session(store).apply { signIn("a@b.c", "right-password") }
        val states = mutableListOf<AuthState>()
        val seen = session.state.value
        states += seen

        session.signOut {}
        session.invalidate("cola_at_A") // a request that was in flight when the person left

        assertThat(session.state.value).isEqualTo(AuthState.SignedOut)
        assertThat(store.value).isNull()
    }

    @Test
    fun `after a restart the stored refresh token is exchanged before the first request`() =
        runBlocking {
            api.refreshes["cola_rt_A"] = "B"
            val session = api.session(MemoryStore("cola_rt_A"))

            session.restore()
            assertThat(session.state.value).isEqualTo(AuthState.SignedIn(account = null))
            apiCall { api.authed(session).account.getMe() }

            val paths = requests().map { it.url.encodedPath to it.headers["Authorization"] }
            assertThat(paths)
                .containsExactly(
                    "/api/v1/auth/sessions/refresh" to null,
                    "/api/v1/me" to "Bearer cola_at_B",
                )
                .inOrder()
            assertThat((session.state.value as AuthState.SignedIn).account?.username)
                .isEqualTo("test-rider")
        }

    @Test
    fun `a token about to end is refreshed before it is sent`() = runBlocking {
        val session = api.session(clock = Clock.fixed(NOW.plusSeconds(880), ZoneOffset.UTC))
        session.signIn("a@b.c", "right-password") // expires at NOW + 900 s: 20 s left

        apiCall { api.authed(session).account.getMe() }

        assertThat(api.refreshCount.get()).isEqualTo(1)
    }

    @Test
    fun `sign-out revokes on the server and clears everything even when offline`() = runBlocking {
        val store = MemoryStore()
        val session = api.session(store).apply { signIn("a@b.c", "right-password") }
        val authed = api.authed(session)

        session.signOut { apiCall { authed.sessions.revokeCurrentSession() } }
        assertThat(api.validAccess).doesNotContain("cola_at_A")
        assertThat(session.state.value).isEqualTo(AuthState.SignedOut)
        assertThat(store.value).isNull()

        session.signIn("a@b.c", "right-password")
        session.signOut { throw DataError.Offline(java.io.IOException("no network")) }
        assertThat(session.state.value).isEqualTo(AuthState.SignedOut)
        assertThat(store.value).isNull()
    }

    @Test
    fun `no token appears in what screens or logs can print`() = runBlocking {
        val session = api.session().apply { signIn("a@b.c", "right-password") }
        val printed = listOf(session.state.value.toString(), Pkce.create().toString())
        printed.forEach {
            assertThat(it).doesNotContain("cola_at_")
            assertThat(it).doesNotContain("cola_rt_")
            assertThat(it).doesNotContain("rider@example.test")
        }
    }
}
