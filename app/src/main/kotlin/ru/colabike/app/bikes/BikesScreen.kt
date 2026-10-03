package ru.colabike.app.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikesRepository

@Composable
fun BikesRoute(
    repository: BikesRepository,
    auth: AuthActions,
    onOpen: (BikeId) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    val viewModel = viewModel { BikesViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    BikesScreen(
        state = state,
        // "Mine" needs an account; a guest sees what is public and nothing to choose between.
        showScopes = authState is AuthState.SignedIn,
        onScope = viewModel::selectScope,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
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
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (BikeId) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.bikes_title)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
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
            when {
                state.loading -> LoadingGrid()
                state.error != null ->
                    ErrorState(
                        state.error.resolve(),
                        onRetry = onRetry,
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
