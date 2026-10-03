package ru.colabike.core.auth

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class YandexSignInTest {
    private val pending = MemoryStore()
    private val flow = YandexSignIn("https://colabike.ru", "https://colabike.ru/app/auth", pending)
    private val code = "cola_ac_" + "a".repeat(43)

    @Test
    fun `RFC 7636 appendix B vector`() {
        assertThat(Pkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
            .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        assertThat(Pkce.create().verifier).matches("[A-Za-z0-9_-]{43}")
    }

    @Test
    fun `the start address carries only the S256 challenge of the kept verifier`() {
        val url = flow.startUrl().toHttpUrl()

        assertThat(url.encodedPath).isEqualTo("/api/auth/native/start")
        assertThat(url.queryParameter("provider")).isEqualTo("yandex")
        assertThat(url.queryParameter("code_challenge_method")).isEqualTo("S256")
        assertThat(url.queryParameter("code_challenge"))
            .isEqualTo(Pkce.challengeOf(pending.value!!))
        assertThat(url.toString()).doesNotContain(pending.value!!)
    }

    @Test
    fun `the return link hands out the code with the verifier once`() {
        flow.startUrl()
        val verifier = pending.value

        val result = flow.handleReturn("https://colabike.ru/app/auth?code=$code")

        assertThat(result).isInstanceOf(YandexSignIn.Return.Code::class.java)
        assertThat((result as YandexSignIn.Return.Code).verifier).isEqualTo(verifier)
        assertThat(result.code).isEqualTo(code)
        assertThat(result.toString()).doesNotContain(code)
        assertThat(pending.value).isNull()
        assertThat(flow.handleReturn("https://colabike.ru/app/auth?code=$code"))
            .isEqualTo(YandexSignIn.Return.Failed(YandexSignIn.Reason.NoPendingSignIn))
    }

    @Test
    fun `errors of the flow and foreign links`() {
        flow.startUrl()
        assertThat(flow.handleReturn("https://colabike.ru/app/auth?error=cancelled"))
            .isEqualTo(YandexSignIn.Return.Failed(YandexSignIn.Reason.Cancelled))
        assertThat(pending.value).isNull()

        flow.startUrl()
        assertThat(flow.handleReturn("https://colabike.ru/app/auth?code=forged"))
            .isEqualTo(YandexSignIn.Return.Failed(YandexSignIn.Reason.Unknown))
        assertThat(flow.handleReturn("https://evil.example/app/auth?code=$code"))
            .isEqualTo(YandexSignIn.Return.NotOurs)
        assertThat(flow.handleReturn("http://colabike.ru/app/auth?code=$code"))
            .isEqualTo(YandexSignIn.Return.NotOurs)
        assertThat(flow.handleReturn("https://colabike.ru/b/abc"))
            .isEqualTo(YandexSignIn.Return.NotOurs)
    }

    @Test
    fun `a link that only looks like ours is not ours and keeps the sign-in waiting`() {
        flow.startUrl()
        val verifier = pending.value

        listOf(
                "https://colabike.ru:8443/app/auth?code=$code", // a port the filter lets through
                "https://colabike.ru.evil.example/app/auth?code=$code",
                "https://evil.colabike.ru/app/auth?code=$code",
                "https://colabike.ru@evil.example/app/auth?code=$code",
                "https://colabike.ru/app/auth/extra?code=$code",
                "https://colabike.ru/app/author?code=$code",
                "javascript:alert(1)",
                "not a url",
            )
            .forEach { link ->
                assertThat(flow.handleReturn(link)).isEqualTo(YandexSignIn.Return.NotOurs)
            }
        // None of them spent the verifier of the sign-in that is really in progress.
        assertThat(pending.value).isEqualTo(verifier)
    }

    @Test
    fun `starting again replaces the verifier, and only the latest start can finish`() {
        flow.startUrl()
        val first = pending.value
        flow.startUrl()
        val second = pending.value

        assertThat(second).isNotEqualTo(first)
        val result = flow.handleReturn("https://colabike.ru/app/auth?code=$code")
        assertThat((result as YandexSignIn.Return.Code).verifier).isEqualTo(second)
    }

    @Test
    fun `a cancelled sign-in forgets its verifier, a late code finds nothing`() {
        flow.startUrl()
        flow.handleReturn("https://colabike.ru/app/auth?error=cancelled")

        assertThat(flow.handleReturn("https://colabike.ru/app/auth?code=$code"))
            .isEqualTo(YandexSignIn.Return.Failed(YandexSignIn.Reason.NoPendingSignIn))
    }

    @Test
    fun `a return without a code or an error is a failure, not a crash`() {
        flow.startUrl()

        assertThat(flow.handleReturn("https://colabike.ru/app/auth"))
            .isEqualTo(YandexSignIn.Return.Failed(YandexSignIn.Reason.Unknown))
    }
}
