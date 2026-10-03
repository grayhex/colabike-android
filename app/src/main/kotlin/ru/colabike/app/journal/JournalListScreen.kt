package ru.colabike.app.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import ru.colabike.app.R
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.JournalCard
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalSummary

@Composable
fun JournalListRoute(
    repository: JournalRepository,
    source: JournalSource,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onOpen: (JournalId) -> Unit,
) {
    val viewModel = viewModel(key = source.toString()) { JournalListViewModel(repository, source) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    JournalListScreen(
        title = title,
        subtitle = subtitle,
        saved = source == JournalSource.Saved,
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = onOpen,
    )
}

/** Journal entries one under another, newest first (the saved ones: newest saves first). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(
    title: String,
    subtitle: String?,
    saved: Boolean,
    state: PagedState<JournalSummary>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (JournalId) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = title, subtitle = subtitle, onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState(Modifier.fillMaxSize())
                state.error != null ->
                    ErrorState(
                        state.error.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                state.isEmpty ->
                    EmptyState(
                        title =
                            stringResource(
                                if (saved) R.string.journal_saved_empty_title
                                else R.string.journal_empty_title
                            ),
                        message =
                            stringResource(
                                if (saved) R.string.journal_saved_empty else R.string.journal_empty
                            ),
                        icon = ColaIcons.Journal,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        Entries(state, onRefresh, onRetry, onLoadMore, onOpen)
                    }
            }
        }
    }
}

@Composable
private fun Entries(
    state: PagedState<JournalSummary>,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (JournalId) -> Unit,
) {
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(list) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().testTag("journal:list"),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.refreshError?.let { error ->
            item { RetryRow(error.resolve(), onRefresh) }
        }
        items(state.items, key = { it.id.value }) { entry ->
            JournalCard(
                entry,
                onClick = { onOpen(entry.id) },
                modifier = Modifier.widthIn(max = 640.dp).testTag("journal:${entry.id.value}"),
            )
        }
        if (state.loadingMore || state.moreError != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(Spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loadingMore) CircularProgressIndicator()
                    else RetryRow(state.moreError!!.resolve(), onRetry)
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
