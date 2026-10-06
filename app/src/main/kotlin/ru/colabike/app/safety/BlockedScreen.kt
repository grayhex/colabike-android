package ru.colabike.app.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.SafetyRepository
import ru.colabike.core.model.UserId

@Composable
fun BlockedRoute(safety: SafetyRepository, onBack: () -> Unit, onOpenPerson: (String) -> Unit) {
    val viewModel = viewModel { BlockedViewModel(safety) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BlockedScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onLoadMore = viewModel::loadMore,
        onUnblock = viewModel::unblock,
        onOpenPerson = onOpenPerson,
    )
}

/**
 * The people the viewer blocked. Each row opens the person and has its own "unblock"; the list says
 * what blocking does, because nobody else will tell the person it was done.
 */
@Composable
fun BlockedScreen(
    state: BlockedUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onUnblock: (UserId) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.blocked_title), onBack = onBack) },
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
                state.people.isEmpty() ->
                    EmptyState(
                        title = stringResource(R.string.blocked_empty_title),
                        message = stringResource(R.string.blocked_empty_message),
                        icon = ColaIcons.Block,
                        modifier = Modifier.fillMaxSize(),
                    )
                else -> BlockedColumn(state, onLoadMore, onUnblock, onOpenPerson)
            }
        }
    }
}

@Composable
private fun BlockedColumn(
    state: BlockedUiState,
    onLoadMore: () -> Unit,
    onUnblock: (UserId) -> Unit,
    onOpenPerson: (String) -> Unit,
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    stringResource(R.string.blocked_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Announced when it appears: an unblocking that did not go through.
                state.notice?.let {
                    Text(
                        it.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        items(state.people, key = { it.person.id.value }) { summary ->
            val name = summary.person.displayName
            val unblockLabel = stringResource(R.string.blocked_unblock_named, name)
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                UserRow(
                    summary.person,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenPerson(summary.person.id.value) },
                )
                OutlinedButton(
                    onClick = { onUnblock(summary.person.id) },
                    enabled = summary.person.id.value !in state.unblocking,
                    modifier =
                        Modifier.heightIn(min = Spacing.touch).semantics {
                            contentDescription = unblockLabel
                        },
                ) {
                    Text(stringResource(R.string.safety_unblock))
                }
            }
        }
        if (state.loadingMore || state.moreError != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(Spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loadingMore) {
                        CircularProgressIndicator()
                    } else {
                        Column(
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
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
}
