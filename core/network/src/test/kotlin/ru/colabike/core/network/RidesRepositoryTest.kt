package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.AnalysisChannel
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideRecurrence
import ru.colabike.core.model.RideRole
import ru.colabike.core.model.RideStatus

class RidesRepositoryTest {
    private val site = TestServer()
    private val rides =
        NetworkRidesRepository(
            site.api.rides,
            site.api.personal,
            site.media,
            Dispatchers.Unconfined,
        )
    private val bike = "6e7f8091-a2b3-4c4d-9e5f-60718293a4b5"
    private val ride = "a0000004-0000-4000-8000-000000000004"

    @After fun close() = site.close()

    @Test
    fun `completed rides page by cursor, carry no geometry and read the shape of a card`() =
        runTest {
            site.json(200, site.fixture("ride-page.json"))

            val page = rides.completed(cursor = "c0", limit = 10)

            val request = site.server.takeRequest().url
            assertThat(request.encodedPath).isEqualTo("/api/v1/rides")
            assertThat(request.queryParameter("cursor")).isEqualTo("c0")
            assertThat(request.queryParameter("limit")).isEqualTo("10")
            assertThat(request.queryParameter("q")).isNull()
            val (first, untimed, odd) = page.items
            assertThat(first.status).isEqualTo(RideStatus.Completed)
            assertThat(first.time).isEqualTo(Instant.parse("2026-09-20T16:00:00Z"))
            assertThat(first.metrics.distanceM).isEqualTo(32_450)
            assertThat(first.metrics.elevationGainM).isEqualTo(120.0)
            assertThat(first.bike?.name).isEqualTo("Городской Трэвел")
            assertThat(first.comments).isEqualTo(2)
            assertThat(untimed.time).isNull()
            assertThat(odd.status).isEqualTo(RideStatus.Unknown)
            assertThat(page.nextCursor).isEqualTo("eyJyIjoiMiJ9")
        }

    @Test
    fun `upcoming plans come from their own list, a weekly series is one card on its next date`() =
        runTest {
            site.json(200, site.fixture("ride-upcoming-page.json"))

            val page = rides.upcoming(query = "  выезд ")

            val request = site.server.takeRequest().url
            assertThat(request.encodedPath).isEqualTo("/api/v1/rides/upcoming")
            assertThat(request.queryParameter("q")).isEqualTo("выезд")
            val plan = page.items.single()
            assertThat(plan.status).isEqualTo(RideStatus.Planned)
            assertThat(plan.recurrence).isEqualTo(RideRecurrence.Weekly)
            assertThat(plan.time).isEqualTo(Instant.parse("2026-10-11T07:00:00Z"))
            assertThat(plan.metrics.distanceM).isNull()
            assertThat(plan.participants?.going).isEqualTo(4)
            assertThat(plan.participants?.maybe).isEqualTo(2)
        }

    @Test
    fun `a bike's rides are its own list and a blank or long search is trimmed`() = runTest {
        site.json(200, site.fixture("ride-page.json"))
        site.json(200, site.fixture("ride-page.json"))

        rides.ofBike(BikeId(bike), query = "   ")
        rides.ofBike(BikeId(bike), query = "я".repeat(200))

        val blank = site.server.takeRequest().url
        assertThat(blank.encodedPath).isEqualTo("/api/v1/bikes/$bike/rides")
        assertThat(blank.queryParameter("q")).isNull()
        assertThat(site.server.takeRequest().url.queryParameter("q")).hasLength(150)
    }

    @Test
    fun `a plan page has the passport, the participants and a hidden meeting without its text`() =
        runTest {
            site.json(200, site.fixture("ride-planned.json"))

            val detail = rides.ride(RideId(ride))

            assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/rides/$ride")
            assertThat(detail.summary.title).isEqualTo("Воскресный выезд")
            assertThat(detail.meetingPoint).isNull()
            assertThat(detail.meetingHidden).isTrue()
            assertThat(detail.recruitmentClosed).isTrue()
            assertThat(detail.expectedEndAt).isEqualTo(Instant.parse("2026-10-11T11:00:00Z"))
            assertThat(detail.features).containsExactly("Кофе-пауза", "Без гонки").inOrder()
            val passport = detail.passport!!
            assertThat(passport.areaLabel).isEqualTo("Измайловский парк")
            assertThat(passport.pace).isEqualTo("calm")
            assertThat(passport.distanceKm?.max).isEqualTo(40.0)
            assertThat(passport.beginnerFriendly).isTrue()
            assertThat(detail.hasPublicRoute).isFalse()
            assertThat(detail.extraMetrics).isEmpty()
        }

