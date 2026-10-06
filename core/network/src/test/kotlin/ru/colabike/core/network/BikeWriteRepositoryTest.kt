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
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BikeChange
import ru.colabike.core.model.BikeDraft
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikePatch
import ru.colabike.core.model.ClassificationDraft
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PriceVisibility

class BikeWriteRepositoryTest {
    private val site = TestServer()
    private val bikes =
        NetworkBikesRepository(
            site.api.bikes,
            site.api.search,
            site.media,
            Dispatchers.Unconfined,
            site.api::bikesWithNulls,
        )
    private val id = "6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"
    private val key = "0c7d9a1e-5b2f-4c3d-8e4a-1f2b3c4d5e6f"

    @After fun close() = site.close()

    private val draft =
        BikeDraft(
            name = " Мой гравел ",
            brand = "Canyon",
            model = "Grail",
            trim = "",
            year = 2023,
            classification =
                ClassificationDraft(
                    category = "road_gravel",
                    subtype = "gravel",
                    uses = listOf("gravel", "bikepacking"),
                    electric = false,
                    fatbike = false,
                ),
            description = "",
            color = "",
            size = "",
            weightKg = null,
            mileageKm = 0,
            manufacturerUrl = "",
            priceRub = null,
            priceVisibility = PriceVisibility(),
            isFormer = false,
            isPublic = false,
        )

