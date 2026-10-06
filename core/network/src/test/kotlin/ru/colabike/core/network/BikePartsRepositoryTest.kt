package ru.colabike.core.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BikeChange
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.ComponentDraft
import ru.colabike.core.model.ComponentPatch
import ru.colabike.core.model.DataError

class BikePartsRepositoryTest {
    private val site = TestServer()
    private val bikes =
        NetworkBikesRepository(
            site.api.bikes,
            site.api.search,
            site.media,
            Dispatchers.Unconfined,
            site.api::bikesWithNulls,
        )
    private val bike = BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
    private val part = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f"
    private val key = "0c7d9a1e-5b2f-4c3d-8e4a-1f2b3c4d5e6f"

    @After fun close() = site.close()

    private val partJson =
        """{"id":"$part","modelId":null,"section":"build","category":"Цепь","name":"KMC X11",
            "notes":"","url":"","groupId":"drivetrain","sortOrder":3,"price":1200}"""

    private val draft =
        ComponentDraft(
            section = "build",
            category = " Цепь ",
            name = " KMC X11 ",
            notes = "",
            priceRub = 1200.0,
            url = "",
            groupId = "drivetrain",
        )

    private fun body(request: RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    @Test
    fun `a new part is a POST with its key and the group the form reckoned`() = runTest {
        site.json(201, partJson, "ETag", "\"part-v1\"")

        bikes.changes.test {
            val added = bikes.addComponent(bike, draft, key)

            assertThat(added.name).isEqualTo("KMC X11")
            assertThat(added.version).isEqualTo("\"part-v1\"")
            assertThat(awaitItem()).isEqualTo(BikeChange.Parts(bike))
        }

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/${bike.value}/components")
        assertThat(request.headers["Idempotency-Key"]).isEqualTo(key)
        val sent = body(request)
        assertThat(sent["section"]).isEqualTo(JsonPrimitive("build"))
        assertThat(sent["category"]).isEqualTo(JsonPrimitive("Цепь"))
        assertThat(sent["name"]).isEqualTo(JsonPrimitive("KMC X11"))
        assertThat(sent["groupId"]).isEqualTo(JsonPrimitive("drivetrain"))
        assertThat(sent["price"]).isEqualTo(JsonPrimitive(1200.0))
    }

    @Test
    fun `a part of a public bike needs a confirmed address`() = runTest {
        site.json(403, error("email_verification_required"))

        bikes.changes.test {
            val result = runCatching { bikes.addComponent(bike, draft, key) }

            val refused = result.exceptionOrNull() as DataError.Rejected
            assertThat(refused.code).isEqualTo("email_verification_required")
            expectNoEvents()
        }
    }

    @Test
    fun `a change sends only what changed, names the version when there is one`() = runTest {
        site.json(200, partJson, "ETag", "\"part-v2\"")

        val saved =
            bikes.updateComponent(bike, part, ComponentPatch(name = "KMC X12"), "\"part-v1\"")

        assertThat(saved.version).isEqualTo("\"part-v2\"")
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.url.encodedPath)
            .isEqualTo("/api/v1/bikes/${bike.value}/components/$part")
        assertThat(request.headers["If-Match"]).isEqualTo("\"part-v1\"")
        assertThat(body(request)).isEqualTo(JsonObject(mapOf("name" to JsonPrimitive("KMC X12"))))
    }

    @Test
    fun `a part with no version is changed without one`() = runTest {
        site.json(200, partJson)

        bikes.updateComponent(bike, part, ComponentPatch(notes = "11 скоростей"), null)

        assertThat(site.server.takeRequest().headers["If-Match"]).isNull()
    }

    @Test
    fun `a price taken away goes as null`() = runTest {
        site.json(200, partJson)

        bikes.updateComponent(bike, part, ComponentPatch(clearPrice = true, notes = "x"), null)

        val sent = body(site.server.takeRequest())
        assertThat(sent["price"]).isEqualTo(JsonNull)
        assertThat(sent.keys).containsExactly("price", "notes")
    }

    @Test
    fun `a part another device changed first is 412`() = runTest {
        site.json(412, error("precondition_failed"))

        bikes.changes.test {
            val result = runCatching {
                bikes.updateComponent(bike, part, ComponentPatch(name = "Другое"), "\"part-v1\"")
            }

            assertThat((result.exceptionOrNull() as DataError.Rejected).status).isEqualTo(412)
            expectNoEvents()
        }
    }

    @Test
    fun `removing is DELETE and the bike's build is announced as changed`() = runTest {
        site.json(204, "")

        bikes.changes.test {
            bikes.removeComponent(bike, part)

            assertThat(awaitItem()).isEqualTo(BikeChange.Parts(bike))
        }
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath)
            .isEqualTo("/api/v1/bikes/${bike.value}/components/$part")
    }

    @Test
    fun `a malformed id is not found without a request`() = runTest {
        val result = runCatching { bikes.removeComponent(bike, "nope") }

        assertThat(result.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `the order of groups is a PUT of all the keys and the bike comes back`() = runTest {
        site.json(200, site.fixture("bike.json"), "ETag", "\"bike-v3\"")

        bikes.changes.test {
            val saved = bikes.setGroupOrder(bike, listOf("brakes", "frame", "drivetrain"))

            assertThat(saved.version).isEqualTo("\"bike-v3\"")
            assertThat(awaitItem()).isEqualTo(BikeChange.Saved(saved))
        }
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/${bike.value}/group-order")
        assertThat(body(request)["groups"])
            .isEqualTo(
                JsonArray(
                    listOf(
                        JsonPrimitive("brakes"),
                        JsonPrimitive("frame"),
                        JsonPrimitive("drivetrain"),
                    )
                )
            )
    }
}
