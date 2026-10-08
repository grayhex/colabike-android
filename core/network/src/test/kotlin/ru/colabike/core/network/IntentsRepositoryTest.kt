package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat as assertThatValue
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.colabike.core.model.DataError
import ru.colabike.core.model.IntentDraft
import ru.colabike.core.model.IntentFold
import ru.colabike.core.model.IntentReadiness
import ru.colabike.core.model.IntentStatus
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.IntentWindowDraft
import ru.colabike.core.model.Range
import ru.colabike.core.model.RideAreaPoint
import ru.colabike.core.model.RidePassport
import ru.colabike.core.model.toDraft

/**
 * Intentions to ride against the server's rules (cola#343): a create is one intention whatever the
 * retries (the `Idempotency-Key`), a change names the version that was read, windows go as local
 * times in the intention's zone, and what the form has no field for is sent back as it was.
 */
class IntentsRepositoryTest {
    private val site = TestServer()
    private val intents =
        NetworkIntentsRepository(
            site.api.planning,
            site.api::planningWithKey,
            site.media,
            Dispatchers.Unconfined,
        )

    @After fun close() = site.close()

    private val id = "b3000000-0000-4000-8000-000000000003"
    private val key = "11111111-1111-4111-8111-111111111111"

    private fun intent(
        own: Boolean = true,
        status: String = "active",
        visibility: String = "private",
        zone: String = "Europe/Moscow",
    ) =
        """
        {"id":"$id","own":$own,"readiness":"ready","timeZone":"$zone",
         "passport":{"area":{"label":"Парк Горького","center":[37.6,55.73],"radiusM":5000},
                     "purpose":"leisure","pace":"relaxed","distanceKm":{"min":20,"max":40},
                     "groupSize":{"min":2,"max":8},"beginnerFriendly":true},
         "windows":[{"startsAt":"2026-10-10T07:00:00.000Z","endsAt":"2026-10-10T10:00:00.000Z"}],
         "meetNewPeople":true,"visibility":"$visibility","status":"$status",
         "allowSuggestions":${if (own) "true" else "null"},
         "author":{"id":"10000000-0000-4000-8000-000000000001","username":"rider2",
                   "name":"Вторая Райдерша","avatarUrl":null},
         "createdAt":"2026-10-05T08:00:00.000Z","updatedAt":"2026-10-05T08:30:00.000Z"}
        """
            .trimIndent()

    private val draft =
        IntentDraft(
            readiness = IntentReadiness.Ready,
            timeZone = ZoneId.of("Europe/Moscow"),
            windows =
                listOf(
                    IntentWindowDraft(
                        LocalDateTime.of(2026, 10, 10, 10, 0),
                        LocalDateTime.of(2026, 10, 10, 13, 0),
                    )
                ),
            passport =
                RidePassport(
                    areaLabel = "Парк Горького",
                    area = RideAreaPoint(37.6, 55.73, 5000),
                    purpose = "leisure",
                    pace = "relaxed",
                    distanceKm = Range(20.0, 40.0),
                    groupSize = Range(2.0, 8.0),
                    beginnerFriendly = true,
                ),
            meetNewPeople = true,
            visibility = IntentVisibility.Private,
            allowSuggestions = true,
        )

