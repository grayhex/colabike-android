package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PushDeviceRegistration

/**
 * The registry of push addresses against the examples the backend publishes (pinned, see
 * SOURCE.txt): what a registration puts on the wire, and what the answers read as.
 */
class PushDeviceRepositoryTest {
    private val site = TestServer()
    private val devices = NetworkPushDeviceRepository(site.api.personal, Dispatchers.Unconfined)

    @After fun close() = site.close()

    private val contract: JsonObject =
        Json.parseToJsonElement(
                requireNotNull(javaClass.getResource("/contracts/notifications/v1/devices.json")) {
                        "no contract file"
                    }
                    .readText()
            )
            .jsonObject

    private suspend fun failure(block: suspend () -> Unit): DataError =
        try {
            block()
            throw AssertionError("expected a failure")
        } catch (e: DataError) {
            e
        }

    private fun example(name: String): JsonObject = contract[name]!!.jsonObject

    private fun registration(name: String): PushDeviceRegistration {
        val request = example(name)["request"]!!.jsonObject
        return PushDeviceRegistration(
            installationId = request["installationId"]!!.jsonPrimitive.content,
            projectId = request["projectId"]!!.jsonPrimitive.content,
            token = request["token"]!!.jsonPrimitive.content,
            expectedGeneration = request["expectedGeneration"]?.jsonPrimitive?.content?.toInt(),
        )
    }

    @Test
    fun `a registration puts the install, the project and the address, and nothing else`() =
        runTest {
            site.json(200, example("register")["response"]!!.toString())

            val binding = devices.register(registration("register"))

            val request = site.server.takeRequest()
            assertThat(request.method).isEqualTo("PUT")
            assertThat(request.url.encodedPath).isEqualTo("/api/v1/me/push-device")
            val sent = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertThat(sent.keys)
                .containsExactly("installationId", "provider", "projectId", "token")
            assertThat(sent["provider"]!!.jsonPrimitive.content).isEqualTo("rustore")
            assertThat(binding.generation).isEqualTo(1)
            assertThat(binding.provider).isEqualTo("rustore")
            assertThat(binding.projectId).isEqualTo("project-a")
            assertThat(binding.registeredAt).isEqualTo(Instant.parse("2026-10-04T09:00:00Z"))
        }

    @Test
    fun `a new address goes with the generation the phone holds`() = runTest {
        site.json(200, example("rotate")["response"]!!.toString())

        val binding = devices.register(registration("rotate"))

        val sent = Json.parseToJsonElement(site.server.takeRequest().body!!.utf8()).jsonObject
        assertThat(sent["expectedGeneration"]!!.jsonPrimitive.content).isEqualTo("1")
        assertThat(binding.generation).isEqualTo(2)
    }

    @Test
    fun `the address is not in what a binding prints`() = runTest {
        site.json(200, example("register")["response"]!!.toString())

        val binding = devices.register(registration("register"))

        assertThat(binding.toString()).doesNotContain("address-given-by-the-provider")
        assertThat(registration("register").toString())
            .doesNotContain("address-given-by-the-provider")
    }

    @Test
    fun `the current binding is read, and none is not an error`() = runTest {
        site.json(200, example("register")["response"]!!.toString())
        site.json(404, error("not_found"))

        assertThat(devices.current()?.generation).isEqualTo(1)
        assertThat(devices.current()).isNull()
        assertThat(site.server.takeRequest().method).isEqualTo("GET")
    }

    @Test
    fun `a generation the server does not hold is refused as a conflict to read again`() = runTest {
        site.json(409, error("conflict"))

        val failure = failure { devices.register(registration("rotate")) } as DataError.Rejected

        assertThat(failure.status).isEqualTo(409)
        assertThat(failure.code).isEqualTo("conflict")
    }

    @Test
    fun `a project the server does not allow, and a server without push, are told apart`() =
        runTest {
            site.json(400, error("invalid_request"))
            site.json(503, error("service_unavailable"))

            val project =
                failure { devices.register(registration("register")) } as DataError.Rejected
            val unavailable = failure { devices.register(registration("register")) }

            assertThat(project.status).isEqualTo(400)
            assertThat(unavailable).isNotInstanceOf(DataError.Rejected::class.java)
        }

    @Test
    fun `revoking sends a delete and revoking what is not there is fine`() = runTest {
        site.json(204, "")

        devices.revoke()

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/me/push-device")
    }
}