    private fun body(request: mockwebserver3.RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    @Test
    fun `the owner's page carries the version an edit names, another reader's does not`() =
        runTest {
            site.json(200, site.fixture("bike.json"), "ETag", "\"bike-v7\"")
            site.json(200, site.fixture("bike.json"))

            val own = bikes.bike(BikeId(id))
            val other = bikes.bike(BikeId(id))

            assertThat(own.version).isEqualTo("\"bike-v7\"")
            assertThat(other.version).isNull()
            assertThat(own.priceVisibility).isEqualTo(PriceVisibility(true, true, false))
            assertThat(own.summary.classification.uses).isEmpty()
        }

    @Test
    fun `a new bike is a POST with its key, the audience named and the text trimmed`() = runTest {
        site.json(201, site.fixture("bike.json"), "ETag", "\"bike-v1\"")

        bikes.changes.test {
            val created = bikes.create(draft, key)

            assertThat(created.version).isEqualTo("\"bike-v1\"")
            assertThat(awaitItem()).isEqualTo(BikeChange.Saved(created))
        }

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes")
        assertThat(request.headers["Idempotency-Key"]).isEqualTo(key)
        val sent = body(request)
        assertThat(sent["name"]).isEqualTo(JsonPrimitive("Мой гравел"))
        assertThat(sent["isPublic"]).isEqualTo(JsonPrimitive(false))
        assertThat(sent["year"]).isEqualTo(JsonPrimitive(2023))
        val classification = sent["classification"]!!.jsonObject
        assertThat(classification["category"]).isEqualTo(JsonPrimitive("road_gravel"))
        assertThat(classification["subtype"]).isEqualTo(JsonPrimitive("gravel"))
        assertThat(classification["uses"])
            .isEqualTo(JsonArray(listOf(JsonPrimitive("gravel"), JsonPrimitive("bikepacking"))))
        // A weight and a price nobody gave are not sent as null: the server takes them as empty.
        assertThat(sent.keys).doesNotContain("weight")
        assertThat(sent.keys).doesNotContain("price")
    }

    @Test
    fun `publishing without a confirmed address is refused with its own code`() = runTest {
        site.json(403, error("email_verification_required"))

        bikes.changes.test {
            val result = runCatching { bikes.create(draft.copy(isPublic = true), key) }

            val refused = result.exceptionOrNull() as DataError.Rejected
            assertThat(refused.status).isEqualTo(403)
            assertThat(refused.code).isEqualTo("email_verification_required")
            expectNoEvents()
        }
    }

    @Test
    fun `an edit names the version it read and sends only what changed`() = runTest {
        site.json(200, site.fixture("bike.json"), "ETag", "\"bike-v8\"")

        val saved = bikes.update(BikeId(id), BikePatch(name = "Новое имя"), "\"bike-v7\"")

        assertThat(saved.version).isEqualTo("\"bike-v8\"")
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/$id")
        assertThat(request.headers["If-Match"]).isEqualTo("\"bike-v7\"")
        assertThat(body(request)).isEqualTo(JsonObject(mapOf("name" to JsonPrimitive("Новое имя"))))
    }

    @Test
    fun `a weight and a price taken away go as null, and the rest of the edit is kept`() = runTest {
        site.json(200, site.fixture("bike.json"), "ETag", "\"bike-v8\"")

        bikes.update(
            BikeId(id),
            BikePatch(color = "Красный", clearWeight = true, clearPrice = true),
            "\"bike-v7\"",
        )

        val sent = body(site.server.takeRequest())
        assertThat(sent["weight"]).isEqualTo(JsonNull)
        assertThat(sent["price"]).isEqualTo(JsonNull)
        assertThat(sent["color"]).isEqualTo(JsonPrimitive("Красный"))
        assertThat(sent.keys).containsExactly("weight", "price", "color")
    }

    @Test
    fun `a classification is sent whole and the prices shown are named all three`() = runTest {
        site.json(200, site.fixture("bike.json"), "ETag", "\"bike-v8\"")

        bikes.update(
            BikeId(id),
            BikePatch(
                classification = ClassificationDraft(category = "mtb", subtype = "trail"),
                priceVisibility = PriceVisibility(bike = true),
                priceRub = 120000.0,
            ),
            "\"bike-v7\"",
        )

        val sent = body(site.server.takeRequest())
        val classification = sent["classification"]!!.jsonObject
        assertThat(classification["category"]).isEqualTo(JsonPrimitive("mtb"))
        assertThat(classification["uses"]).isEqualTo(JsonArray(emptyList()))
        assertThat(sent["priceVisibility"]!!.jsonObject)
            .isEqualTo(
                JsonObject(
                    mapOf(
                        "bike" to JsonPrimitive(true),
                        "components" to JsonPrimitive(false),
                        "accessories" to JsonPrimitive(false),
                    )
                )
            )
        assertThat(sent["price"]).isEqualTo(JsonPrimitive(120000.0))
    }

    @Test
    fun `a bike another device changed first is 412 and is announced to nobody`() = runTest {
        site.json(412, error("precondition_failed"))

        bikes.changes.test {
            val result = runCatching {
                bikes.update(BikeId(id), BikePatch(name = "Другое"), "\"bike-v7\"")
            }

            assertThat((result.exceptionOrNull() as DataError.Rejected).status).isEqualTo(412)
            expectNoEvents()
        }
    }

    @Test
    fun `an edit with no version is not sent`() = runTest {
        val result = runCatching { bikes.update(BikeId(id), BikePatch(name = "Имя"), null) }

        assertThat((result.exceptionOrNull() as DataError.Rejected).status).isEqualTo(428)
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an edit that changes nothing only reads the bike`() = runTest {
        site.json(200, site.fixture("bike.json"), "ETag", "\"bike-v7\"")

        val read = bikes.update(BikeId(id), BikePatch(), "\"bike-v7\"")

        assertThat(read.version).isEqualTo("\"bike-v7\"")
        assertThat(site.server.requestCount).isEqualTo(1)
        assertThat(site.server.takeRequest().method).isEqualTo("GET")
    }

    @Test
    fun `deleting is DELETE and the bike is announced as gone`() = runTest {
        site.json(204, "")

        bikes.changes.test {
            bikes.delete(BikeId(id))

            assertThat(awaitItem()).isEqualTo(BikeChange.Removed(BikeId(id)))
        }
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/$id")
    }

    @Test
    fun `a bike with rides is not deleted and the server's words are kept`() = runTest {
        site.json(409, error("conflict", "У велосипеда есть покатушки."))

        bikes.changes.test {
            val result = runCatching { bikes.delete(BikeId(id)) }

            val refused = result.exceptionOrNull() as DataError.Rejected
            assertThat(refused.status).isEqualTo(409)
            assertThat(refused.userMessage).isEqualTo("У велосипеда есть покатушки.")
            expectNoEvents()
        }
    }

    @Test
    fun `a malformed bike id is not found without a request`() = runTest {
        val result = runCatching { bikes.delete(BikeId("nope")) }

        assertThat(result.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }
}