    @Test
    fun `area survives create read replace and removal over the v1 HTTP endpoints`() = runTest {
        val original = intent()
        val changed =
            original
                .replace("Парк Горького", "Другой парк")
                .replace("[37.6,55.73]", "[30.31,59.94]")
                .replace("5000", "12000")
        val textOnly = changed.replace(",\"center\":[30.31,59.94],\"radiusM\":12000", "")
        site.json(201, original, "ETag", "\"i1\"")
        site.json(200, original, "ETag", "\"i1\"")
        site.json(200, changed, "ETag", "\"i2\"")
        site.json(200, textOnly, "ETag", "\"i3\"")

        val created = intents.create(draft, key)
        val read = intents.get(created.id)
        assertThat(read.passport.area).isEqualTo(draft.passport.area)
        val replacement =
            read
                .toDraft()
                .copy(
                    passport =
                        read.passport.copy(
                            areaLabel = "Другой парк",
                            area = RideAreaPoint(30.31, 59.94, 12000),
                        )
                )
        val edited = intents.replace(read.id, replacement, read.version)
        assertThat(edited.passport.area).isEqualTo(replacement.passport.area)
        assertThat(edited.passport.areaLabel).isEqualTo("Другой парк")
        val removed =
            intents.replace(
                edited.id,
                edited.toDraft().copy(passport = edited.passport.copy(area = null)),
                edited.version,
            )
        assertThat(removed.passport.area).isNull()
        assertThat(removed.passport.areaLabel).isEqualTo("Другой парк")

        val createRequest = site.server.takeRequest()
        assertThat(createRequest.method).isEqualTo("POST")
        assertThat(createRequest.url.encodedPath).isEqualTo("/api/v1/ride-intents")
        val readRequest = site.server.takeRequest()
        assertThat(readRequest.method).isEqualTo("GET")
        assertThat(readRequest.url.encodedPath).isEqualTo("/api/v1/ride-intents/$id")
        val editRequest = site.server.takeRequest()
        assertThat(editRequest.headers["If-Match"]).isEqualTo("\"i1\"")
        val body = Json.parseToJsonElement(editRequest.body!!.utf8()).jsonObject
        val area = body.getValue("passport").jsonObject.getValue("area").jsonObject
        assertThat(area.getValue("center").jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("30.31", "59.94")
            .inOrder()
        assertThat(area.getValue("radiusM").jsonPrimitive.content).isEqualTo("12000")
        assertThat(body.getValue("visibility").jsonPrimitive.content).isEqualTo("private")
        val removal =
            Json.parseToJsonElement(site.server.takeRequest().body!!.utf8())
                .jsonObject
                .getValue("passport")
                .jsonObject
                .getValue("area")
                .jsonObject
        assertThat(removal.keys).containsExactly("label")
    }

    @Test
    fun `an intention is read with its version, its windows and its zone`() = runTest {
        site.json(200, intent(), "ETag", "\"i1\"")

        val read = intents.get(id)

        assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/ride-intents/$id")
        assertThat(read.id).isEqualTo(id)
        assertThat(read.own).isTrue()
        assertThat(read.readiness).isEqualTo(IntentReadiness.Ready)
        assertThat(read.timeZone).isEqualTo(ZoneId.of("Europe/Moscow"))
        assertThat(read.visibility).isEqualTo(IntentVisibility.Private)
        assertThat(read.status).isEqualTo(IntentStatus.Active)
        assertThat(read.windows.single().startsAt).isEqualTo(Instant.parse("2026-10-10T07:00:00Z"))
        assertThat(read.passport.areaLabel).isEqualTo("Парк Горького")
        assertThat(read.passport.distanceKm).isEqualTo(Range(20.0, 40.0))
        assertThat(read.author.username).isEqualTo("rider2")
        assertThat(read.allowSuggestions).isTrue()
        assertThat(read.version).isEqualTo("\"i1\"")
        assertThat(read.editable).isTrue()
    }

    @Test
    fun `someone else's intention shows no private choice, and a cancelled one is not editable`() =
        runTest {
            site.json(200, intent(own = false, status = "cancelled", visibility = "community"))

            val read = intents.get(id)

            assertThat(read.own).isFalse()
            assertThat(read.allowSuggestions).isNull()
            assertThat(read.status).isEqualTo(IntentStatus.Cancelled)
            assertThat(read.editable).isFalse()
        }

    @Test
    fun `an unknown state and an unknown zone are kept as safe words, not dropped`() = runTest {
        site.json(200, intent(status = "paused_for_the_winter", zone = "Mars/Olympus"))

        val read = intents.get(id)

        assertThat(read.status).isEqualTo(IntentStatus.Unknown)
        assertThat(read.timeZone).isEqualTo(ZoneId.of("UTC"))
    }

    @Test
    fun `a create carries its key, the local times and the whole passport`() = runTest {
        site.json(201, intent(), "ETag", "\"i1\"")

        val made = intents.create(draft, key)

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/ride-intents")
        assertThat(request.headers["Idempotency-Key"]).isEqualTo(key)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(body.getValue("timeZone").jsonPrimitive.content).isEqualTo("Europe/Moscow")
        val window = body.getValue("windows").jsonArray.single().jsonObject
        assertThat(window.getValue("startLocal").jsonPrimitive.content)
            .isEqualTo("2026-10-10T10:00")
        assertThat(window.getValue("endLocal").jsonPrimitive.content).isEqualTo("2026-10-10T13:00")
        // No fold unless the hour repeats.
        assertThat(window.containsKey("startFold")).isFalse()
        val passport = body.getValue("passport").jsonObject
        val area = passport.getValue("area").jsonObject
        assertThat(area.getValue("label").jsonPrimitive.content).isEqualTo("Парк Горького")
        assertThat(area.getValue("radiusM").jsonPrimitive.content).isEqualTo("5000")
        assertThat(passport.getValue("distanceKm").jsonObject.getValue("max").jsonPrimitive.content)
            .isEqualTo("40.0")
        assertThat(passport.getValue("beginnerFriendly").jsonPrimitive.content).isEqualTo("true")
        assertThat(body.getValue("visibility").jsonPrimitive.content).isEqualTo("private")
        assertThat(body.getValue("allowSuggestions").jsonPrimitive.content).isEqualTo("true")
        assertThat(made.version).isEqualTo("\"i1\"")
    }

    @Test
    fun `a repeat of the same create is the same intention, not a second one`() = runTest {
        site.json(201, intent(), "ETag", "\"i1\"")
        site.json(200, intent(), "ETag", "\"i1\"", "Idempotency-Replayed", "true")

        val first = intents.create(draft, key)
        val again = intents.create(draft, key)

        assertThat(again.id).isEqualTo(first.id)
        assertThat(site.server.takeRequest().headers["Idempotency-Key"]).isEqualTo(key)
        assertThat(site.server.takeRequest().headers["Idempotency-Key"]).isEqualTo(key)
    }

    @Test
    fun `a key that is no UUID is a mistake of ours, not a request`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { intents.create(draft, "not-a-uuid") }
        }
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `the key is sent only with a create`() = runTest {
        site.json(200, intent(), "ETag", "\"i1\"")

        intents.get(id)

        assertThat(site.server.takeRequest().headers["Idempotency-Key"]).isNull()
    }

