package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.SavedChange

class FeedRepositoryTest {
    private val site = TestServer()
    private val feed = NetworkFeedRepository(site.api.personal, site.media, Dispatchers.Unconfined)

    @After fun close() = site.close()

    @Test
    fun `every kind of publication maps to its own item and the filter is sent`() = runTest {
        site.json(200, site.fixture("feed-page.json"))

        val page = feed.feed(FeedFilter.Journal, cursor = "c0", limit = 10)

        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/me/feed")
        assertThat(request.queryParameter("type")).isEqualTo("journal")
        assertThat(request.queryParameter("cursor")).isEqualTo("c0")
        assertThat(request.queryParameter("limit")).isEqualTo("10")
        val (bike, journal, ride, listing) = page.items
        assertThat(bike).isInstanceOf(FeedItem.Bike::class.java)
        assertThat((journal as FeedItem.Journal).entry.title).isEqualTo("Замена цепи и кассеты")
        assertThat(journal.publishedAt).isEqualTo(Instant.parse("2026-09-20T09:00:00Z"))
        val rideSummary = (ride as FeedItem.Ride).ride
        assertThat(rideSummary.status).isEqualTo(RideStatus.Completed)
        assertThat(rideSummary.time)
            .isEqualTo(Instant.parse("2026-09-20T16:00:00Z").minusSeconds(0))
        assertThat(rideSummary.distanceMeters).isEqualTo(32_450)
        val brief = (listing as FeedItem.Listing).listing
        assertThat(brief.price).isEqualTo(2500.0)
        assertThat(brief.cover?.url).isEqualTo(site.config.siteUrl + "/api/market-photos/f4a5b6c7")
        assertThat(page.nextCursor).isEqualTo("eyJ0IjoiMjAyNi0wOS0xNiJ9")
    }

    @Test
    fun `a type from the future and an item without its object are left out`() = runTest {
        site.json(200, site.fixture("feed-page.json"))

        val page = feed.feed(FeedFilter.All)

        // Six items on the wire: "poll" is unknown and the last journal item has no journal.
        assertThat(page.items).hasSize(4)
        assertThat(page.items.map { it.key }.toSet()).hasSize(4)
        assertThat(site.server.takeRequest().url.queryParameter("type")).isEqualTo("all")
    }

    @Test
    fun `a guest asking for the feed is signed out`() = runTest {
        site.json(401, error("unauthorized"))

        val failure = runCatching { feed.feed(FeedFilter.All) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.SignedOut::class.java)
    }
}

class JournalRepositoryTest {
    private val site = TestServer()
    private val journal =
        NetworkJournalRepository(
            site.api.journal,
            site.api.personal,
            site.media,
            Dispatchers.Unconfined,
        )
    private val bike = "6e7f8091-a2b3-4c4d-9e5f-60718293a4b5"
    private val entry = "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f"

    @After fun close() = site.close()

    @Test
    fun `a bike's journal pages with the cursor and maps what a card needs`() = runTest {
        site.json(200, site.fixture("journal-page.json"))

        val page = journal.ofBike(BikeId(bike), cursor = "c1", limit = 5)

        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/bikes/$bike/journal")
        assertThat(request.queryParameter("cursor")).isEqualTo("c1")
        assertThat(request.queryParameter("limit")).isEqualTo("5")
        val item = page.items.single()
        assertThat(item.kind).isEqualTo("service")
        assertThat(item.status).isEqualTo(JournalStatus.Published)
        assertThat(item.eventDate).isEqualTo(LocalDate.parse("2026-09-14"))
        assertThat(item.mileageKm).isEqualTo(4200)
        assertThat(item.comments).isEqualTo(2)
        assertThat(item.excerpt).startsWith("Цепь вытянулась")
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `an entry keeps its snapshot, drops unsafe links and photos, and marks unknown sections`() =
        runTest {
            site.json(200, site.fixture("journal-entry.json"))

            val result = journal.entry(JournalId(entry))

            assertThat(site.server.takeRequest().url.encodedPath)
                .isEqualTo("/api/v1/journal/$entry")
            assertThat(result.body).contains("**цепь**")
            val (first, second) = result.components
            assertThat(first.url).isEqualTo("https://example.test/kmc")
            assertThat(first.priceRub).isEqualTo(1800.0)
            assertThat(second.url).isNull()
            assertThat(second.priceRub).isNull()
            assertThat(second.section).isEqualTo("other")
            assertThat(result.photos.map { it.url })
                .containsExactly(site.config.siteUrl + "/api/journal-photos/c1c1c1c1")
            assertThat(result.summary.excerpt).isEmpty()
        }

    @Test
    fun `a malformed id is not found without a request`() = runTest {
        listOf("", "../me", "not-a-uuid").forEach {
            assertThat(runCatching { journal.entry(JournalId(it)) }.exceptionOrNull())
                .isInstanceOf(DataError.NotFound::class.java)
            assertThat(runCatching { journal.ofBike(BikeId(it)) }.exceptionOrNull())
                .isInstanceOf(DataError.NotFound::class.java)
            assertThat(runCatching { journal.setSaved(JournalId(it), true) }.exceptionOrNull())
                .isInstanceOf(DataError.NotFound::class.java)
        }
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `saving is PUT, unsaving is DELETE, the answer is the state and it is announced`() =
        runTest {
            val id = JournalId(entry)
            assertThat(journal.isSaved(id)).isNull()
            site.json(200, """{"saved":true}""")
            site.json(200, """{"saved":false}""")
            val announced = mutableListOf<SavedChange>()
            val job =
                launch(Dispatchers.Unconfined) {
                    journal.savedChanges.collect { announced += it }
                }

            assertThat(journal.setSaved(id, true)).isTrue()
            assertThat(journal.isSaved(id)).isTrue()
            assertThat(journal.setSaved(id, false)).isFalse()
            assertThat(journal.isSaved(id)).isFalse()

            val put = site.server.takeRequest()
            val delete = site.server.takeRequest()
            assertThat(put.method).isEqualTo("PUT")
            assertThat(delete.method).isEqualTo("DELETE")
            assertThat(put.url.encodedPath).isEqualTo("/api/v1/journal/$entry/save")
            assertThat(announced)
                .containsExactly(SavedChange(id, true), SavedChange(id, false))
                .inOrder()
            job.cancel()
        }

    @Test
    fun `a refused save changes nothing and is not announced`() = runTest {
        val id = JournalId(entry)
        site.json(404, error("not_found"))

        val failure = runCatching { journal.setSaved(id, true) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(journal.isSaved(id)).isNull()
    }

    @Test
    fun `the saved list marks its entries as saved`() = runTest {
        site.json(200, site.fixture("journal-page.json"))

        val page = journal.saved()

        assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/me/saved/journal")
        assertThat(journal.isSaved(page.items.single().id)).isTrue()
    }
}
