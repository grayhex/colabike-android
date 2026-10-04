package ru.colabike.app.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.SkeletonGroup
import ru.colabike.core.designsystem.component.colaFieldShape
import ru.colabike.core.designsystem.component.colaTextFieldColors
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
        onClearFilters = viewModel::clearFilters,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
        onOpenSearch = onSearch,
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
    onClearFilters: () -> Unit = {},
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (BikeId) -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.bikes_title),
                actions = {
                    // The wide search: builds by components and facets, and people.
                    if (onOpenSearch != null) {
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                painterResource(ColaIcons.Search),
                                contentDescription = stringResource(R.string.search_open),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(state.typed, onSearchText)
            if (showScopes) {
                Row(
                    Modifier.padding(horizontal = Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    ColaFilterChip(
                        selected = state.scope == BikeScope.Public,
                        onClick = { onScope(BikeScope.Public) },
                        label = stringResource(R.string.bikes_scope_public),
                    )
                    ColaFilterChip(
                        selected = state.scope == BikeScope.Mine,
                        onClick = { onScope(BikeScope.Mine) },
                        label = stringResource(R.string.bikes_scope_mine),
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(BikeLabels.categories, key = { it.first }) { (key, label) ->
                    ColaFilterChip(
                        selected = key in state.query.categories,
                        onClick = { onToggleCategory(key) },
                        label = label,
                    )
                }
            }
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
        contentPadding = PaddingValues(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
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

/**
 * The search line: what is typed is shown at once, the request follows a pause (the view model).
 * The keyboard's search key only hides the keyboard, since the list already follows the text.
 */
@Composable
private fun SearchField(text: String, onText: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        placeholder = { Text(stringResource(R.string.bikes_search_hint)) },
        leadingIcon = { Icon(painterResource(ColaIcons.Search), contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onText("") }) {
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
                .padding(bottom = Spacing.s),
    )
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
            contentPadding = PaddingValues(Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            userScrollEnabled = false,
        ) {
            items(4) { BikeCardSkeleton() }
        }
    }
}
