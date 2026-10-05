package ru.colabike.app.rides

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
import ru.colabike.app.notifications.NotificationsBell
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.RideCard
import ru.colabike.core.designsystem.component.SkeletonGroup
import ru.colabike.core.designsystem.component.colaFieldShape
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideRole
import ru.colabike.core.model.RidesRepository

@Composable
fun RidesRoute(
    repository: RidesRepository,
    auth: AuthActions,
    onOpen: (RideId) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
    /** Plans whose part of the viewer changed (an answer was taken): the lists read again. */
    participationChanges: Flow<String> = emptyFlow(),
    /** "I want to ride"; null where the section has no such entry. */
    onOpenIntents: (() -> Unit)? = null,
) {
    val viewModel = viewModel {
        RidesViewModel(
            repository,
            commentChanges = commentChanges,
            participationChanges = participationChanges,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    RidesScreen(
        state = state,
        // "Mine" and "my plans" need an account; a guest sees what is public.
        personal = authState is AuthState.SignedIn,
        onSegment = viewModel::select,
        onSearchText = viewModel::onSearchText,
        onClearSearch = viewModel::clearSearch,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
        scrollToTop = scrollToTop,
        onOpenIntents = onOpenIntents,
    )
}

@Composable
fun BikeRidesRoute(
    repository: RidesRepository,
    bike: BikeId,
    bikeName: String,
    onBack: () -> Unit,
    onOpen: (RideId) -> Unit,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) {
    val viewModel =
        viewModel(key = "bike-rides:${bike.value}") {
            BikeRidesViewModel(repository, bike, commentChanges = commentChanges)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BikeRidesScreen(
        state = state,
        bikeName = bikeName,
        onBack = onBack,
        onSearchText = viewModel::onSearchText,
        onClearSearch = viewModel::clearSearch,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
    )
}

/**
 * Plans and rides in lists of their own: ahead, done, and for a member their plans and rides.
 * Search (the server's `q`) works in the two public lists. A tap on the selected Rides tab arrives
 * as [scrollToTop].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RidesScreen(
    state: RidesUiState,
    personal: Boolean,
    onSegment: (RideSegment) -> Unit,
    onSearchText: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (RideId) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
    onOpenIntents: (() -> Unit)? = null,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.rides_title),
                actions = { NotificationsBell() },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FlowRow(
                Modifier.padding(horizontal = Spacing.screen).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                RideSegment.entries
                    .filter { personal || !it.personal }
                    .forEach { segment ->
                        ColaFilterChip(
                            selected = state.segment == segment,
                            onClick = { onSegment(segment) },
                            label = stringResource(segment.label()),
                        )
                    }
                // Not a list of the section but a place of its own: "I want to ride" is a member's.
                if (personal && onOpenIntents != null) {
                    ColaFilterChip(
                        selected = false,
                        onClick = onOpenIntents,
                        label = stringResource(R.string.rides_intents),
                        modifier = Modifier.testTag("rides:intents"),
                    )
                }
            }
            if (!state.segment.personal) {
                SearchField(state.typed, onSearchText, onClearSearch)
            }
            RideList(
                state = state,
                emptyTitle = stringResource(state.segment.emptyTitle()),
                emptyMessage = stringResource(state.segment.emptyMessage()),
                onClearSearch = onClearSearch,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
                scrollToTop = scrollToTop,
            )
        }
    }
}

/** The completed public rides of one bike, with the same search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeRidesScreen(
    state: RidesUiState,
    bikeName: String,
    onBack: () -> Unit,
    onSearchText: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (RideId) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.rides_title),
                subtitle = bikeName,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(state.typed, onSearchText, onClearSearch)
            RideList(
                state = state,
                emptyTitle = stringResource(R.string.rides_empty_completed_title),
                emptyMessage = stringResource(R.string.rides_empty_completed),
                onClearSearch = onClearSearch,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
            )
        }
    }
}

private fun RideSegment.label() =
    when (this) {
        RideSegment.Upcoming -> R.string.rides_seg_upcoming
        RideSegment.Completed -> R.string.rides_seg_completed
        RideSegment.MyPlans -> R.string.rides_seg_plans
        RideSegment.Mine -> R.string.rides_seg_mine
    }

private fun RideSegment.emptyTitle() =
    when (this) {
        RideSegment.Upcoming -> R.string.rides_empty_upcoming_title
        RideSegment.Completed -> R.string.rides_empty_completed_title
        RideSegment.MyPlans -> R.string.rides_empty_plans_title
        RideSegment.Mine -> R.string.rides_empty_mine_title
    }

private fun RideSegment.emptyMessage() =
    when (this) {
        RideSegment.Upcoming -> R.string.rides_empty_upcoming
        RideSegment.Completed -> R.string.rides_empty_completed
        RideSegment.MyPlans -> R.string.rides_empty_plans
        RideSegment.Mine -> R.string.rides_empty_mine
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RideList(
    state: RidesUiState,
    emptyTitle: String,
    emptyMessage: String,
    onClearSearch: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (RideId) -> Unit,
    scrollToTop: Flow<Unit> = emptyFlow(),
) {
    val page = state.page
    when {
        page.loading -> LoadingGrid()
        page.error != null ->
            ErrorState(page.error.resolve(), onRetry = onRetry, modifier = Modifier.fillMaxSize())
        page.isEmpty && state.query.isNotEmpty() ->
            EmptyState(
                title = stringResource(R.string.bikes_search_empty_title),
                message = stringResource(R.string.rides_search_empty),
                icon = ColaIcons.Search,
                actionLabel = stringResource(R.string.bikes_search_reset),
                onAction = onClearSearch,
                modifier = Modifier.fillMaxSize(),
            )
        page.isEmpty ->
            EmptyState(
                title = emptyTitle,
                message = emptyMessage,
                icon = ColaIcons.Route,
                modifier = Modifier.fillMaxSize(),
            )
        else ->
            PullToRefreshBox(isRefreshing = page.refreshing, onRefresh = onRefresh) {
                Rides(page, onOpen, onLoadMore, onRefresh, scrollToTop)
            }
    }
}

@Composable
private fun Rides(
    page: PagedState<RideRow>,
    onOpen: (RideId) -> Unit,
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
        columns = StaggeredGridCells.Adaptive(minSize = 320.dp),
        contentPadding = PaddingValues(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalItemSpacing = Spacing.l,
        modifier = Modifier.fillMaxSize().testTag("rides:list"),
    ) {
        page.refreshError?.let { error ->
            item(span = StaggeredGridItemSpan.FullLine) { RetryRow(error.resolve(), onRefresh) }
        }
        items(page.items, key = { it.key }) { row ->
            RideRowCard(row, onOpen)
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

/** A ride card with what the list adds to it: the viewer's part in a plan, a private ride. */
@Composable
private fun RideRowCard(row: RideRow, onOpen: (RideId) -> Unit) {
    val badges = mutableListOf<String>()
    var note: String? = null
    when (row) {
        is RideRow.Public -> Unit
        is RideRow.Own -> {
            if (!row.own.isPublic) {
                badges += stringResource(R.string.rides_badge_private)
                note = stringResource(R.string.rides_note_private)
            }
        }
        is RideRow.Plan -> {
            val plan = row.plan
            badges +=
                stringResource(
                    when (plan.role) {
                        RideRole.Organizer -> R.string.rides_role_organizer
                        RideRole.Accepted -> R.string.rides_role_accepted
                        RideRole.Maybe -> R.string.rides_role_maybe
                        RideRole.Invited -> R.string.rides_role_invited
                        RideRole.Cancelled -> R.string.rides_role_cancelled
                        RideRole.Unknown -> R.string.rides_role_invited
                    }
                )
            val lines = mutableListOf<String>()
            if (plan.occurrenceCancelled) {
                badges += stringResource(R.string.rides_occurrence_cancelled)
                lines += stringResource(R.string.rides_note_series_goes_on)
            }
            if (plan.changedAfterAnswer) lines += stringResource(R.string.rides_note_changed)
            plan.meetingPoint?.let { lines += stringResource(R.string.rides_note_meeting, it) }
            if (plan.meetingHidden) lines += stringResource(R.string.rides_note_meeting_hidden)
            note = lines.joinToString(" ").ifEmpty { null }
        }
    }
    RideCard(
        row.ride,
        onClick = if (row.hasPage) ({ onOpen(row.ride.id) }) else null,
        badges = badges,
        note = note,
        modifier = Modifier.testTag("ride:${row.key}"),
    )
}

@Composable
private fun SearchField(text: String, onText: (String) -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        placeholder = { Text(stringResource(R.string.rides_search_hint)) },
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
                .padding(top = Spacing.s, bottom = Spacing.s),
    )
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
            columns = GridCells.Adaptive(minSize = 320.dp),
            contentPadding = PaddingValues(Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            userScrollEnabled = false,
        ) {
            items(3) { BikeCardSkeleton() }
        }
    }
}