    @Test
    fun `a recorded ride page says it has a route and keeps the numbers the author opened`() =
        runTest {
            site.json(200, site.fixture("ride-recorded.json"))

            val detail = rides.ride(RideId(ride))

            assertThat(detail.hasPublicRoute).isTrue()
            assertThat(detail.extraMetrics)
                .containsExactly("avgHeartRate", 142.0, "calories", 820.0)
            assertThat(detail.passport).isNull()
            assertThat(detail.meetingHidden).isFalse()
            assertThat(detail.meetingPoint).isNull()
        }

    @Test
    fun `a route keeps the server's lines apart and reads longitude first`() = runTest {
        site.json(200, site.fixture("ride-recorded.json"))

        val route = rides.ride(RideId(ride)).route!!

        // Two lines: what lies between them is a privacy cut and is never drawn as a segment.
        assertThat(route.lines).hasSize(2)
        assertThat(route.lines[0].first()).isEqualTo(GeoPoint(latitude = 55.75, longitude = 37.61))
        assertThat(route.lines[1].last()).isEqualTo(GeoPoint(latitude = 55.78, longitude = 37.65))
        assertThat(route.pointCount).isEqualTo(4)
    }

    @Test
    fun `a point off the Earth ends its line, a lone point and an empty line are no route`() =
        runTest {
            site.json(200, site.fixture("ride-dirty-route.json"))

            val route = rides.ride(RideId(ride)).route!!

            // Longitude 200 ends the first line; [37.63, 37.64] starts a new one; latitude 95 is
            // dropped, so the third line is only its two good points; the lone point is dropped.
            assertThat(route.lines.map { it.size }).containsExactly(2, 2, 2).inOrder()
            assertThat(route.lines[0].map { it.longitude }).containsExactly(37.61, 37.62).inOrder()
            assertThat(route.lines[1].map { it.longitude }).containsExactly(37.63, 37.64).inOrder()
            assertThat(route.lines[2].map { it.longitude }).containsExactly(37.72, 37.73).inOrder()
            assertThat(route.lines.flatten().none { it.latitude > 90.0 }).isTrue()
        }

    @Test
    fun `a ride without a public track has no route`() = runTest {
        site.json(200, site.fixture("ride-planned.json"))

        assertThat(rides.ride(RideId(ride)).route).isNull()
    }

    @Test
    fun `the analysis comes from its own request and keeps breaks, gaps and missing sensors`() =
        runTest {
            site.json(200, site.fixture("ride-analysis.json"))

            val analysis = rides.analysis(RideId(ride))!!

            assertThat(site.server.takeRequest().url.encodedPath)
                .isEqualTo("/api/v1/rides/$ride/analysis")
            assertThat(analysis.downsampled).isTrue()
            assertThat(analysis.pointCount).isEqualTo(6)
            // The empty third segment is dropped; the two real ones stay separate.
            assertThat(analysis.segments.map { it.size }).containsExactly(3, 3).inOrder()
            val (start, middle, end) = analysis.segments[0]
            assertThat(start.values[AnalysisChannel.Elevation]).isEqualTo(120.5)
            assertThat(start.distanceM).isEqualTo(0.0)
            // The author gave no heart rate at the middle point, and the server says so by a gap.
            assertThat(middle.values).doesNotContainKey(AnalysisChannel.HeartRate)
            assertThat(middle.gapBefore(AnalysisChannel.HeartRate)).isTrue()
            assertThat(middle.gapBefore(AnalysisChannel.Speed)).isFalse()
            assertThat(end.gapBefore(AnalysisChannel.Elevation)).isTrue()
            assertThat(end.gapBefore(AnalysisChannel.Grade)).isTrue()
            // A point whose position is not on the Earth keeps its numbers but has no position.
            assertThat(analysis.segments[1].last().position).isNull()
            assertThat(analysis.segments[1].last().values[AnalysisChannel.Elevation])
                .isEqualTo(128.0)
            // Heart rate has two values; cadence and power have none, so they get no chart.
            assertThat(analysis.channels)
                .containsExactly(
                    AnalysisChannel.Elevation,
                    AnalysisChannel.Speed,
                    AnalysisChannel.Grade,
                    AnalysisChannel.HeartRate,
                )
                .inOrder()
        }

