package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.colabike.core.model.AccountDeletion
import ru.colabike.core.model.DataError
import ru.colabike.core.model.DeletionProof

class AccountDeletionRepositoryTest {
    private val site = TestServer()
    private val repository =
        NetworkAccountDeletionRepository(site.api.account, Dispatchers.Unconfined)

    @After fun close() = site.close()

    @Test
    fun `how the account confirms the deletion is read from the server`() = runTest {
        site.json(200, """{"method":"password","allowed":true,"reason":null}""")
        site.json(200, """{"method":"yandex","allowed":true,"reason":null}""")
        site.json(200, """{"method":null,"allowed":false,"reason":"admin"}""")
        site.json(200, """{"method":null,"allowed":false,"reason":"no_method"}""")

        assertThat(repository.deletion())
            .isEqualTo(AccountDeletion(AccountDeletion.Method.Password, true, null))
        assertThat(repository.deletion())
            .isEqualTo(AccountDeletion(AccountDeletion.Method.Yandex, true, null))
        assertThat(repository.deletion())
            .isEqualTo(AccountDeletion(null, false, AccountDeletion.Reason.Admin))
        assertThat(repository.deletion())
            .isEqualTo(AccountDeletion(null, false, AccountDeletion.Reason.NoMethod))
        assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/account/deletion")
    }

    @Test
    fun `a method or a reason this version does not know is a deletion it does not offer`() =
        runTest {
            site.json(200, """{"method":"passkey","allowed":true,"reason":null}""")
            site.json(200, """{"method":null,"allowed":false,"reason":"frozen"}""")

            val unknownMethod = repository.deletion()
            assertThat(unknownMethod.allowed).isFalse()
            assertThat(unknownMethod.method).isNull()
            assertThat(repository.deletion().reason).isEqualTo(AccountDeletion.Reason.Other)
        }

    @Test
    fun `a password goes with the confirmation word and nothing else`() = runTest {
        site.server.enqueue(MockResponse.Builder().code(204).build())

        repository.delete(DeletionProof.Password("секрет-пароль-1"))

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/account/delete")
        val body = request.body!!.utf8()
        assertThat(body).contains("\"confirm\":\"УДАЛИТЬ\"")
        assertThat(body).contains("\"password\":\"секрет-пароль-1\"")
        // The server wants exactly one way to confirm: the other is absent, not null.
        assertThat(body).doesNotContain("reauth")
        assertThat(body).doesNotContain("null")
    }

    @Test
    fun `a fresh sign-in with the provider goes as reauth`() = runTest {
        site.server.enqueue(MockResponse.Builder().code(204).build())
        val code = "cola_ac_" + "A".repeat(43)
        val verifier = "V".repeat(43)

        repository.delete(DeletionProof.Provider(code, verifier))

        val body = site.server.takeRequest().body!!.utf8()
        assertThat(body).contains("\"reauth\":{\"code\":\"$code\",\"codeVerifier\":\"$verifier\"}")
        assertThat(body).doesNotContain("password")
    }

    @Test
    fun `a proof the server refuses is a rejection with its code, and the account stays`() =
        runTest {
            site.json(401, error("invalid_credentials", "Пароль не подходит."))
            site.json(409, error("conflict", "Администратору нельзя."))

            val wrong =
                assertThrows(DataError.Rejected::class.java) {
                    kotlinx.coroutines.runBlocking {
                        repository.delete(DeletionProof.Password("не тот"))
                    }
                }
            assertThat(wrong.status).isEqualTo(401)
            assertThat(wrong.code).isEqualTo("invalid_credentials")
            val admin =
                assertThrows(DataError.Rejected::class.java) {
                    kotlinx.coroutines.runBlocking {
                        repository.delete(DeletionProof.Password("любой"))
                    }
                }
            assertThat(admin.status).isEqualTo(409)
        }

    @Test
    fun `a proof never shows in its text form`() {
        assertThat(DeletionProof.Password("секрет").toString()).doesNotContain("секрет")
        val provider = DeletionProof.Provider("cola_ac_x", "verifier-x")
        assertThat(provider.toString()).doesNotContain("cola_ac_x")
        assertThat(provider.toString()).doesNotContain("verifier-x")
    }
}
