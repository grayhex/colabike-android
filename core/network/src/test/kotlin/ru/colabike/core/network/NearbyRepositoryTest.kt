package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NearbyChange
import ru.colabike.core.model.NearbyOffersState
import ru.colabike.core.model.NearbyReason
import ru.colabike.core.model.NearbySource

/**
 * The private area of "rides near me" against the server's rules (cola#343): the version of what
 * was read is the `If-Match` of what is changed, only a cell centre is sent, a conflict is a
 * conflict and not an overwrite, and nothing of a place is printed.
 */
class NearbyRepositoryTest {
    private val site = TestServer()
    private val nearby =
        NetworkNearbyRepository(site.api.planning, site.media, Dispatchers.Unconfined)

    @After fun close() = site.close()

    private fun state(
        enabled: Boolean = true,
        source: String? = "device",
        expires: String? = "2026-10-06T09:00:00.000Z",
        expired: Boolean = false,
    ) =
        """
        {"available":true,"enabled":$enabled,"source":${source?.let { "\"$it\"" }},
         "area":${if (source == null) "null" else """{"label":null,"center":[37.625,55.755],"radiusM":10000}"""},
         "observedAt":${if (source == null) "null" else "\"2026-10-05T09:00:00.000Z\""},
         "expiresAt":${expires?.let { "\"$it\"" }},"expired":$expired,"horizonDays":14,
         "filters":{"purposes":["social"],"paces":[],"surfaces":[]},
         "limits":{"minRadiusM":5000,"maxRadiusM":50000,"radiusStepM":1000,"deviceTtlHours":24,
                   "cell":{"latStep":0.03,"lngStep":0.05}}}
        """
            .trimIndent()

    @Test
    fun `the state is read with the version it carries`() = runTest {
        site.json(200, state(), "ETag", "\"v1\"")

        val read = nearby.settings()

        assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/me/nearby")
        assertThat(read.available).isTrue()
        assertThat(read.enabled).isTrue()
        assertThat(read.source).isEqualTo(NearbySource.Device)
        assertThat(read.area?.radiusM).isEqualTo(10_000)
        assertThat(read.area?.longitude).isEqualTo(37.625)
        assertThat(read.area?.latitude).isEqualTo(55.755)
        assertThat(read.expiresAt).isNotNull()
        assertThat(read.horizonDays).isEqualTo(14)
        assertThat(read.preferences.purposes).containsExactly("social")
        assertThat(read.limits.grid.latStep).isEqualTo(0.03)
        assertThat(read.limits.maxRadiusM).isEqualTo(50_000)
        assertThat(read.version).isEqualTo("\"v1\"")
    }

    @Test
    fun `a place is not printed`() = runTest {
        site.json(200, state(), "ETag", "\"v1\"")

        val read = nearby.settings()

        assertThat(read.area.toString()).doesNotContain("37.6")
        assertThat(read.area.toString()).doesNotContain("55.7")
        assertThat(read.toString()).doesNotContain("37.625")
    }

    @Test
    fun `no area is no source and no place`() = runTest {
        site.json(200, state(enabled = false, source = null, expires = null), "ETag", "\"v0\"")

        val read = nearby.settings()

        assertThat(read.area).isNull()
        assertThat(read.source).isNull()
        assertThat(read.expiresAt).isNull()
        assertThat(read.enabled).isFalse()
    }

    @Test
    fun `the area of the phone is the cell centre, with the version that was read`() = runTest {
        site.json(200, state(), "ETag", "\"v2\"")

        val saved =
            nearby.confirmDeviceArea(
                longitude = 37.625,
                latitude = 55.755,
                radiusM = 10_000,
                replaceManual = false,
                version = "\"v1\"",
            )

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/me/nearby/area")
        assertThat(request.headers["If-Match"]).isEqualTo("\"v1\"")
        val sent = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(sent.keys).containsExactly("source", "center", "radiusM")
        assertThat(sent["source"]!!.jsonPrimitive.content).isEqualTo("device")
        assertThat(sent["center"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() })
            .containsExactly(37.625, 55.755)
            .inOrder()
        assertThat(saved.version).isEqualTo("\"v2\"")
    }

