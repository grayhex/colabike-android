package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.market.ContactState
import ru.colabike.app.market.ListingUiState
import ru.colabike.app.market.ListingViewModel
import ru.colabike.app.market.MarketViewModel
import ru.colabike.app.market.SavedMarketViewModel
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ListingCategory
import ru.colabike.core.model.ListingCondition
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.ListingSort
import ru.colabike.core.model.ListingStatus
import ru.colabike.core.model.ListingType
import ru.colabike.core.model.MarketQuery
import ru.colabike.core.model.Page

/** The market list, a listing's page with its save and its contact, and the saved ones. */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeMarket()

    private fun market(seller: String? = null) =
        MarketViewModel(repository, seller, debounceMs = 400)

    private fun listing(id: String = "l1", repository: FakeMarket = this.repository) =
        ListingViewModel(repository, ListingId(id))

    // --- the list ----------------------------------------------------------------------------

    @Test
    fun `it opens on every listing, newest first`() = runTest {
        val viewModel = market()

        assertThat(repository.queries).containsExactly(MarketQuery() to null)
        assertThat(viewModel.state.value.page.items.map { it.id })
            .containsExactly("l1", "l2", "l3", "l4")
    }

    @Test
    fun `typing waits for a pause and then asks once, with the whole text`() = runTest {
        val viewModel = market()
        repository.queries.clear()

        viewModel.onSearchText("в")
        advanceTimeBy(200)
        viewModel.onSearchText("втул")
        advanceTimeBy(200)
        viewModel.onSearchText("втулка ")
        assertThat(viewModel.state.value.fields.text).isEqualTo("втулка ")
        assertThat(repository.queries).isEmpty()

        advanceTimeBy(401)
        runCurrent()

        assertThat(repository.queries).containsExactly(MarketQuery(text = "втулка") to null)
    }

    @Test
    fun `a price is digits and whole roubles, and it waits for the pause like the text`() =
        runTest {
            val viewModel = market()
            repository.queries.clear()

            viewModel.onPriceMin("1 0a00")
            viewModel.onPriceMax("5000000000000")
            assertThat(viewModel.state.value.fields.priceMin).isEqualTo("1000")
            // At most ten digits: the API takes no more.
            assertThat(viewModel.state.value.fields.priceMax).isEqualTo("5000000000")
            advanceTimeBy(401)
            runCurrent()

            assertThat(repository.queries.last().first)
                .isEqualTo(MarketQuery(priceMin = 1000, priceMax = 5_000_000_000))
        }

    @Test
    fun `an emptied price field lifts the bound`() = runTest {
        val viewModel = market()
        viewModel.onPriceMin("100")
        advanceTimeBy(401)
        runCurrent()
        assertThat(viewModel.state.value.query.priceMin).isEqualTo(100)

        viewModel.onPriceMin("")
        advanceTimeBy(401)
        runCurrent()

        assertThat(viewModel.state.value.query.priceMin).isNull()
    }

    @Test
    fun `a filter starts the list over with no cursor, and choosing it again lifts it`() = runTest {
        repository.pages =
            mapOf(
                null to Page(listingBriefs(1, 2), "next"),
                "next" to Page(listingBriefs(3, 2), null),
            )
        val viewModel = market()
        viewModel.loadMore()
        assertThat(viewModel.state.value.page.items).hasSize(4)
        repository.queries.clear()

        viewModel.selectCategory(ListingCategory.Components)
        assertThat(repository.queries.last())
            .isEqualTo(MarketQuery(category = ListingCategory.Components) to null)
        assertThat(viewModel.state.value.page.items).hasSize(2)

        viewModel.selectType(ListingType.Sale)
        viewModel.selectCondition(ListingCondition.Used)
        assertThat(repository.queries.last().first)
            .isEqualTo(
                MarketQuery(
                    category = ListingCategory.Components,
                    type = ListingType.Sale,
                    condition = ListingCondition.Used,
                )
            )

        viewModel.selectType(ListingType.Sale)
        assertThat(repository.queries.last().first.type).isNull()
    }

    @Test
    fun `a new order is a new list, because a cursor of one order is refused in the other`() =
        runTest {
            repository.pages = mapOf(null to Page(listingBriefs(1, 2), "next"))
            val viewModel = market()

            viewModel.selectSort(ListingSort.PriceAsc)
            assertThat(repository.queries.last())
                .isEqualTo(MarketQuery(sort = ListingSort.PriceAsc) to null)
            viewModel.loadMore()
            repository.queries.clear()
            viewModel.selectSort(ListingSort.PriceDesc)

            // The cursor of the price-ascending page is not sent to the descending list.
            assertThat(repository.queries)
                .containsExactly(MarketQuery(sort = ListingSort.PriceDesc) to null)
        }

    @Test
    fun `the next page is appended, and a listing on two pages shows once`() = runTest {
        repository.pages =
            mapOf(
                null to Page(listingBriefs(1, 3), "next"),
                "next" to Page(listingBriefs(3, 2), null),
            )
        val viewModel = market()

        viewModel.loadMore()

        assertThat(viewModel.state.value.page.items.map { it.id })
            .containsExactly("l1", "l2", "l3", "l4")
            .inOrder()
        assertThat(viewModel.state.value.page.nextCursor).isNull()
    }

    @Test
    fun `a seller's list asks for that seller, and clearing the filters keeps the seller and the order`() =
        runTest {
            val viewModel = market(seller = "test-rider")
            assertThat(repository.queries.last().first.seller).isEqualTo("test-rider")
            viewModel.selectSort(ListingSort.PriceDesc)
            viewModel.selectCategory(ListingCategory.Bikes)

            viewModel.clearFilters()

            assertThat(repository.queries.last())
                .isEqualTo(MarketQuery(seller = "test-rider", sort = ListingSort.PriceDesc) to null)
            assertThat(viewModel.state.value.query.hasFilters).isFalse()
        }

    @Test
    fun `a failed first page is an error with a retry, and a failed next page keeps the list`() =
        runTest {
            repository.nextError = DataError.Offline(java.io.IOException("x"))
            val viewModel = market()
            assertThat(viewModel.state.value.page.error).isNotNull()

            viewModel.retry()
            assertThat(viewModel.state.value.page.error).isNull()
            assertThat(viewModel.state.value.page.items).hasSize(4)

            repository.pages = mapOf(null to Page(listingBriefs(1, 4), "next"))
            viewModel.refresh()
            repository.nextError = DataError.Offline(java.io.IOException("x"))
            viewModel.loadMore()

            assertThat(viewModel.state.value.page.items).hasSize(4)
            assertThat(viewModel.state.value.page.moreError).isNotNull()
        }

    // --- the page ----------------------------------------------------------------------------

    @Test
    fun `a listing loads its page and then its seller's others, and asks for no contact`() =
        runTest {
            val viewModel = listing()

            val state = viewModel.state.value as ListingUiState.Loaded

            assertThat(state.listing.brief.title).isEqualTo("Втулка 1")
            assertThat(state.others?.total).isEqualTo(6)
            assertThat(state.contact).isEqualTo(ContactState.Hidden)
            assertThat(repository.contactCalls).isEmpty()
        }

    @Test
    fun `a listing that is not there is told so, and a failure is told with a retry`() = runTest {
        val gone = listing("nope")
        val failed = (gone.state.value as ListingUiState.Failed)
        assertThat(failed.notFound).isTrue()

        repository.listingError = DataError.Offline(java.io.IOException("x"))
        val offline = listing()
        assertThat((offline.state.value as ListingUiState.Failed).notFound).isFalse()
        offline.load()
        assertThat(offline.state.value).isInstanceOf(ListingUiState.Loaded::class.java)
    }

    @Test
    fun `the others failing does not fail the page`() = runTest {
        repository.othersError = DataError.Offline(java.io.IOException("x"))

        val state = listing().state.value as ListingUiState.Loaded

        assertThat(state.others).isNull()
        assertThat(state.listing.brief.id).isEqualTo("l1")
    }

    @Test
    fun `the bookmark shows the result at once and takes the answer of the server`() = runTest {
        val viewModel = listing()

        viewModel.toggleSaved()

        assertThat((viewModel.state.value as ListingUiState.Loaded).saved).isTrue()
        assertThat(repository.saveCalls).containsExactly("l1" to true)
        viewModel.toggleSaved()
        assertThat((viewModel.state.value as ListingUiState.Loaded).saved).isFalse()
        assertThat(repository.saveCalls.last()).isEqualTo("l1" to false)
    }

    @Test
    fun `a refused save puts the bookmark back and says why`() = runTest {
        val viewModel = listing()
        repository.saveError = DataError.Offline(java.io.IOException("x"))

        viewModel.toggleSaved()

        val state = viewModel.state.value as ListingUiState.Loaded
        assertThat(state.saved).isFalse()
        assertThat(state.saving).isFalse()
        assertThat(state.saveError).isNotNull()
    }

    @Test
    fun `a listing that is off the market cannot be saved, whatever the tap`() = runTest {
        repository.listings =
            mapOf(
                "l1" to listingModel(1, status = ListingStatus.Sold),
                "l2" to listingModel(2, expired = true),
            )

        for (id in listOf("l1", "l2")) {
            val viewModel = listing(id)
            viewModel.toggleSaved()
            assertThat((viewModel.state.value as ListingUiState.Loaded).canSave).isFalse()
        }
        assertThat(repository.saveCalls).isEmpty()
    }

    @Test
    fun `saved on the saved list is shown on the page without loading it again`() = runTest {
        val viewModel = listing()
        val before = repository.listingCalls.size

        repository.setSaved(ListingId("l1"), true)

        assertThat((viewModel.state.value as ListingUiState.Loaded).saved).isTrue()
        assertThat(repository.listingCalls).hasSize(before)
    }

    // --- the contact -------------------------------------------------------------------------

    @Test
    fun `the contact is asked for by a tap, once, and kept only in this state`() = runTest {
        val viewModel = listing()

        viewModel.showContact()
        viewModel.showContact()

        val contact = (viewModel.state.value as ListingUiState.Loaded).contact
        assertThat(contact).isInstanceOf(ContactState.Shown::class.java)
        assertThat((contact as ContactState.Shown).text).contains("@seller")
        // The second tap found it already shown and asked nobody.
        assertThat(repository.contactCalls).containsExactly("l1")
        // The text is not in the state's text form (it ends up in logs and crash reports).
        assertThat(viewModel.state.value.toString()).doesNotContain("@seller")
        assertThat(viewModel.state.value.toString()).doesNotContain("900")
    }

    @Test
    fun `the contact can be taken off the screen and is asked for again`() = runTest {
        val viewModel = listing()
        viewModel.showContact()

        viewModel.hideContact()
        assertThat((viewModel.state.value as ListingUiState.Loaded).contact)
            .isEqualTo(ContactState.Hidden)
        viewModel.showContact()

        assertThat(repository.contactCalls).containsExactly("l1", "l1")
    }

    @Test
    fun `an unconfirmed e-mail, a limit and a listing that is gone are told in words`() = runTest {
        val viewModel = listing()

        repository.contactError =
            DataError.Rejected(403, "email_verification_required", "Подтвердите почту")
        viewModel.showContact()
        val unverified = (viewModel.state.value as ListingUiState.Loaded).contact
        assertThat(unverified).isInstanceOf(ContactState.Failed::class.java)

        repository.contactError = DataError.RateLimited(retryAfterSeconds = 120)
        viewModel.showContact()
        val limited = (viewModel.state.value as ListingUiState.Loaded).contact
        assertThat(limited).isInstanceOf(ContactState.Failed::class.java)
        // The two are not the same words.
        assertThat((limited as ContactState.Failed).message)
            .isNotEqualTo((unverified as ContactState.Failed).message)

        repository.contactError = DataError.NotFound()
        viewModel.showContact()
        assertThat((viewModel.state.value as ListingUiState.Loaded).contact)
            .isInstanceOf(ContactState.Failed::class.java)

        // A failure leaves the way to try again.
        viewModel.showContact()
        assertThat((viewModel.state.value as ListingUiState.Loaded).contact)
            .isInstanceOf(ContactState.Shown::class.java)
    }

    @Test
    fun `no contact is asked for a sold or expired listing, an own one, or one without a contact`() =
        runTest {
            repository.listings =
                mapOf(
                    "l1" to listingModel(1, status = ListingStatus.Sold),
                    "l2" to listingModel(2, expired = true),
                    "l3" to listingModel(3, isOwner = true),
                    "l4" to listingModel(4, hasContact = false),
                    "l5" to listingModel(5, status = ListingStatus.Draft, isOwner = true),
                )

            for (id in listOf("l1", "l2", "l3", "l4", "l5")) {
                val viewModel = listing(id)
                viewModel.showContact()
                assertThat((viewModel.state.value as ListingUiState.Loaded).contact)
                    .isEqualTo(ContactState.Hidden)
            }
            assertThat(repository.contactCalls).isEmpty()
        }

    // --- the saved ones ----------------------------------------------------------------------

    @Test
    fun `the saved list drops a listing that was un-saved on its page`() = runTest {
        val viewModel = SavedMarketViewModel(repository)
        assertThat(viewModel.state.value.items.map { it.id }).containsExactly("l1", "l2")

        repository.setSaved(ListingId("l1"), false)

        assertThat(viewModel.state.value.items.map { it.id }).containsExactly("l2")
        // Saving does not add it to the list; that is for the next load.
        repository.setSaved(ListingId("l9"), true)
        assertThat(viewModel.state.value.items.map { it.id }).containsExactly("l2")
    }
}
