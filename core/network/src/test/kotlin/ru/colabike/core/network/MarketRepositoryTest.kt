package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ListingCategory
import ru.colabike.core.model.ListingCondition
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.ListingSort
import ru.colabike.core.model.ListingStatus
import ru.colabike.core.model.ListingType
import ru.colabike.core.model.MarketQuery
import ru.colabike.core.model.SavedListingChange

class MarketRepositoryTest {
    private val site = TestServer()
    private val market =
        NetworkMarketRepository(
            site.api.market,
            site.api.personal,
            site.media,
            Dispatchers.Unconfined,
        )

    @After fun close() = site.close()

    private val id = ListingId("a0000000-0000-4000-8000-000000000001")

    @Test
    fun `the list asks for every filter, the order and the cursor it was given`() = runTest {
        site.json(200, site.fixture("market-page.json"))

        val page =
            market.page(
                MarketQuery(
                    text = "  кассета ",
                    category = ListingCategory.Components,
                    type = ListingType.Sale,
                    condition = ListingCondition.Used,
                    priceMin = 1000,
                    priceMax = 5000,
                    city = " Москва ",
                    seller = "test-rider",
                    sort = ListingSort.PriceAsc,
                ),
                cursor = "c0",
                limit = 12,
            )

        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/market")
        assertThat(request.queryParameter("q")).isEqualTo("кассета")
        assertThat(request.queryParameter("category")).isEqualTo("components")
        assertThat(request.queryParameter("type")).isEqualTo("sale")
        assertThat(request.queryParameter("condition")).isEqualTo("used")
        assertThat(request.queryParameter("price_min")).isEqualTo("1000")
        assertThat(request.queryParameter("price_max")).isEqualTo("5000")
        assertThat(request.queryParameter("city")).isEqualTo("Москва")
        assertThat(request.queryParameter("seller")).isEqualTo("test-rider")
        assertThat(request.queryParameter("sort")).isEqualTo("price_asc")
        assertThat(request.queryParameter("cursor")).isEqualTo("c0")
        assertThat(request.queryParameter("limit")).isEqualTo("12")
        assertThat(page.nextCursor).isEqualTo("eyJwIjoiNDUwMCIsImlkIjoiYTAifQ")
    }

    @Test
    fun `an empty query sends no filters, the newest first`() = runTest {
        site.json(200, site.fixture("market-page.json"))

        market.page(MarketQuery())

        val request = site.server.takeRequest().url
        for (name in listOf("q", "category", "type", "condition", "price_min", "price_max")) {
            assertThat(request.queryParameter(name)).isNull()
        }
        assertThat(request.queryParameter("city")).isNull()
        assertThat(request.queryParameter("seller")).isNull()
        assertThat(request.queryParameter("sort")).isEqualTo("new")
        assertThat(request.queryParameter("cursor")).isNull()
    }

    @Test
    fun `the other orders are named as the server names them, and a price is never negative`() =
        runTest {
            site.json(200, site.fixture("market-page.json"))
            site.json(200, site.fixture("market-page.json"))

            market.page(MarketQuery(sort = ListingSort.PriceDesc, priceMin = -5))
            market.page(MarketQuery(type = ListingType.Free, category = ListingCategory.Bikes))

            val first = site.server.takeRequest().url
            assertThat(first.queryParameter("sort")).isEqualTo("price_desc")
            assertThat(first.queryParameter("price_min")).isEqualTo("0")
            val second = site.server.takeRequest().url
            assertThat(second.queryParameter("type")).isEqualTo("free")
            assertThat(second.queryParameter("category")).isEqualTo("bikes")
        }

    @Test
    fun `a card has the price as it is, a free thing, a wish without a price and the first photo`() =
        runTest {
            site.json(200, site.fixture("market-page.json"))

            val (cassette, wanted, free) = market.page(MarketQuery()).items

            assertThat(cassette.id).isEqualTo(id.value)
            assertThat(cassette.price).isEqualTo(4500.0)
            assertThat(cassette.currency).isEqualTo("RUB")
            assertThat(cassette.type).isEqualTo("sale")
            assertThat(cassette.cover?.url).endsWith("/api/market/media/b010?width=640")
            assertThat(cassette.author.displayName).isEqualTo("Тестовый Райдер")
            // By agreement is null, not zero; nothing to show is no cover, not a blank address.
            assertThat(wanted.price).isNull()
            assertThat(wanted.cover).isNull()
            assertThat(free.price).isEqualTo(0.0)
            assertThat(free.type).isEqualTo("free")
        }

