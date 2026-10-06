package ru.colabike.app

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.colabike.app.auth.AuthController
import ru.colabike.app.auth.YandexFailure
import ru.colabike.app.auth.YandexReauth
import ru.colabike.core.auth.AuthState
import ru.colabike.core.auth.DeviceInfo
import ru.colabike.core.auth.DeviceSession
import ru.colabike.core.auth.SecretStore
import ru.colabike.core.auth.YandexSignIn
import ru.colabike.core.network.ApiConfig
import ru.colabike.core.network.ColaBikeApi
import ru.colabike.core.network.HttpClients
import ru.colabike.core.network.MediaUrls

/** What the activity hands to sign-in: the return of the browser flow, and nothing else. */
class AuthControllerTest {
    private class Memory(var value: String? = null) : SecretStore {
        override fun read() = value

        override fun write(token: String) {
            value = token
        }

        override fun clear() {
            value = null
        }
    }

    private val config = ApiConfig("https://colabike.test", "0.1.0")
    private val code = "cola_ac_" + "a".repeat(43)
    private val verifier = Memory()

    private suspend fun controller(
        scope: TestScope,
        signedIn: Boolean,
        yandexReady: () -> Boolean = { true },
    ): AuthController {
        val session =
            DeviceSession(
                plainSessions = ColaBikeApi(config, HttpClients.base(config)).sessions,
                store = Memory(if (signedIn) "cola_rt_stored" else null),
                device = DeviceInfo("Test", "0.1.0"),
                media = MediaUrls(config.siteUrl),
            )
        session.restore()
        return AuthController(
            session = session,
            yandex = YandexSignIn(config.siteUrl, "https://colabike.test/app/auth", verifier),
            yandexReady = yandexReady,
            revoke = {},
            scope = scope,
        )
    }

    @Test
    fun `the Yandex button follows the build and the server, asked each time`() = runTest {
        var ready = false
        val auth = controller(this, signedIn = false, yandexReady = { ready })

        assertThat(auth.yandexEnabled).isFalse()
        ready = true
        assertThat(auth.yandexEnabled).isTrue()
    }

    @Test
    fun `a cancelled browser flow is reported to the sign-in screen`() = runTest {
        val auth = controller(this, signedIn = false)
        auth.startUrlForTest()

        auth.yandexFailures.test {
            auth.handleLink("https://colabike.test/app/auth?error=cancelled")

            val failure = awaitItem()
            assertThat(failure).isInstanceOf(YandexFailure.Flow::class.java)
            assertThat((failure as YandexFailure.Flow).reason)
                .isEqualTo(YandexSignIn.Reason.Cancelled)
        }
    }

    @Test
    fun `a stray return while signed in finishes nothing and leaves no error behind`() = runTest {
        val auth = controller(this, signedIn = true)
        assertThat(auth.state.value).isInstanceOf(AuthState.SignedIn::class.java)

        auth.yandexFailures.test {
            auth.handleLink("https://colabike.test/app/auth?code=$code")
            auth.handleLink("https://colabike.test/app/auth?error=cancelled")

            expectNoEvents()
        }
        assertThat(auth.state.value).isInstanceOf(AuthState.SignedIn::class.java)
    }

    @Test
    fun `a return that a signed-in person asked for is a proof for the screen, not a session`() =
        runTest {
            val auth = controller(this, signedIn = true)
            auth.beginReauth()

            auth.yandexReauth.test {
                auth.handleLink("https://colabike.test/app/auth?code=$code")

                val proof = awaitItem() as YandexReauth.Proof
                assertThat(proof.code).isEqualTo(code)
                assertThat(proof.verifier).isNotEmpty()
                // Secrets stay out of the text form, which is what logs and crash reports see.
                assertThat(proof.toString()).doesNotContain(code)
                assertThat(proof.toString()).doesNotContain(proof.verifier)
            }
            assertThat(auth.state.value).isInstanceOf(AuthState.SignedIn::class.java)
            // The verifier was handed out once.
            auth.yandexReauth.test {
                auth.handleLink("https://colabike.test/app/auth?code=$code")
                expectNoEvents()
            }
        }

    @Test
    fun `a confirmation that failed is told only to the screen that asked for it`() = runTest {
        val auth = controller(this, signedIn = true)

        auth.yandexReauth.test {
            // Nobody asked: a cancelled return is nobody's news.
            auth.handleLink("https://colabike.test/app/auth?error=cancelled")
            expectNoEvents()
            auth.beginReauth()
            auth.handleLink("https://colabike.test/app/auth?error=cancelled")

            assertThat(awaitItem()).isEqualTo(YandexReauth.Failed(YandexSignIn.Reason.Cancelled))
        }
    }

    @Test
    fun `an address that is not the return is none of its business`() = runTest {
        val auth = controller(this, signedIn = false)

        auth.yandexFailures.test {
            auth.handleLink("https://colabike.test/b/6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
            auth.handleLink("https://evil.example/app/auth?code=$code")

            expectNoEvents()
        }
    }

    /** Opens the flow as the button does, so that a verifier is waiting. */
    private fun AuthController.startUrlForTest() {
        YandexSignIn(config.siteUrl, "https://colabike.test/app/auth", verifier).startUrl()
    }
}
