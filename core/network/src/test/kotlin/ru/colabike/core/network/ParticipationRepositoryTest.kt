package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.colabike.core.model.AgreementChange
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ParticipationOutcome
import ru.colabike.core.model.ParticipationResponse
import ru.colabike.core.model.ParticipationState
import ru.colabike.core.model.RequestedDateStatus
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.ViewerRole

/**
 * A person's part in one date of a plan (cola#343): the date and the edition of terms that were
 * seen go with every "going" and "maybe", a refusal carries the plan as it is now, and what the
 * server hides (the meeting point of one who is not going) stays hidden.
 */
class ParticipationRepositoryTest {
    private val site = TestServer()
    private val participation =
        NetworkParticipationRepository(site.api.planning, site.media, Dispatchers.Unconfined)

    @After fun close() = site.close()

    private val ride = "b2000000-0000-4000-8000-0000000000b2"
    private val date = Instant.parse("2026-10-10T07:00:00Z")

    private fun state(
        participation: String = "invited",
        response: String? = null,
        previous: String? = null,
        changed: Boolean = false,
        allowed: String = """["accepted","maybe","declined"]""",
        revision: Int = 3,
        changes: String = """["start"]""",
        status: String = "planned",
        requested: String = """{"at":"2026-10-10T07:00:00.000Z","status":"current"}""",
        hidden: Boolean = false,
        closed: Boolean = false,
        scheduledAt: String = "\"2026-10-10T07:00:00.000Z\"",
        zone: String = "Europe/Moscow",
    ) =
        """
        {"rideId":"$ride","title":"Воскресный выезд","status":"$status","description":"Спокойно",
         "features":["кофе"],"author":{"id":"10000000-0000-4000-8000-000000000001",
         "username":"rider2","name":"Вторая Райдерша","avatarUrl":null},
         "timeZone":"$zone","recurrence":"weekly","scheduledAt":$scheduledAt,
         "expectedEndAt":"2026-10-10T10:00:00.000Z","requested":$requested,
         "agreement":{"revision":$revision,"changes":$changes,"changedAt":"2026-10-05T08:00:00.000Z"},
         "recruitmentClosed":$closed,"meetingPoint":${if (hidden) "null" else "\"У входа в парк\""},
         "meetingHidden":$hidden,"passport":{"area":{"label":"Парк"},"purpose":"leisure"},
         "participants":{"going":4,"maybe":2},
         "viewer":{"role":"invitee","participation":"$participation",
                   "response":${response?.let { "\"$it\"" }},
                   "previousResponse":${previous?.let { "\"$it\"" }},
                   "changedAfterAnswer":$changed,"allowedResponses":$allowed}}
        """
            .trimIndent()

    @Test
    fun `the state of a date is read for the date a notification named`() = runTest {
        site.json(200, state())

        val read = participation.get(ride, date)

        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/rides/$ride/participation")
        assertThat(Instant.parse(request.queryParameter("occurrenceAt"))).isEqualTo(date)
        assertThat(read.title).isEqualTo("Воскресный выезд")
        assertThat(read.status).isEqualTo(RideStatus.Planned)
        assertThat(read.timeZone).isEqualTo(ZoneId.of("Europe/Moscow"))
        assertThat(read.scheduledAt).isEqualTo(date)
        assertThat(read.requested?.status).isEqualTo(RequestedDateStatus.Current)
        assertThat(read.agreement.revision).isEqualTo(3)
        assertThat(read.agreement.changes).containsExactly(AgreementChange.Start)
        assertThat(read.meetingPoint).isEqualTo("У входа в парк")
        assertThat(read.going).isEqualTo(4)
        assertThat(read.role).isEqualTo(ViewerRole.Invitee)
        assertThat(read.state).isEqualTo(ParticipationState.Invited)
        assertThat(read.allowed)
            .containsExactly(
                ParticipationResponse.Accepted,
                ParticipationResponse.Maybe,
                ParticipationResponse.Declined,
            )
    }

    @Test
    fun `no date asked about is no date in the query`() = runTest {
        site.json(200, state(requested = "null"))

        val read = participation.get(ride, null)

        assertThat(site.server.takeRequest().url.queryParameter("occurrenceAt")).isNull()
        assertThat(read.requested).isNull()
    }

    @Test
    fun `terms that changed after an answer are a state of their own, with the earlier answer`() =
        runTest {
            site.json(
                200,
                state(
                    participation = "reconfirm",
                    response = null,
                    previous = "accepted",
                    changed = true,
                    changes = """["start","place","something_new"]""",
                    revision = 4,
                ),
            )

            val read = participation.get(ride, date)

            assertThat(read.state).isEqualTo(ParticipationState.Reconfirm)
            assertThat(read.response).isNull()
            assertThat(read.previousResponse).isEqualTo(ParticipationResponse.Accepted)
            assertThat(read.changedAfterAnswer).isTrue()
            // A change from the future is dropped, the known ones stay.
            assertThat(read.agreement.changes)
                .containsExactly(AgreementChange.Start, AgreementChange.Place)
        }