    @Test
    fun `no analysis is not an error, any other answer is`() = runTest {
        site.json(404, error("not_found"))
        assertThat(rides.analysis(RideId(ride))).isNull()

        site.json(500, error("internal_error"))
        assertThat(runCatching { rides.analysis(RideId(ride)) }.exceptionOrNull())
            .isInstanceOf(DataError.Server::class.java)
    }

    @Test
    fun `a private, cancelled or missing ride is not found, and an id that is no UUID is not asked`() =
        runTest {
            site.json(404, error("not_found"))
            assertThat(runCatching { rides.ride(RideId(ride)) }.exceptionOrNull())
                .isInstanceOf(DataError.NotFound::class.java)

            listOf("", "../me", "x").forEach {
                assertThat(runCatching { rides.ride(RideId(it)) }.exceptionOrNull())
                    .isInstanceOf(DataError.NotFound::class.java)
                assertThat(runCatching { rides.ofBike(BikeId(it)) }.exceptionOrNull())
                    .isInstanceOf(DataError.NotFound::class.java)
            }
            assertThat(site.server.requestCount).isEqualTo(1)
        }

    @Test
    fun `my rides show the owner's fields, and a private or cancelled one has no page`() = runTest {
        site.json(200, site.fixture("own-ride-page.json"))

        val page = rides.mine()

        assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/me/rides")
        val (private, cancelled, open) = page.items
        assertThat(private.isPublic).isFalse()
        assertThat(private.privacyRadiusM).isEqualTo(300)
        assertThat(private.pointCount).isEqualTo(1200)
        assertThat(private.hasPage).isFalse()
        assertThat(cancelled.ride.status).isEqualTo(RideStatus.Cancelled)
        assertThat(cancelled.hasPage).isFalse()
        assertThat(open.privacyRadiusM).isNull()
        assertThat(open.hasPage).isTrue()
    }

    @Test
    fun `my upcoming is one request without a cursor, with the role and what changed`() = runTest {
        site.json(200, site.fixture("my-upcoming-rides.json"))

        val plans = rides.myUpcoming()

        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/me/rides/upcoming")
        assertThat(request.query).isNull()
        assertThat(plans.map { it.role })
            .containsExactly(
                RideRole.Organizer,
                RideRole.Accepted,
                RideRole.Maybe,
                RideRole.Invited,
                RideRole.Cancelled,
            )
            .inOrder()
        assertThat(plans[0].meetingPoint).isEqualTo("У фонтана")
        assertThat(plans[1].changedAfterAnswer).isTrue()
        assertThat(plans[2].occurrenceCancelled).isTrue()
        assertThat(plans[2].ride.recurrence).isEqualTo(RideRecurrence.Weekly)
        assertThat(plans[2].hasPage).isTrue()
        assertThat(plans[3].meetingHidden).isTrue()
        assertThat(plans[3].meetingPoint).isNull()
        assertThat(plans[4].hasPage).isFalse()
    }

    @Test
    fun `a guest's personal lists are signed out`() = runTest {
        site.json(401, error("unauthorized"))
        site.json(401, error("unauthorized"))

        assertThat(runCatching { rides.mine() }.exceptionOrNull())
            .isInstanceOf(DataError.SignedOut::class.java)
        assertThat(runCatching { rides.myUpcoming() }.exceptionOrNull())
            .isInstanceOf(DataError.SignedOut::class.java)
    }
}
