package ru.colabike.app.market

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.Pager
import ru.colabike.core.model.ListingBrief
import ru.colabike.core.model.ListingCategory
import ru.colabike.core.model.ListingCondition
import ru.colabike.core.model.ListingSort
import ru.colabike.core.model.ListingType
import ru.colabike.core.model.MarketQuery
import ru.colabike.core.model.MarketRepository

/** What the person typed into the fields that wait for a pause before they become a query. */
@Immutable
data class MarketFields(
    val text: String = "",
    val priceMin: String = "",
    val priceMax: String = "",
    val city: String = "",
)

@Immutable
data class MarketUiState(
    val fields: MarketFields = MarketFields(),
    /** What the list was asked for. A new query starts a new list. */
    val query: MarketQuery = MarketQuery(),
    val page: PagedState<ListingBrief> = PagedState(),
)

/**
 * The market list: keyset paging over `/market` with the server's own search, filters and order. A
 * new query cancels the request in flight and starts over with no cursor, so the pages of the old
 * query can never be appended to the new list (a cursor of one order, or of one filter set, is a
 * 400 or a wrong page in another). With a [seller] the list is that person's listings.
 */
class MarketViewModel(
    private val repository: MarketRepository,
    private val seller: String? = null,
    private val debounceMs: Long = DEBOUNCE_MS,
) : ViewModel() {
    private val fields = MutableStateFlow(MarketFields())
    private val query = MutableStateFlow(MarketQuery(seller = seller))
    private var typing: Job? = null

    private val pager =
        Pager<ListingBrief, String>(viewModelScope, { it.id }) { cursor ->
            repository.page(query.value, cursor)
        }

    val state: StateFlow<MarketUiState> =
        combine(fields, query, pager.state, ::MarketUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, MarketUiState())

    init {
        pager.load()
    }

    /** The field follows every key at once, the list waits for a pause. */
    fun onSearchText(text: String) = type { it.copy(text = text) }

    fun clearSearch() = onSearchText("")

    fun onCity(city: String) = type { it.copy(city = city) }

    /** Digits only: the API takes whole roubles, and nothing else can be a price. */
    fun onPriceMin(text: String) = type { it.copy(priceMin = text.digits()) }

    fun onPriceMax(text: String) = type { it.copy(priceMax = text.digits()) }

    /** Picking the chosen one again lifts the filter. */
    fun selectCategory(category: ListingCategory) =
        apply(query.value.copy(category = category.takeUnless { it == query.value.category }))

    fun selectType(type: ListingType) =
        apply(query.value.copy(type = type.takeUnless { it == query.value.type }))

    fun selectCondition(condition: ListingCondition) =
        apply(query.value.copy(condition = condition.takeUnless { it == query.value.condition }))

    fun selectSort(sort: ListingSort) = apply(query.value.copy(sort = sort))

    /** Back to every listing (of the seller, when there is one); the order stays. */
    fun clearFilters() {
        typing?.cancel()
        fields.value = MarketFields()
        apply(MarketQuery(seller = seller, sort = query.value.sort))
    }

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun loadMore() = pager.loadMore()

    private fun type(change: (MarketFields) -> MarketFields) {
        fields.value = change(fields.value)
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(debounceMs)
            val typed = fields.value
            apply(
                query.value.copy(
                    text = typed.text.trim(),
                    city = typed.city.trim(),
                    priceMin = typed.priceMin.toLongOrNull(),
                    priceMax = typed.priceMax.toLongOrNull(),
                )
            )
        }
    }

    private fun apply(next: MarketQuery) {
        if (next == query.value) return
        query.value = next
        pager.load()
    }

    private fun String.digits() = filter { it in '0'..'9' }.take(MAX_PRICE_DIGITS)

    private companion object {
        const val DEBOUNCE_MS = 400L
        const val MAX_PRICE_DIGITS = 10
    }
}
