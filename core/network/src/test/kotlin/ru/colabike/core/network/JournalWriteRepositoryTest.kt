package ru.colabike.core.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalChange
import ru.colabike.core.model.JournalDraft
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalPatch
import ru.colabike.core.model.JournalStatus

class JournalWriteRepositoryTest {
    private val site = TestServer()
    private val journal =
        NetworkJournalRepository(
            site.api.journal,
            site.api.personal,
            site.media,
            Dispatchers.Unconfined,
            site.api::journalWithNulls,
        )
    private val bike = BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
    private val entry = JournalId("c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f")
    private val key = "0c7d9a1e-5b2f-4c3d-8e4a-1f2b3c4d5e6f"
    private val part = "a1a1a1a1-b2b2-4c3c-8d4d-e5e5e5e5e5e5"

    @After fun close() = site.close()

    private val draft =
        JournalDraft(
            kind = "build",
            title = " Новая цепь ",
            body = "Поставил **KMC**.",
            status = JournalStatus.Published,
            isPublic = true,
            eventDate = LocalDate.parse("2026-09-14"),
            mileageKm = 4200,
            installationResult = "modified",
            componentIds = listOf(part),
        )

    private fun body(request: mockwebserver3.RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    @Test
    fun `the author's page carries the version an edit names, a reader's does not`() = runTest {
        site.json(200, site.fixture("journal-entry.json"), "ETag", "\"entry-v3\"")
        site.json(200, site.fixture("journal-entry.json"))

        val own = journal.entry(entry)
        val other = journal.entry(entry)

        assertThat(own.version).isEqualTo("\"entry-v3\"")
        assertThat(other.version).isNull()
    }

    @Test
    fun `a new entry is a POST with its key, the audience named and the text trimmed`() = runTest {
        site.json(201, site.fixture("journal-entry.json"), "ETag", "\"entry-v1\"")

        journal.changes.test {
            val created = journal.create(bike, draft, key)

            assertThat(created.version).isEqualTo("\"entry-v1\"")
            assertThat(awaitItem()).isEqualTo(JournalChange.Saved(created))
        }

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/journal")
        assertThat(request.headers["Idempotency-Key"]).isEqualTo(key)
        val sent = body(request)
        assertThat(sent["bikeId"]).isEqualTo(JsonPrimitive(bike.value))
        assertThat(sent["kind"]).isEqualTo(JsonPrimitive("build"))
        assertThat(sent["title"]).isEqualTo(JsonPrimitive("Новая цепь"))
        assertThat(sent["status"]).isEqualTo(JsonPrimitive("published"))
        assertThat(sent["isPublic"]).isEqualTo(JsonPrimitive(true))
        assertThat(sent["eventDate"]).isEqualTo(JsonPrimitive("2026-09-14"))
        assertThat(sent["mileage"]).isEqualTo(JsonPrimitive(4200))
        assertThat(sent["installationResult"]).isEqualTo(JsonPrimitive("modified"))
        assertThat(sent["componentIds"]).isEqualTo(JsonArray(listOf(JsonPrimitive(part))))
        // The app does not tie an entry to a ride.
        assertThat(sent.keys).doesNotContain("rideId")
    }

    @Test
    fun `a draft is never sent as public, and a result is sent only with a build`() = runTest {
        site.json(201, site.fixture("journal-entry.json"), "ETag", "\"entry-v1\"")

        journal.create(
            bike,
            draft.copy(status = JournalStatus.Draft, isPublic = true, kind = "service"),
            key,
        )

        val sent = body(site.server.takeRequest())
        assertThat(sent["status"]).isEqualTo(JsonPrimitive("draft"))
        assertThat(sent["isPublic"]).isEqualTo(JsonPrimitive(false))
        assertThat(sent.keys).doesNotContain("installationResult")
    }

    @Test
    fun `publishing for everyone without a confirmed address is refused with its own code`() =
        runTest {
            site.json(403, error("email_verification_required"))

            journal.changes.test {
                val result = runCatching { journal.create(bike, draft, key) }

                val refused = result.exceptionOrNull() as DataError.Rejected
                assertThat(refused.status).isEqualTo(403)
                assertThat(refused.code).isEqualTo("email_verification_required")
                expectNoEvents()
            }
        }

    @Test
    fun `an edit names the version it read and sends only what changed`() = runTest {
        site.json(200, site.fixture("journal-entry.json"), "ETag", "\"entry-v4\"")

        val saved = journal.update(entry, JournalPatch(title = " Другое "), "\"entry-v3\"")

        assertThat(saved.version).isEqualTo("\"entry-v4\"")
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/journal/${entry.value}")
        assertThat(request.headers["If-Match"]).isEqualTo("\"entry-v3\"")
        assertThat(body(request)).isEqualTo(JsonObject(mapOf("title" to JsonPrimitive("Другое"))))
    }

    @Test
    fun `a date, a mileage and a result taken away go as null, and the rest is kept`() = runTest {
        site.json(200, site.fixture("journal-entry.json"), "ETag", "\"entry-v4\"")

        journal.update(
            entry,
            JournalPatch(
                body = "Новый текст",
                clearEventDate = true,
                clearMileage = true,
                clearInstallation = true,
            ),
            "\"entry-v3\"",
        )

        val sent = body(site.server.takeRequest())
        assertThat(sent["eventDate"]).isEqualTo(JsonNull)
        assertThat(sent["mileage"]).isEqualTo(JsonNull)
        assertThat(sent["installationResult"]).isEqualTo(JsonNull)
        assertThat(sent["body"]).isEqualTo(JsonPrimitive("Новый текст"))
        assertThat(sent.keys).containsExactly("eventDate", "mileage", "installationResult", "body")
    }

    @Test
    fun `the components are replaced whole and the audience goes with its status`() = runTest {
        site.json(200, site.fixture("journal-entry.json"), "ETag", "\"entry-v4\"")

        journal.update(
            entry,
            JournalPatch(
                status = JournalStatus.Published,
                isPublic = true,
                componentIds = listOf(part),
            ),
            "\"entry-v3\"",
        )

        val sent = body(site.server.takeRequest())
        assertThat(sent["status"]).isEqualTo(JsonPrimitive("published"))
        assertThat(sent["isPublic"]).isEqualTo(JsonPrimitive(true))
        assertThat(sent["componentIds"]).isEqualTo(JsonArray(listOf(JsonPrimitive(part))))
    }

    @Test
    fun `an entry another device changed first is 412 and is announced to nobody`() = runTest {
        site.json(412, error("precondition_failed"))

        journal.changes.test {
            val result = runCatching { journal.update(entry, JournalPatch(title = "Д"), "\"v\"") }

            assertThat((result.exceptionOrNull() as DataError.Rejected).status).isEqualTo(412)
            expectNoEvents()
        }
    }

    @Test
    fun `an edit with no version is not sent`() = runTest {
        val result = runCatching { journal.update(entry, JournalPatch(title = "Д"), null) }

        assertThat((result.exceptionOrNull() as DataError.Rejected).status).isEqualTo(428)
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an edit that changes nothing only reads the entry`() = runTest {
        site.json(200, site.fixture("journal-entry.json"), "ETag", "\"entry-v3\"")

        val read = journal.update(entry, JournalPatch(), "\"entry-v3\"")

        assertThat(read.version).isEqualTo("\"entry-v3\"")
        assertThat(site.server.requestCount).isEqualTo(1)
        assertThat(site.server.takeRequest().method).isEqualTo("GET")
    }

    @Test
    fun `deleting is DELETE and the entry is announced as gone`() = runTest {
        site.json(204, "")

        journal.changes.test {
            journal.delete(entry)

            assertThat(awaitItem()).isEqualTo(JournalChange.Removed(entry))
        }
        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/journal/${entry.value}")
    }

    @Test
    fun `an entry already deleted elsewhere is deleted all the same and the lists are told`() =
        runTest {
            site.json(404, error("not_found"))

            journal.changes.test {
                journal.delete(entry)

                assertThat(awaitItem()).isEqualTo(JournalChange.Removed(entry))
            }
        }

    @Test
    fun `a malformed id is not found without a request`() = runTest {
        val result = runCatching { journal.delete(JournalId("nope")) }

        assertThat(result.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }
}
