package ru.colabike.app.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.HeaderActions
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.ColaDropdownChip
import ru.colabike.core.designsystem.component.ColaDropdownItem
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaSearchField
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.SkeletonGroup
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.CommentCountChange

@Composable
fun BikesRoute(
    repository: BikesRepository,
    auth: AuthActions,
    onOpen: (BikeId) -> Unit,
    onSearch: () -> Unit = {},
    onOpenCatalog: (() -> Unit)? = null,
    onCreate: (() -> Unit)? = null,
    scrollToTop: Flow<Unit> = emptyFlow(),
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) {
    val viewModel = viewModel { BikesViewModel(repository, commentChanges = commentChanges) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    BikesScreen(
        state = state,
        // "Mine" needs an account; a guest sees what is public and nothing to choose between.
        showScopes = authState is AuthState.SignedIn,
        onScope = viewModel::selectScope,
        onSearchText = viewModel::onSearchText,
        onToggleCategory = viewModel::toggleCategory,
        onClearCategories = viewModel::clearCategories,
        onClearFilters = viewModel::clearFilters,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
        onOpenSearch = onSearch,
        onOpenCatalog = onOpenCatalog,
        // A new bike is the signed-in person's own: a guest has no garage to add it to.
        onCreate = if (authState is AuthState.SignedIn) onCreate else null,
        scrollToTop = scrollToTop,
    )
}

/**
 * Bikes as photo-first cards; as many columns as the pane width holds at 280 dp each. A tap on the
 * already selected Bikes tab arrives as [scrollToTop].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikesScreen(
    state: BikesUiState,
    showScopes: Boolean = true,
    onScope: (BikeScope) -> Unit,
    onSearchText: (String) -> Unit = {},
    onToggleCategory: (String) -> Unit = {},
    onClearCategories: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (BikeId) -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    onOpenCatalog: (() -> Unit)? = null,
    onCreate: (() -> Unit)? = null,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            BoxWithConstraints {
                ColaTopBar(
                    title = stringResource(R.string.bikes_title),
                    // A narrow list pane needs the same room for actions as enlarged text.
                    actionsBelow = LocalDensity.current.fontScale >= LargeFont || maxWidth < 360.dp,
                    actions = {
                        // The catalog of component models, apart from the bikes that carry them.
                        if (onOpenCatalog != null) {
                            IconButton(onClick = onOpenCatalog) {
                                Icon(
                                    painterResource(ColaIcons.Build),
                                    contentDescription = stringResource(R.string.components_open),
                                )
                            }
                        }
                        HeaderActions()
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // The wide search (builds by components and facets, people) is the button at the end
            // of the line, where the reference has its filters.
            ColaSearchField(
                value = state.typed,
                onValueChange = onSearchText,
                placeholder = stringResource(R.string.bikes_search_hint),
                clearLabel = stringResource(R.string.bikes_search_clear),
                filtersLabel = onOpenSearch?.let { stringResource(R.string.search_open) },
                onFilters = onOpenSearch,
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
            FilterRow(
                state = state,
                showScopes = showScopes,
                onScope = onScope,
                onToggleCategory = onToggleCategory,
                onClearCategories = onClearCategories,
                onCreate = onCreate,
            )
            when {
                state.loading -> LoadingGrid()
                state.error != null ->
                    ErrorState(
                        state.error.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                state.bikes.isEmpty() && state.query.isFiltered ->
                    EmptyState(
                        title = stringResource(R.string.bikes_search_empty_title),
                        message = stringResource(R.string.bikes_search_empty),
                        icon = ColaIcons.Search,
                        actionLabel = stringResource(R.string.bikes_search_reset),
                        onAction = onClearFilters,
                        modifier = Modifier.fillMaxSize(),
                    )
                state.bikes.isEmpty() ->
                    EmptyState(
                        title =
                            stringResource(
                                if (state.scope == BikeScope.Mine) R.string.bikes_empty_mine_title
                                else R.string.bikes_empty_public_title
                            ),
                        message =
                            stringResource(
                                if (state.scope == BikeScope.Mine) R.string.bikes_empty_mine
                                else R.string.bikes_empty_public
                            ),
                        icon = ColaIcons.Bike,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        BikeGrid(state, onOpen, onLoadMore, onRefresh, scrollToTop)
                    }
            }
        }
    }
}

/**
 * The line under the search, one line on a phone: who the bikes are (everyone's or the person's
 * own), which categories, and "Add". The first two are lists that open on a tap and show what is
 * chosen; the categories may be several, and "All categories" clears them. "Add" is an action in
 * the accent colour, not a filter. The categories take the room that is left, so what is chosen is
 * as whole as the screen allows; with a large font the three do not fit side by side and go to as
 * many lines as they need, rather than one of them being squeezed out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    state: BikesUiState,
    showScopes: Boolean,
    onScope: (BikeScope) -> Unit,
    onToggleCategory: (String) -> Unit,
    onClearCategories: () -> Unit,
    onCreate: (() -> Unit)?,
) {
    val scope: @Composable () -> Unit = {
        if (showScopes) ScopeFilter(state.scope, onScope)
    }
    val categories: @Composable (Modifier, Boolean) -> Unit = { modifier, tight ->
        CategoryFilter(state.query.categories, tight, onToggleCategory, onClearCategories, modifier)
    }
    val add: @Composable () -> Unit = { if (onCreate != null) AddBikeButton(onCreate) }
    val large = LocalDensity.current.fontScale >= LargeFont
    if (large)
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            scope()
            categories(Modifier, false)
            add()
        }
    else
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            scope()
            // On a narrow screen "All categories" does not fit beside the other two: its short
            // form.
            BoxWithConstraints(Modifier.weight(1f)) { categories(Modifier, maxWidth < TightFilter) }
            add()
        }
}

@Composable
private fun ScopeFilter(scope: BikeScope, onScope: (BikeScope) -> Unit) {
    val everyone = stringResource(R.string.bikes_scope_public)
    val mine = stringResource(R.string.bikes_scope_mine)
    val current = if (scope == BikeScope.Mine) mine else everyone
    ColaDropdownChip(
        label = current,
        active = scope == BikeScope.Mine,
        description = stringResource(R.string.bikes_scope_filter, current),
        modifier = Modifier.testTag("bikes:scope"),
    ) { close ->
        ColaDropdownItem(everyone, scope == BikeScope.Public) {
            onScope(BikeScope.Public)
            close()
        }
        ColaDropdownItem(mine, scope == BikeScope.Mine) {
            onScope(BikeScope.Mine)
            close()
        }
    }
}

@Composable
private fun CategoryFilter(
    categories: Set<String>,
    tight: Boolean,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = BikeLabels.categories.filter { it.first in categories }
    val all = stringResource(R.string.bikes_category_all)
    val label =
        when (chosen.size) {
            0 -> if (tight) stringResource(R.string.bikes_category_all_short) else all
            1 -> chosen.first().second
            else ->
                stringResource(R.string.bikes_category_more, chosen.first().second, chosen.size - 1)
        }
    ColaDropdownChip(
        label = label,
        active = chosen.isNotEmpty(),
        description =
            stringResource(R.string.bikes_category_filter, if (chosen.isEmpty()) all else label),
        modifier = modifier.testTag("bikes:category"),
    ) { close ->
        ColaDropdownItem(all, chosen.isEmpty()) {
            onClear()
            close()
        }
        // Several can be chosen: the list stays open until the person is done.
        BikeLabels.categories.forEach { (key, name) ->
            ColaDropdownItem(name, key in categories) { onToggle(key) }
        }
    }
}

/** A new bike of one's own: an action, in the accent colour, not a filter. */
@Composable
private fun AddBikeButton(onCreate: () -> Unit) {
    // TalkBack says the whole thing; the screen has room for one word.
    val addLabel = stringResource(R.string.bike_add)
    TextButton(
        onClick = onCreate,
        modifier = Modifier.testTag("bikes:add").semantics { contentDescription = addLabel },
        contentPadding = PaddingValues(horizontal = Spacing.s),
    ) {
        Icon(
            painterResource(ColaIcons.Add),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(R.string.bike_add_short),
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** The room under which the category filter says "Categories" instead of "All categories". */
private val TightFilter = 150.dp

/** From this font scale on the buttons of the header go under the title. */
private const val LargeFont = 1.3f

@Composable
private fun BikeGrid(
    state: BikesUiState,
    onOpen: (BikeId) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    scrollToTop: Flow<Unit>,
) {
    val grid = rememberLazyGridState()
    LaunchedEffect(scrollToTop, grid) { scrollToTop.collect { grid.animateScrollToItem(0) } }
    // The grid keeps the card it shows at the top when the list around it changes, so the result of
    // a new search would open scrolled past its own first cards. A different first card means a
    // different result: it is read from the top. Coming back to a list that was left, `shown`
    // starts at what the list is now, and the scroll stays where it was.
    val first = state.bikes.firstOrNull()?.id
    var shown by remember { mutableStateOf(first) }
    LaunchedEffect(first) {
        if (first != shown) {
            shown = first
            grid.scrollToItem(0)
        }
    }
    val nearEnd by remember {
        derivedStateOf {
            (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                grid.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(grid) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxSize().testTag("bikes:grid"),
    ) {
        state.refreshError?.let { error ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                RetryRow(error.resolve(), onRetry = onRefresh)
            }
        }
        items(state.bikes, key = { it.id.value }) { bike ->
            BikeCard(
                bike,
                onClick = { onOpen(bike.id) },
                // Found by the live smoke test (app/src/androidTest).
                modifier = Modifier.testTag("bike:${bike.id.value}"),
            )
        }
        if (state.loadingMore || state.moreError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(Spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loadingMore) CircularProgressIndicator()
                    else RetryRow(state.moreError!!.resolve(), onRetry = onLoadMore)
                }
            }
        }
    }
}

/** A failure inside the list: what went wrong and a retry of exactly that. */
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
            columns = GridCells.Adaptive(minSize = 280.dp),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            userScrollEnabled = false,
        ) {
            items(4) { BikeCardSkeleton() }
        }
    }
}