    @Test
    fun `a seller that cannot be a username is a missing seller and sends nothing`() = runTest {
        val error = runCatching { market.page(MarketQuery(seller = "../me")) }.exceptionOrNull()

        assertThat(error).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a listing has its page with the links to the catalog and to the bike, and what the viewer saved`() =
        runTest {
            site.json(200, site.fixture("market-listing.json"))

            val listing = market.listing(id)

            assertThat(site.server.takeRequest().url.encodedPath)
                .isEqualTo("/api/v1/market/${id.value}")
            assertThat(listing.brief.title).isEqualTo("Кассета Shimano 12")
            assertThat(listing.description).isEqualTo("Описание: Кассета Shimano 12")
            assertThat(listing.condition).isEqualTo(ListingCondition.Used)
            assertThat(listing.status).isEqualTo(ListingStatus.Active)
            assertThat(listing.onMarket).isTrue()
            assertThat(listing.hasContact).isTrue()
            assertThat(listing.saved).isTrue()
            assertThat(listing.isOwner).isFalse()
            assertThat(listing.publishedAt).isEqualTo(Instant.parse("2026-09-21T10:00:00Z"))
            assertThat(listing.photos).hasSize(2)
            assertThat(listing.photos.first().url).endsWith("/api/market/media/b010?width=640")
            assertThat(listing.componentModel?.id).isEqualTo("c0000000-0000-4000-8000-000000000001")
            assertThat(listing.componentModel?.path).isEqualTo("/components/deore-m6100-c0000000")
            assertThat(listing.bikeModel).isNull()
            assertThat(listing.linkedBike?.id)
                .isEqualTo(BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"))
        }

    @Test
    fun `a sold and an expired listing are read with their note, and are not on the market`() =
        runTest {
            site.json(200, site.fixture("market-listing-sold.json"))
            site.json(200, site.fixture("market-listing-expired.json"))

            val sold = market.listing(ListingId("a0000000-0000-4000-8000-000000000004"))
            val expired = market.listing(ListingId("a0000000-0000-4000-8000-000000000005"))

            assertThat(sold.status).isEqualTo(ListingStatus.Sold)
            assertThat(sold.onMarket).isFalse()
            assertThat(expired.status).isEqualTo(ListingStatus.Active)
            assertThat(expired.expired).isTrue()
            assertThat(expired.onMarket).isFalse()
        }

    @Test
    fun `an own draft is a draft, and the contact the owner's copy carries is not read at all`() =
        runTest {
            site.json(200, site.fixture("market-listing-draft-own.json"))

            val draft = market.listing(ListingId("a0000000-0000-4000-8000-000000000006"))

            assertThat(draft.status).isEqualTo(ListingStatus.Draft)
            assertThat(draft.isOwner).isTrue()
            assertThat(draft.onMarket).isFalse()
            assertThat(draft.publishedAt).isNull()
            // Neither the model nor its text form can hold the number from the fixture.
            assertThat(draft.toString()).doesNotContain("900")
        }

    @Test
    fun `values of a later server version are tolerated, not a crash`() = runTest {
        site.json(200, site.fixture("market-listing-future.json"))

        val listing = market.listing(ListingId("a0000000-0000-4000-8000-000000000007"))

        assertThat(listing.status).isEqualTo(ListingStatus.Unknown)
        assertThat(listing.onMarket).isFalse()
        assertThat(listing.condition).isNull()
        assertThat(listing.brief.title).isEqualTo("Из будущего")
    }

    @Test
    fun `a hidden or unknown listing is not found, and an id that is no UUID never leaves the app`() =
        runTest {
            site.json(404, error("not_found", "Нет такого объявления"))
            val missing = runCatching { market.listing(id) }.exceptionOrNull()
            assertThat(missing).isInstanceOf(DataError.NotFound::class.java)

            for (call in
                listOf<suspend () -> Any>(
                    { market.listing(ListingId("../../me")) },
                    { market.sellerOthers(ListingId("nope")) },
                    { market.setSaved(ListingId("x"), true) },
                    { market.contact(ListingId("1")) },
                )) {
                assertThat(runCatching { call() }.exceptionOrNull())
                    .isInstanceOf(DataError.NotFound::class.java)
            }
            // One request: the 404 above. Nothing was sent for the four bad ids.
            assertThat(site.server.requestCount).isEqualTo(1)
        }

    @Test
    fun `the seller's other listings come with how many there are`() = runTest {
        site.json(200, site.fixture("market-others.json"))

        val others = market.sellerOthers(id)

        assertThat(site.server.takeRequest().url.encodedPath)
            .isEqualTo("/api/v1/market/${id.value}/others")
        assertThat(others.items.map { it.title }).containsExactly("Звонок", "Фара").inOrder()
        assertThat(others.total).isEqualTo(6)
    }

    @Test
    fun `saving and un-saving take the answer of the server, tell the other screens, and are idempotent`() =
        runTest {
            site.json(200, site.fixture("market-saved.json"))
            site.json(200, site.fixture("market-unsaved.json"))
            val seen = mutableListOf<SavedListingChange>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    market.savedChanges.toList(seen)
                }

            val saved = market.setSaved(id, true)
            val unsaved = market.setSaved(id, false)
            job.cancel()

            assertThat(saved).isTrue()
            assertThat(unsaved).isFalse()
            val put = site.server.takeRequest()
            assertThat(put.method).isEqualTo("PUT")
            assertThat(put.url.encodedPath).isEqualTo("/api/v1/market/${id.value}/save")
            assertThat(site.server.takeRequest().method).isEqualTo("DELETE")
            assertThat(seen)
                .containsExactly(SavedListingChange(id, true), SavedListingChange(id, false))
                .inOrder()
        }