    @Test
    fun `replacing a hand-picked area is said out loud`() = runTest {
        site.json(200, state(), "ETag", "\"v2\"")

        nearby.confirmDeviceArea(37.625, 55.755, 10_000, replaceManual = true, version = "\"v1\"")

        val sent = Json.parseToJsonElement(site.server.takeRequest().body!!.utf8()).jsonObject
        assertThat(sent["replaceSource"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `another phone got there first, and the server's word is kept`() = runTest {
        site.json(412, error("precondition_failed"))
        site.json(409, error("conflict"))
        site.json(428, error("precondition_required"))

        val stale =
            runCatching {
                nearby.confirmDeviceArea(37.625, 55.755, 10_000, false, "\"old\"")
            }
                .exceptionOrNull() as DataError.Rejected
        val other =
            runCatching {
                nearby.confirmDeviceArea(37.625, 55.755, 10_000, false, "\"v1\"")
            }
                .exceptionOrNull() as DataError.Rejected
        val none =
            runCatching {
                nearby.confirmDeviceArea(37.625, 55.755, 10_000, false, null)
            }
                .exceptionOrNull() as DataError.Rejected

        assertThat(stale.status).isEqualTo(412)
        assertThat(other.status).isEqualTo(409)
        assertThat(none.status).isEqualTo(428)
    }

    @Test
    fun `a change names only what it changes`() = runTest {
        site.json(200, state(enabled = true), "ETag", "\"v3\"")

        nearby.change(NearbyChange(enabled = true, horizonDays = 7), version = "\"v2\"")

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.headers["If-Match"]).isEqualTo("\"v2\"")
        val sent = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(sent.keys).containsExactly("enabled", "horizonDays")
    }

    @Test
    fun `the kinds are sent sorted and only when named`() = runTest {
        site.json(200, state(), "ETag", "\"v3\"")

        nearby.change(NearbyChange(paces = setOf("sporty", "moderate")), version = null)

        val request = site.server.takeRequest()
        assertThat(request.headers["If-Match"]).isNull()
        val sent = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(sent.keys).containsExactly("filters")
        val filters = sent["filters"]!!.jsonObject
        assertThat(filters.keys).containsExactly("paces")
        assertThat(filters["paces"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("moderate", "sporty")
            .inOrder()
    }

    @Test
    fun `a change that changes nothing asks nothing new, it reads`() = runTest {
        site.json(200, state(), "ETag", "\"v1\"")

        val read = nearby.change(NearbyChange(), version = "\"v1\"")

        assertThat(site.server.takeRequest().method).isEqualTo("GET")
        assertThat(read.version).isEqualTo("\"v1\"")
    }

    @Test
    fun `removing the area and forgetting everything are deletes`() = runTest {
        site.json(200, state(source = null, expires = null), "ETag", "\"v4\"")
        site.json(204, "")

        val removed = nearby.removeArea("\"v3\"")
        nearby.forget()

        val first = site.server.takeRequest()
        assertThat(first.method).isEqualTo("DELETE")
        assertThat(first.url.encodedPath).isEqualTo("/api/v1/me/nearby/area")
        assertThat(first.headers["If-Match"]).isEqualTo("\"v3\"")
        assertThat(removed.area).isNull()
        val second = site.server.takeRequest()
        assertThat(second.method).isEqualTo("DELETE")
        assertThat(second.url.encodedPath).isEqualTo("/api/v1/me/nearby")
    }

    @Test
    fun `the offers are the public cards of the rides, with why they are offered`() = runTest {
        val ride =
            Json.parseToJsonElement(site.fixture("ride-upcoming-page.json"))
                .jsonObject["items"]!!
                .jsonArray[0]
                .toString()
        site.json(
            200,
            """{"state":"ready","items":[{"ride":$ride,"reasons":["nearby","intent"]}]}""",
        )

        val offers = nearby.offers(limit = 5)

        val request = site.server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/me/nearby/offers")
        assertThat(request.url.queryParameter("limit")).isEqualTo("5")
        assertThat(offers.state).isEqualTo(NearbyOffersState.Ready)
        assertThat(offers.items).hasSize(1)
        assertThat(offers.items[0].ride.title).isEqualTo("Воскресный выезд")
        assertThat(offers.items[0].reasons)
            .containsExactly(NearbyReason.Nearby, NearbyReason.Intent)
    }

    @Test
    fun `an empty list says why, and a state the app does not know is not a promise`() = runTest {
        site.json(200, """{"state":"expired","items":[]}""")
        site.json(200, """{"state":"somewhere_else","items":[]}""")

        assertThat(nearby.offers().state).isEqualTo(NearbyOffersState.Expired)
        assertThat(nearby.offers().state).isEqualTo(NearbyOffersState.Unavailable)
    }

    @Test
    fun `a signed-out person is told so`() = runTest {
        site.json(401, error("unauthorized"))

        val failure = runCatching { nearby.settings() }.exceptionOrNull()

        assertThat(failure).isInstanceOf(DataError::class.java)
    }
}
