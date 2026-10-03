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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.SkeletonGroup
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikesRepository

@Composable
fun BikesRoute(repository: BikesRepository, onOpen: (BikeId) -> Unit) {
    val viewModel = viewModel { BikesViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BikesScreen(
        state = state,
        onScope = viewModel::selectScope,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
    )
}

/** Bikes as photo-first cards; as many columns as the pane width holds at 280 dp each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikesScreen(
    state: BikesUiState,
    onScope: (BikeScope) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (BikeId) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.bikes_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                FilterChip(
                    selected = state.scope == BikeScope.Public,
                    onClick = { onScope(BikeScope.Public) },
                    label = { Text(stringResource(R.string.bikes_scope_public)) },
                )
                FilterChip(
                    selected = state.scope == BikeScope.Mine,
                    onClick = { onScope(BikeScope.Mine) },
                    label = { Text(stringResource(R.string.bikes_scope_mine)) },
                )
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
                        title = stringResource(R.string.bikes_empty_title),
                        message =
                            stringResource(
                                if (state.scope == BikeScope.Mine) R.string.bikes_empty_mine
                                else R.string.bikes_empty_public
                            ),
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        BikeGrid(state, onOpen, onLoadMore)
                    }
            }
        }
    }
}

@Composable
private fun BikeGrid(state: BikesUiState, onOpen: (BikeId) -> Unit, onLoadMore: () -> Unit) {
    val grid = rememberLazyGridState()
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
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.bikes, key = { it.id.value }) { bike ->
            BikeCard(bike, onClick = { onOpen(bike.id) })
        }
        if (state.loadingMore || state.moreError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(Spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loadingMore) CircularProgressIndicator()
                    else
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.moreError!!.resolve(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onLoadMore) {
                                Text(
                                    stringResource(
                                        ru.colabike.core.designsystem.R.string.cola_retry
                                    )
                                )
                            }
                        }
                }
            }
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
