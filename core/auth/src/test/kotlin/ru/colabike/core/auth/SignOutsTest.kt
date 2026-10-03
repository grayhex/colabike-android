package ru.colabike.core.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SignOutsTest {
    private val signedIn = AuthState.SignedIn(account = null)

    private fun count(vararg states: AuthState) = runBlocking {
        flowOf(*states).signOuts().toList().size
    }

    @Test
    fun `a start without a session is not a sign-out`() {
        assertThat(count(AuthState.Restoring, AuthState.SignedOut)).isEqualTo(0)
    }

    @Test
    fun `leaving a session is a sign-out`() {
        assertThat(count(AuthState.Restoring, signedIn, AuthState.SignedOut)).isEqualTo(1)
    }

    @Test
    fun `each departure counts, each arrival does not`() {
        assertThat(
                count(
                    AuthState.SignedOut,
                    signedIn,
                    AuthState.SignedOut,
                    signedIn,
                    AuthState.SignedOut,
                )
            )
            .isEqualTo(2)
    }

    @Test
    fun `signing in alone is none`() {
        assertThat(count(AuthState.Restoring, signedIn)).isEqualTo(0)
    }
}