    @Test
    fun `a replace names the version that was read`() = runTest {
        site.json(200, intent(), "ETag", "\"i2\"")

        val changed = intents.replace(id, draft, "\"i1\"")

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/ride-intents/$id")
        assertThat(request.headers["If-Match"]).isEqualTo("\"i1\"")
        assertThat(changed.version).isEqualTo("\"i2\"")
    }

    @Test
    fun `a change another device got in first is a conflict, a cancelled one cannot be changed`() =
        runTest {
            site.json(412, error("precondition_failed"))
            site.json(409, error("conflict"))

            val stale =
                assertThrows(DataError.Rejected::class.java) {
                    kotlinx.coroutines.runBlocking { intents.replace(id, draft, "\"old\"") }
                }
            val cancelled =
                assertThrows(DataError.Rejected::class.java) {
                    kotlinx.coroutines.runBlocking { intents.replace(id, draft, null) }
                }

            assertThat(stale.status).isEqualTo(412)
            assertThat(cancelled.status).isEqualTo(409)
        }

    @Test
    fun `a cancel has no body and returns the cancelled intention`() = runTest {
        site.json(200, intent(status = "cancelled"), "ETag", "\"i3\"")

        val closed = intents.cancel(id)

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/ride-intents/$id/cancel")
        assertThat(closed.status).isEqualTo(IntentStatus.Cancelled)
    }

    @Test
    fun `a delete is a delete, and a repeat is fine`() = runTest {
        site.server.enqueue(mockwebserver3.MockResponse.Builder().code(204).build())

        intents.delete(id)

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/ride-intents/$id")
    }

    @Test
    fun `the lists page by cursor and own intentions come with theirs`() = runTest {
        site.json(200, """{"items":[${intent()}],"nextCursor":"c2"}""")
        site.json(
            200,
            """{"items":[${intent(own = false, visibility = "community")}],"nextCursor":null}""",
        )

        val mine = intents.own(cursor = "c1", limit = 10)
        val others = intents.community(cursor = null, limit = 24)

        val own = site.server.takeRequest().url
        assertThat(own.encodedPath).isEqualTo("/api/v1/me/ride-intents")
        assertThat(own.queryParameter("cursor")).isEqualTo("c1")
        assertThat(own.queryParameter("limit")).isEqualTo("10")
        assertThat(mine.nextCursor).isEqualTo("c2")
        assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/ride-intents")
        assertThat(others.items.single().own).isFalse()
        assertThat(others.hasMore).isFalse()
    }

    @Test
    fun `an id that is no UUID is not found without asking`() = runTest {
        assertThrows(DataError.NotFound::class.java) {
            kotlinx.coroutines.runBlocking { intents.get("../x") }
        }
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    // --- the hour the clocks go back -----------------------------------------------------------

    @Test
    fun `a repeated hour carries a fold, and the draft of a read intention says which one it was`() =
        runTest {
            val zone = ZoneId.of("Europe/Berlin")
            // 2026-10-25 03:00 summer time becomes 02:00 winter time: 02:30 happens twice.
            val earlier = Instant.parse("2026-10-25T00:30:00Z") // 02:30 CEST
            val later = Instant.parse("2026-10-25T01:30:00Z") // 02:30 CET
            val window = ru.colabike.core.model.IntentWindow(earlier, later).toDraft(zone)

            assertThat(window.start).isEqualTo(LocalDateTime.of(2026, 10, 25, 2, 30))
            assertThat(window.startFold).isEqualTo(IntentFold.Earlier)
            assertThat(window.endFold).isEqualTo(IntentFold.Later)
            // An ordinary time has no fold.
            val ordinary =
                ru.colabike.core.model
                    .IntentWindow(
                        Instant.parse("2026-10-10T07:00:00Z"),
                        Instant.parse("2026-10-10T09:00:00Z"),
                    )
                    .toDraft(zone)
            assertThat(ordinary.startFold).isNull()
            assertThatValue(ordinary.endFold).isNull()
        }

    @Test
    fun `the fold goes to the server in its own words`() = runTest {
        site.json(201, intent())
        val folded =
            draft.copy(
                windows =
                    listOf(
                        IntentWindowDraft(
                            LocalDateTime.of(2026, 10, 25, 2, 30),
                            LocalDateTime.of(2026, 10, 25, 2, 45),
                            IntentFold.Earlier,
                            IntentFold.Later,
                        )
                    )
            )

        intents.create(folded, key)

        val window =
            Json.parseToJsonElement(site.server.takeRequest().body!!.utf8())
                .jsonObject
                .getValue("windows")
                .jsonArray
                .single()
                .jsonObject
        assertThat(window.getValue("startFold").jsonPrimitive.content).isEqualTo("earlier")
        assertThat(window.getValue("endFold").jsonPrimitive.content).isEqualTo("later")
    }
}
