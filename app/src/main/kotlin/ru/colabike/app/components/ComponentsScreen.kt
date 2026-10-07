package ru.colabike.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaSearchField
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ComponentCard
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.SkeletonGroup
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.ComponentFilters
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.ComponentModel
import ru.colabike.core.model.ComponentSort
import ru.colabike.core.model.ComponentsRepository

@Composable
fun ComponentsRoute(
    repository: ComponentsRepository,
    onBack: () -> Unit,
    onOpen: (ComponentId) -> Unit,
) {
    val viewModel = viewModel { ComponentsViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ComponentsScreen(
        state = state,
        onBack = onBack,
        onSearchText = viewModel::onSearchText,
        onClearSearch = viewModel::clearSearch,
        onCategory = viewModel::selectCategory,
        onBrand = viewModel::selectBrand,
        onSort = viewModel::selectSort,
        onClearFilters = viewModel::clearFilters,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
    )
}

/**
 * The component catalog: the server's search, the categories and brands the server lists, and the
 * order (new or popular). A model opens its page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentsScreen(
    state: ComponentsUiState,
    onBack: () -> Unit,
    onSearchText: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onCategory: (String?) -> Unit = {},
    onBrand: (String?) -> Unit = {},
    onSort: (ComponentSort) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpen: (ComponentId) -> Unit = {},
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.components_title), onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(state.typed, onSearchText, onClearSearch)
            Filters(state, onCategory, onBrand, onSort)
            val page = state.page
            when {
                page.loading -> LoadingGrid()
                page.error != null ->
                    ErrorState(
                        page.error.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                page.isEmpty && !state.query.isDefault ->
                    EmptyState(
                        title = stringResource(R.string.components_none_title),
                        message = stringResource(R.string.components_none),
                        icon = ColaIcons.Search,
                        actionLabel = stringResource(R.string.bikes_search_reset),
                        onAction = onClearFilters,
                        modifier = Modifier.fillMaxSize(),
                    )
                page.isEmpty ->
                    EmptyState(
                        title = stringResource(R.string.components_empty_title),
                        message = stringResource(R.string.components_empty),
                        icon = ColaIcons.Build,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    PullToRefreshBox(isRefreshing = page.refreshing, onRefresh = onRefresh) {
                        Models(page, onOpen, onLoadMore, onRefresh)
                    }
            }
        }
    }
}

@Composable
private fun SearchField(text: String, onText: (String) -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    ColaSearchField(
        value = text,
        onValueChange = onText,
        placeholder = stringResource(R.string.components_search_hint),
        clearLabel = stringResource(R.string.bikes_search_clear),
        onClear = onClear,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(vertical = Spacing.s)
                .testTag("components:search"),
    )
}

/** The order, then the category and the brand as menus: their values are the server's. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(
    state: ComponentsUiState,
    onCategory: (String?) -> Unit,
    onBrand: (String?) -> Unit,
    onSort: (ComponentSort) -> Unit,
) {
    FlowRow(
        Modifier.padding(horizontal = Spacing.screen).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ColaFilterChip(
            selected = state.query.sort == ComponentSort.New,
            onClick = { onSort(ComponentSort.New) },
            label = stringResource(R.string.components_sort_new),
        )
        ColaFilterChip(
            selected = state.query.sort == ComponentSort.Popular,
            onClick = { onSort(ComponentSort.Popular) },
            label = stringResource(R.string.components_sort_popular),
        )
        val filters: ComponentFilters? = state.filters
        if (filters != null) {
            if (filters.categories.isNotEmpty()) {
                FilterMenu(
                    title = stringResource(R.string.components_category),
                    chosen = state.query.category,
                    options = filters.categories,
                    onChoose = onCategory,
                    tag = "components:category",
                )
            }
            if (filters.brands.isNotEmpty()) {
                FilterMenu(
                    title = stringResource(R.string.components_brand),
                    chosen = state.query.brand,
                    options = filters.brands,
                    onChoose = onBrand,
                    tag = "components:brand",
                )
            }
        }
    }
}

/** A chip that opens the list of values; the chosen value is on the chip, "All" lifts it. */
@Composable
private fun FilterMenu(
    title: String,
    chosen: String?,
    options: List<String>,
    onChoose: (String?) -> Unit,
    tag: String,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ColaFilterChip(
            selected = chosen != null,
            onClick = { open = true },
            label = chosen ?: title,
            modifier = Modifier.testTag(tag),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.components_filter_all)) },
                onClick = {
                    open = false
                    if (chosen != null) onChoose(chosen)
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        open = false
                        // Choosing the chosen one again lifts it, in the ViewModel.
                        onChoose(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun Models(
    page: PagedState<ComponentModel>,
    onOpen: (ComponentId) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val grid = rememberLazyStaggeredGridState()
    val nearEnd by remember {
        derivedStateOf {
            (grid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0) >=
                grid.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(grid) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }
    LazyVerticalStaggeredGrid(
        state = grid,
        columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalItemSpacing = Spacing.l,
        modifier = Modifier.fillMaxSize().testTag("components:list"),
    ) {
        page.refreshError?.let { error ->
            item(span = StaggeredGridItemSpan.FullLine) { RetryRow(error.resolve(), onRefresh) }
        }
        items(page.items, key = { it.id.value }) { model ->
            ComponentCard(
                name = model.name,
                brand = model.brand,
                category = model.category,
                builds = model.builds,
                coverUrl = model.coverUrl,
                archived = model.archived,
                onClick = { onOpen(model.id) },
                modifier = Modifier.testTag("component:${model.id.value}"),
            )
        }
        if (page.loadingMore || page.moreError != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
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
private fun LoadingGrid() {
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