    @Test
    fun `a failed save tells nobody`() = runTest {
        site.json(404, error("not_found"))
        val seen = mutableListOf<SavedListingChange>()
        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) { market.savedChanges.toList(seen) }

        val error = runCatching { market.setSaved(id, true) }.exceptionOrNull()
        job.cancel()

        assertThat(error).isInstanceOf(DataError.NotFound::class.java)
        assertThat(seen).isEmpty()
    }

    @Test
    fun `the saved list is the personal one, with its own cursor`() = runTest {
        site.json(200, site.fixture("market-page.json"))

        val page = market.saved(cursor = "s1", limit = 10)

        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/me/saved/market")
        assertThat(request.queryParameter("cursor")).isEqualTo("s1")
        assertThat(request.queryParameter("limit")).isEqualTo("10")
        assertThat(page.items).hasSize(3)
    }

    @Test
    fun `the contact is asked for by its own request and is given as it is`() = runTest {
        site.json(200, site.fixture("market-contact.json"))

        val contact = market.contact(id)

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/market/${id.value}/contact")
        assertThat(contact).isEqualTo("+7 900 111-22-33, Telegram @seller")
        // Reading a list or a page never asks for it.
        site.json(200, site.fixture("market-listing.json"))
        market.listing(id)
        assertThat(site.server.takeRequest().url.encodedPath).doesNotContain("contact")
    }

    @Test
    fun `an unconfirmed e-mail, a limit, a guest and a listing that is gone are four different answers`() =
        runTest {
            site.json(403, error("email_verification_required", "Подтвердите почту"))
            site.json(429, error("rate_limited"), "Retry-After", "120")
            site.json(401, error("unauthorized"))
            site.json(404, error("not_found"))

            val unverified = runCatching { market.contact(id) }.exceptionOrNull()
            val limited = runCatching { market.contact(id) }.exceptionOrNull()
            val guest = runCatching { market.contact(id) }.exceptionOrNull()
            val gone = runCatching { market.contact(id) }.exceptionOrNull()

            assertThat((unverified as DataError.Rejected).code)
                .isEqualTo("email_verification_required")
            assertThat((limited as DataError.RateLimited).retryAfterSeconds).isEqualTo(120)
            assertThat(guest).isNotNull()
            assertThat(gone).isInstanceOf(DataError.NotFound::class.java)
        }

    @Test
    fun `a contact in a failure is not in the text of the error`() = runTest {
        site.json(500, """{"error":{"code":"x","message":"+7 900 111-22-33"}}""")

        val error = runCatching { market.contact(id) }.exceptionOrNull()

        assertThat(error).isInstanceOf(DataError.Server::class.java)
        assertThat(error.toString()).doesNotContain("900")
    }
}
