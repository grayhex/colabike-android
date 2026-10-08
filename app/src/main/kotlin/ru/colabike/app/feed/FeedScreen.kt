package ru.colabike.app.feed

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
import ru.colabike.app.rides.LocalRidePreviewEntry
import ru.colabike.app.rides.PreviewRideCard
import ru.colabike.app.ui.HeaderActions
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.JournalCard
import ru.colabike.core.designsystem.component.ListingCard
import ru.colabike.core.designsystem.component.SkeletonGroup
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.FeedRepository
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.RideId

/** Where the feed leads. Callbacks, so the screen never touches navigation itself. */
class FeedActions(
    val onOpenBike: (BikeId) -> Unit,
    val onOpenJournal: (JournalId) -> Unit,
    val onOpenRide: (RideId) -> Unit,
    val onFindPeople: () -> Unit,
    val onBrowseBikes: () -> Unit,
    val onOpenListing: (ListingId) -> Unit = {},
)

@Composable
fun FeedRoute(
    feed: FeedRepository,
    bikes: BikesRepository,
    auth: AuthActions,
    actions: FeedActions,
    scrollToTop: Flow<Unit> = emptyFlow(),
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) {
    val authState by auth.state.collectAsStateWithLifecycle()
    if (authState is AuthState.SignedIn) {
        val viewModel = viewModel { FeedViewModel(feed, bikes, commentChanges) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        FeedScreen(
            state = state,
            actions = actions,
            onFilter = viewModel::select,
            onRefresh =
                run {
                    val previews = LocalRidePreviewEntry.current?.model
                    {
                        previews?.refresh()
                        viewModel.refresh()
                    }
                },
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            scrollToTop = scrollToTop,
        )
    } else {
        // The feed is personal: a guest has none, and nothing is asked of the server for them.
        FeedGuestScreen(onSignIn = LocalSignInRequest.current, onBrowse = actions.onBrowseBikes)
    }
}

@Composable
fun FeedGuestScreen(onSignIn: () -> Unit, onBrowse: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.feed_title),
                actions = { HeaderActions() },
            )
        },
    ) { padding ->
        EmptyState(
            title = stringResource(R.string.feed_guest_title),
            message = stringResource(R.string.feed_guest_message),
            icon = ColaIcons.Feed,
            actionLabel = stringResource(R.string.feed_sign_in),
            onAction = onSignIn,
            secondaryLabel = stringResource(R.string.feed_browse),
            onSecondary = onBrowse,
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}

/**
 * What the people and bikes the viewer follows published: bikes, journal entries, rides and market
 * listings in one stream, newest first, with the server's own filter on top. A tap on the selected
 * Feed tab arrives as [scrollToTop].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    state: FeedUiState,
    actions: FeedActions,
    onFilter: (FeedFilter) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    val page = state.page
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.feed_title),
                actions = { HeaderActions() },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FlowRow(
                Modifier.padding(horizontal = Spacing.screen).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FeedFilters.forEach { (filter, label) ->
                    ColaFilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilter(filter) },
                        label = stringResource(label),
                    )
                }
            }
            when {
                page.loading -> LoadingGrid()
                page.error != null ->
                    ErrorState(
                        page.error.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                page.isEmpty -> Empty(state.filter, actions)
                else ->
                    PullToRefreshBox(isRefreshing = page.refreshing, onRefresh = onRefresh) {
                        FeedGrid(page, actions, onLoadMore, onRefresh, scrollToTop)
                    }
            }
        }
    }
}

private val FeedFilters =
    listOf(
        FeedFilter.All to R.string.feed_filter_all,
        FeedFilter.Rides to R.string.feed_filter_rides,
        FeedFilter.Journal to R.string.feed_filter_journal,
    )

@Composable
private fun Empty(filter: FeedFilter, actions: FeedActions) {
    when (filter) {
        FeedFilter.All ->
            EmptyState(
                title = stringResource(R.string.feed_empty_title),
                message = stringResource(R.string.feed_empty_message),
                icon = ColaIcons.Feed,
                actionLabel = stringResource(R.string.feed_find_people),
                onAction = actions.onFindPeople,
                secondaryLabel = stringResource(R.string.feed_browse),
                onSecondary = actions.onBrowseBikes,
                modifier = Modifier.fillMaxSize(),
            )
        else ->
            EmptyState(
                title =
                    stringResource(
                        if (filter == FeedFilter.Rides) R.string.feed_empty_rides_title
                        else R.string.feed_empty_journal_title
                    ),
                message = stringResource(R.string.feed_empty_filtered),
                icon = if (filter == FeedFilter.Rides) ColaIcons.Route else ColaIcons.Journal,
                modifier = Modifier.fillMaxSize(),
            )
    }
}

@Composable
private fun FeedGrid(
    page: PagedState<FeedItem>,
    actions: FeedActions,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    scrollToTop: Flow<Unit>,
) {
    val grid = rememberLazyStaggeredGridState()
    LaunchedEffect(scrollToTop, grid) { scrollToTop.collect { grid.animateScrollToItem(0) } }
    val nearEnd by remember {
        derivedStateOf {
            (grid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0) >=
                grid.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(grid) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }
    LazyVerticalStaggeredGrid(
        state = grid,
        columns = StaggeredGridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalItemSpacing = Spacing.l,
        modifier = Modifier.fillMaxSize().testTag("feed:grid"),
    ) {
        page.refreshError?.let { error ->
            item(span = StaggeredGridItemSpan.FullLine) { RetryRow(error.resolve(), onRefresh) }
        }
        items(page.items, key = { it.key }) { item ->
            when (item) {
                is FeedItem.Bike ->
                    BikeCard(
                        item.bike,
                        authorFirst = true,
                        publishedAt = item.publishedAt,
                        onClick = { actions.onOpenBike(item.bike.id) },
                        modifier = Modifier.testTag("bike:${item.bike.id.value}"),
                    )
                is FeedItem.Journal ->
                    JournalCard(
                        item.entry,
                        onClick = { actions.onOpenJournal(item.entry.id) },
                        modifier = Modifier.testTag("journal:${item.entry.id.value}"),
                    )
                is FeedItem.Ride ->
                    PreviewRideCard(
                        item.ride,
                        onClick = { actions.onOpenRide(item.ride.id) },
                        modifier = Modifier.testTag("ride:${item.ride.id.value}"),
                    )
                is FeedItem.Listing ->
                    ListingCard(
                        item.listing,
                        onClick = { actions.onOpenListing(ListingId(item.listing.id)) },
                        modifier = Modifier.testTag("listing:${item.listing.id}"),
                    )
            }
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