    @Test
    fun `a hidden meeting point stays hidden, a cancelled date is told from a cancelled plan`() =
        runTest {
            site.json(
                200,
                state(
                    hidden = true,
                    allowed = "[]",
                    requested = """{"at":"2026-10-10T07:00:00.000Z","status":"cancelled"}""",
                ),
            )
            site.json(200, state(status = "cancelled", scheduledAt = "null", allowed = "[]"))

            val dateOnly = participation.get(ride, date)
            val whole = participation.get(ride, date)

            assertThat(dateOnly.meetingPoint).isNull()
            assertThat(dateOnly.meetingHidden).isTrue()
            assertThat(dateOnly.dateCancelled).isTrue()
            assertThat(dateOnly.planCancelled).isFalse()
            assertThat(dateOnly.allowed).isEmpty()
            assertThat(whole.planCancelled).isTrue()
            assertThat(whole.scheduledAt).isNull()
        }

    @Test
    fun `an unknown response or state of the future is not offered or guessed`() = runTest {
        site.json(
            200,
            state(participation = "left_for_mars", allowed = """["accepted","teleport"]"""),
        )

        val read = participation.get(ride, date)

        assertThat(read.state).isEqualTo(ParticipationState.Unknown)
        assertThat(read.allowed).containsExactly(ParticipationResponse.Accepted)
    }

    // --- answering -------------------------------------------------------------------------------

    @Test
    fun `going names the date and the edition that were seen`() = runTest {
        site.json(200, state(participation = "accepted", response = "accepted"))

        val outcome =
            participation.respond(ride, ParticipationResponse.Accepted, date, expectedRevision = 3)

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/rides/$ride/participation")
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(body.getValue("response").jsonPrimitive.content).isEqualTo("accepted")
        assertThat(Instant.parse(body.getValue("occurrenceAt").jsonPrimitive.content))
            .isEqualTo(date)
        assertThat(body.getValue("expectedAgreementRevision").jsonPrimitive.content).isEqualTo("3")
        val saved = (outcome as ParticipationOutcome.Saved).participation
        assertThat(saved.state).isEqualTo(ParticipationState.Accepted)
        assertThat(saved.response).isEqualTo(ParticipationResponse.Accepted)
    }

    @Test
    fun `not going needs no edition`() = runTest {
        site.json(200, state(participation = "declined", response = "declined"))

        participation.respond(ride, ParticipationResponse.Declined, date, expectedRevision = 3)

        val body = Json.parseToJsonElement(site.server.takeRequest().body!!.utf8()).jsonObject
        assertThat(body.containsKey("expectedAgreementRevision")).isFalse()
        assertThat(body.getValue("response").jsonPrimitive.content).isEqualTo("declined")
    }

    @Test
    fun `terms that changed meanwhile are not confirmed silently, the plan as it is now comes back`() =
        runTest {
            site.json(
                409,
                """{"error":{"code":"conflict","message":"Условия изменились"},
                    "current":${state(participation = "reconfirm", previous = "accepted", revision = 5, changed = true)}}""",
            )

            val outcome =
                participation.respond(
                    ride,
                    ParticipationResponse.Accepted,
                    date,
                    expectedRevision = 3,
                )

            val changed = outcome as ParticipationOutcome.Changed
            assertThat(changed.current?.agreement?.revision).isEqualTo(5)
            assertThat(changed.current?.state).isEqualTo(ParticipationState.Reconfirm)
        }

    @Test
    fun `a plan the person can no longer see comes back as changed without a state`() = runTest {
        site.json(409, """{"error":{"code":"conflict","message":"Нет доступа"},"current":null}""")

        val outcome =
            participation.respond(ride, ParticipationResponse.Maybe, date, expectedRevision = 3)

        assertThat((outcome as ParticipationOutcome.Changed).current).isNull()
    }

    @Test
    fun `other refusals are errors, and an answer taken tells the lists`() = runTest {
        site.json(429, error("rate_limited"), "Retry-After", "60")
        site.json(200, state(participation = "maybe", response = "maybe"))

        assertThrows(DataError.RateLimited::class.java) {
            kotlinx.coroutines.runBlocking {
                participation.respond(ride, ParticipationResponse.Maybe, date, 3)
            }
        }
        val seen = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            participation.changes.collect { seen += it }
        }
        participation.respond(ride, ParticipationResponse.Maybe, date, 3)

        assertThat(seen).containsExactly(ride)
    }

    @Test
    fun `an id that is no UUID is not found without asking`() = runTest {
        assertThrows(DataError.NotFound::class.java) {
            kotlinx.coroutines.runBlocking { participation.get("../x", null) }
        }
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a plan that is not the person's to see is not found`() = runTest {
        site.json(404, error("not_found"))

        assertThrows(DataError.NotFound::class.java) {
            kotlinx.coroutines.runBlocking { participation.get(ride, date) }
        }
    }
}
