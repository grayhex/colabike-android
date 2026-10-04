package ru.colabike.app.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.ListingCard
import ru.colabike.core.designsystem.component.SkeletonGroup
import ru.colabike.core.designsystem.component.colaFieldShape
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.ListingBrief
import ru.colabike.core.model.ListingCategory
import ru.colabike.core.model.ListingCondition
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.ListingSort
import ru.colabike.core.model.ListingType
import ru.colabike.core.model.MarketQuery
import ru.colabike.core.model.MarketRepository

@Composable
fun MarketRoute(
    repository: MarketRepository,
    seller: String?,
    onBack: () -> Unit,
    onOpen: (ListingId) -> Unit,
) {
    val viewModel =
        viewModel(key = "market:${seller.orEmpty()}") { MarketViewModel(repository, seller) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MarketScreen(
        state = state,
        seller = seller,
        onBack = onBack,
        actions =
            MarketActions(
                onSearchText = viewModel::onSearchText,
                onClearSearch = viewModel::clearSearch,
                onCity = viewModel::onCity,
                onPriceMin = viewModel::onPriceMin,
                onPriceMax = viewModel::onPriceMax,
                onCategory = viewModel::selectCategory,
                onType = viewModel::selectType,
                onCondition = viewModel::selectCondition,
                onSort = viewModel::selectSort,
                onClearFilters = viewModel::clearFilters,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                onOpen = onOpen,
            ),
    )
}

/** What the market list can be asked to do. */
class MarketActions(
    val onSearchText: (String) -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onCity: (String) -> Unit = {},
    val onPriceMin: (String) -> Unit = {},
    val onPriceMax: (String) -> Unit = {},
    val onCategory: (ListingCategory) -> Unit = {},
    val onType: (ListingType) -> Unit = {},
    val onCondition: (ListingCondition) -> Unit = {},
    val onSort: (ListingSort) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpen: (ListingId) -> Unit = {},
)

/**
 * The market: the server's own search, filters and order over the listings that are on the market
 * now. A listing opens its page; nothing here asks for a contact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    state: MarketUiState,
    seller: String?,
    onBack: () -> Unit,
    actions: MarketActions = MarketActions(),
    filtersOpen: Boolean = false,
) {
    // The panel stays as it was through a turn of the screen; a test can start it open.
    var open by rememberSaveable { mutableStateOf(filtersOpen) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.market_title),
                subtitle = seller?.let { stringResource(R.string.market_seller_subtitle, it) },
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(state.fields.text, actions.onSearchText, actions.onClearSearch)
            Controls(
                state = state,
                open = open,
                onToggle = { open = !open },
                actions = actions,
            )
            val page = state.page
            when {
                page.loading -> LoadingGrid()
                // With the filters open there is little room under them: a state that does not
                // fit scrolls, so its button is always within reach.
                page.error != null ->
                    Scrollable {
                        ErrorState(page.error.resolve(), onRetry = actions.onRetry)
                    }
                page.isEmpty && state.query.hasFilters ->
                    Scrollable {
                        EmptyState(
                            title = stringResource(R.string.market_none_title),
                            message = stringResource(R.string.market_none),
                            icon = ColaIcons.Search,
                            actionLabel = stringResource(R.string.bikes_search_reset),
                            onAction = actions.onClearFilters,
                        )
                    }
                page.isEmpty ->
                    Scrollable {
                        EmptyState(
                            title = stringResource(R.string.market_empty_title),
                            message =
                                stringResource(
                                    if (seller != null) R.string.market_empty_seller
                                    else R.string.market_empty
                                ),
                            icon = ColaIcons.Tag,
                        )
                    }
                else ->
                    ListingList(
                        page = page,
                        onOpen = actions.onOpen,
                        onLoadMore = actions.onLoadMore,
                        onRefresh = actions.onRefresh,
                        tag = "market:list",
                    )
            }
        }
    }
}

@Composable
private fun Scrollable(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
private fun SearchField(text: String, onText: (String) -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        placeholder = { Text(stringResource(R.string.market_search_hint)) },
        leadingIcon = { Icon(painterResource(ColaIcons.Search), contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        painterResource(ColaIcons.Close),
                        contentDescription = stringResource(R.string.bikes_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = colaFieldShape,
        colors = colaTextFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.s, bottom = Spacing.s)
                .testTag("market:search"),
    )
}

/** The order always, and under one chip the filters that make the list shorter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    state: MarketUiState,
    open: Boolean,
    onToggle: () -> Unit,
    actions: MarketActions,
) {
    val query = state.query
    FlowRow(
        Modifier.padding(horizontal = Spacing.screen).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ColaFilterChip(
            selected = query.sort == ListingSort.New,
            onClick = { actions.onSort(ListingSort.New) },
            label = stringResource(R.string.market_sort_new),
        )
        ColaFilterChip(
            selected = query.sort == ListingSort.PriceAsc,
            onClick = { actions.onSort(ListingSort.PriceAsc) },
            label = stringResource(R.string.market_sort_price_asc),
        )
        ColaFilterChip(
            selected = query.sort == ListingSort.PriceDesc,
            onClick = { actions.onSort(ListingSort.PriceDesc) },
            label = stringResource(R.string.market_sort_price_desc),
        )
        val active = query.activeFilters()
        ColaFilterChip(
            selected = open || active > 0,
            onClick = onToggle,
            label =
                if (active > 0) stringResource(R.string.market_filters_active, active)
                else stringResource(R.string.market_filters),
            modifier = Modifier.testTag("market:filters"),
        )
    }
    if (open) FilterPanel(state, actions)
}

/** Category, kind and condition as chips, the price and the place as fields. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(state: MarketUiState, actions: MarketActions) {
    val query = state.query
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.selectableGroup(),
        ) {
            for ((category, label) in
                listOf(
                    ListingCategory.Bikes to R.string.market_category_bikes,
                    ListingCategory.Components to R.string.market_category_components,
                    ListingCategory.Accessories to R.string.market_category_accessories,
                )) {
                ColaFilterChip(
                    selected = query.category == category,
                    onClick = { actions.onCategory(category) },
                    label = stringResource(label),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.selectableGroup(),
        ) {
            for ((type, label) in
                listOf(
                    ListingType.Sale to R.string.market_type_sale,
                    ListingType.Wanted to R.string.market_type_wanted,
                    ListingType.Exchange to R.string.market_type_exchange,
                    ListingType.Free to R.string.market_type_free,
                )) {
                ColaFilterChip(
                    selected = query.type == type,
                    onClick = { actions.onType(type) },
                    label = stringResource(label),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.selectableGroup(),
        ) {
            ColaFilterChip(
                selected = query.condition == ListingCondition.New,
                onClick = { actions.onCondition(ListingCondition.New) },
                label = stringResource(R.string.market_condition_new),
            )
            ColaFilterChip(
                selected = query.condition == ListingCondition.Used,
                onClick = { actions.onCondition(ListingCondition.Used) },
                label = stringResource(R.string.market_condition_used),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            NumberField(
                value = state.fields.priceMin,
                label = stringResource(R.string.market_price_min),
                onChange = actions.onPriceMin,
                modifier = Modifier.weight(1f).testTag("market:price_min"),
            )
            NumberField(
                value = state.fields.priceMax,
                label = stringResource(R.string.market_price_max),
                onChange = actions.onPriceMax,
                modifier = Modifier.weight(1f).testTag("market:price_max"),
            )
        }
        val focus = LocalFocusManager.current
        OutlinedTextField(
            value = state.fields.city,
            onValueChange = actions.onCity,
            label = { Text(stringResource(R.string.market_city)) },
            singleLine = true,
            shape = colaFieldShape,
            colors = colaTextFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("market:city"),
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = colaFieldShape,
        colors = colaTextFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** How many of the filters that make the list shorter are on (text and order are not counted). */
private fun MarketQuery.activeFilters(): Int =
    listOf(category, type, condition, priceMin, priceMax, city.ifEmpty { null }).count {
        it != null
    }

/** Listings as cards, with the next page asked for near the end, a refresh and a retry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListingList(
    page: PagedState<ListingBrief>,
    onOpen: (ListingId) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    tag: String,
) {
    PullToRefreshBox(isRefreshing = page.refreshing, onRefresh = onRefresh) {
        val grid = rememberLazyGridState()
        val nearEnd by remember {
            derivedStateOf {
                (grid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0) >=
                    grid.layoutInfo.totalItemsCount - 6
            }
        }
        LaunchedEffect(grid) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(minSize = 340.dp),
            contentPadding = PaddingValues(Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            modifier = Modifier.fillMaxSize().testTag(tag),
        ) {
            page.refreshError?.let { error ->
                item(span = { GridItemSpan(maxLineSpan) }) { RetryRow(error.resolve(), onRefresh) }
            }
            items(page.items, key = { it.id }) { listing ->
                ListingCard(
                    listing,
                    onClick = { onOpen(ListingId(listing.id)) },
                    modifier = Modifier.testTag("listing:${listing.id}"),
                )
            }
            if (page.loadingMore || page.moreError != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(Spacing.l),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (page.loadingMore) CircularProgressIndicator()
                        else RetryRow(page.moreError!!.resolve(), onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryRow(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) {
            Text(stringResource(ru.colabike.core.designsystem.R.string.cola_retry))
        }
    }
}

@Composable
internal fun LoadingGrid() {
    SkeletonGroup(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            contentPadding = PaddingValues(Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            userScrollEnabled = false,
        ) {
            items(4) { BikeCardSkeleton() }
        }
    }
}

/** What the signed-in person saved and is still on the market. */
@Composable
fun SavedMarketRoute(
    repository: MarketRepository,
    onBack: () -> Unit,
    onOpen: (ListingId) -> Unit,
) {
    val viewModel = viewModel { SavedMarketViewModel(repository) }
    val page by viewModel.state.collectAsStateWithLifecycle()
    SavedMarketScreen(
        page = page,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
    )
}

@Composable
fun SavedMarketScreen(
    page: PagedState<ListingBrief>,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpen: (ListingId) -> Unit = {},
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.market_saved_title), onBack = onBack)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                page.loading -> LoadingGrid()
                page.error != null ->
                    ErrorState(
                        page.error.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                page.isEmpty ->
                    EmptyState(
                        title = stringResource(R.string.market_saved_empty_title),
                        message = stringResource(R.string.market_saved_empty),
                        icon = ColaIcons.Bookmark,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    ListingList(
                        page = page,
                        onOpen = onOpen,
                        onLoadMore = onLoadMore,
                        onRefresh = onRefresh,
                        tag = "market:saved",
                    )
            }
        }
    }
}
