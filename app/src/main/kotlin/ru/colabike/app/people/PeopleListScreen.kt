package ru.colabike.app.people

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
import androidx.compose.material3.MaterialTheme
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
import ru.colabike.core.model.PeopleRepository

@Composable
fun PeopleListRoute(
    repository: PeopleRepository,
    ref: String,
    kind: PeopleListKind,
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val viewModel = viewModel { PeopleListViewModel(repository, ref, kind) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PeopleListScreen(
        kind = kind,
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onLoadMore = viewModel::loadMore,
        onOpenPerson = onOpenPerson,
    )
}

/** The followers of a person, or the people they follow: one row each, a tap opens the person. */
@Composable
fun PeopleListScreen(
    kind: PeopleListKind,
    state: PeopleListUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title =
                    stringResource(
                        if (kind == PeopleListKind.Followers) R.string.people_followers
                        else R.string.people_following
                    ),
                onBack = onBack,
            )
        },
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
                        title =
                            stringResource(
                                if (kind == PeopleListKind.Followers)
                                    R.string.people_no_followers_title
                                else R.string.people_no_following_title
                            ),
                        message = stringResource(R.string.people_empty),
                        icon = ColaIcons.Person,
                        modifier = Modifier.fillMaxSize(),
                    )
                else -> PeopleColumn(state, onLoadMore, onOpenPerson)
            }
        }
    }
}

@Composable
private fun PeopleColumn(
    state: PeopleListUiState,
    onLoadMore: () -> Unit,
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
        items(state.people, key = { it.person.id.value }) { summary ->
            UserRow(
                summary.person,
                modifier = Modifier.widthIn(max = 560.dp),
                onClick = { onOpenPerson(summary.person.id.value) },
            )
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
