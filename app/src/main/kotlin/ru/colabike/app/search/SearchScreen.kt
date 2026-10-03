package ru.colabike.app.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import ru.colabike.app.R
import ru.colabike.app.bikes.BikeLabels
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.component.colaFieldShape
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.PeopleRepository

@Composable
fun SearchRoute(
    bikes: BikesRepository,
    people: PeopleRepository,
    onBack: () -> Unit,
    onOpenBike: (BikeId) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val viewModel = viewModel { SearchViewModel(bikes, people) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onBack = onBack,
        onTab = viewModel::selectTab,
        onText = viewModel::onText,
        onClear = viewModel::clearText,
        onCategory = viewModel::selectCategory,
        onSuspension = viewModel::selectSuspension,
        onElectric = viewModel::toggleElectric,
        onFatbike = viewModel::toggleFatbike,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpenBike = onOpenBike,
        onOpenPerson = onOpenPerson,
    )
}

/** The suspensions the site lists, with the words it uses (cola lib/bike-classification.ts). */
private val Suspensions =
    listOf("rigid" to "Rigid", "hardtail" to "Hardtail", "full_suspension" to "Full Suspension")

/**
 * One box, two searches: builds (text over names, brands, models and components, with the facets
 * the API has) and people (text only). The field is focused when the screen opens; the tabs keep
 * their own results.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onTab: (SearchTab) -> Unit,
    onText: (String) -> Unit,
    onClear: () -> Unit,
    onCategory: (String) -> Unit,
    onSuspension: (String) -> Unit,
    onElectric: () -> Unit,
    onFatbike: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenBike: (BikeId) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.search_title), onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Field(state.typed, state.tab, onText, onClear)
            Row(
                Modifier.padding(horizontal = Spacing.screen).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                ColaFilterChip(
                    selected = state.tab == SearchTab.Bikes,
                    onClick = { onTab(SearchTab.Bikes) },
                    label = stringResource(R.string.search_tab_bikes),
                )
                ColaFilterChip(
                    selected = state.tab == SearchTab.People,
                    onClick = { onTab(SearchTab.People) },
                    label = stringResource(R.string.search_tab_people),
                )
            }
            if (state.tab == SearchTab.Bikes) {
                Facets(state, onCategory, onSuspension, onElectric, onFatbike)
            }
            when (state.tab) {
                SearchTab.Bikes -> BikeResults(state.bikes, onRetry, onLoadMore, onOpenBike)
                SearchTab.People -> PeopleResults(state.people, onRetry, onLoadMore, onOpenPerson)
            }
        }
    }
}

@Composable
private fun Field(text: String, tab: SearchTab, onText: (String) -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        placeholder = {
            Text(
                stringResource(
                    if (tab == SearchTab.Bikes) R.string.search_hint_bikes
                    else R.string.search_hint_people
                )
            )
        },
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
                .padding(bottom = Spacing.s)
                .focusRequester(requester),
    )
}

@Composable
private fun Facets(
    state: SearchUiState,
    onCategory: (String) -> Unit,
    onSuspension: (String) -> Unit,
    onElectric: () -> Unit,
    onFatbike: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.testTag("search:facets"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(BikeLabels.categories, key = { "c-" + it.first }) { (key, label) ->
            ColaFilterChip(state.category == key, { onCategory(key) }, label)
        }
        items(Suspensions, key = { "s-" + it.first }) { (key, label) ->
            ColaFilterChip(state.suspension == key, { onSuspension(key) }, label)
        }
        item(key = "electric") {
            ColaFilterChip(state.electric, onElectric, stringResource(R.string.search_electric))
        }
        item(key = "fatbike") {
            ColaFilterChip(state.fatbike, onFatbike, stringResource(R.string.search_fatbike))
        }
    }
}

@Composable
private fun BikeResults(
    results: Results<ru.colabike.core.model.BikeSummary>,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenBike: (BikeId) -> Unit,
) {
    when {
        !results.asked ->
            EmptyState(
                title = stringResource(R.string.search_start_title),
                message = stringResource(R.string.search_start_bikes),
                icon = ColaIcons.Search,
                modifier = Modifier.fillMaxSize(),
            )
        results.loading -> Searching()
        results.error != null ->
            ErrorState(
                results.error.resolve(),
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
        results.items.isEmpty() ->
            EmptyState(
                title = stringResource(R.string.bikes_search_empty_title),
                message = stringResource(R.string.bikes_search_empty),
                icon = ColaIcons.Search,
                modifier = Modifier.fillMaxSize(),
            )
        else -> {
            val grid = rememberLazyGridState()
            val nearEnd by remember {
                derivedStateOf {
                    (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                        grid.layoutInfo.totalItemsCount - 4
                }
            }
            LaunchedEffect(grid) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(minSize = 280.dp),
                contentPadding = PaddingValues(Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
                modifier = Modifier.fillMaxSize().testTag("search:bikes"),
            ) {
                items(results.items, key = { it.id.value }) { bike ->
                    BikeCard(
                        bike,
                        onClick = { onOpenBike(bike.id) },
                        modifier = Modifier.testTag("bike:${bike.id.value}"),
                    )
                }
                if (results.loadingMore || results.moreError != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MoreFooter(results.loadingMore, results.moreError?.resolve(), onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleResults(
    results: Results<ru.colabike.core.model.PersonSummary>,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    when {
        !results.asked ->
            EmptyState(
                title = stringResource(R.string.search_start_title),
                message = stringResource(R.string.search_start_people),
                icon = ColaIcons.Person,
                modifier = Modifier.fillMaxSize(),
            )
        results.loading -> Searching()
        results.error != null ->
            ErrorState(
                results.error.resolve(),
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )
        results.items.isEmpty() ->
            EmptyState(
                title = stringResource(R.string.search_people_empty_title),
                message = stringResource(R.string.search_people_empty),
                icon = ColaIcons.Person,
                modifier = Modifier.fillMaxSize(),
            )
        else -> {
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
                modifier = Modifier.fillMaxSize().testTag("search:people"),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(results.items, key = { it.person.id.value }) { summary ->
                    UserRow(
                        summary.person,
                        modifier = Modifier.widthIn(max = 560.dp),
                        onClick = { onOpenPerson(summary.person.id.value) },
                    )
                }
                if (results.loadingMore || results.moreError != null) {
                    item {
                        MoreFooter(results.loadingMore, results.moreError?.resolve(), onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun Searching() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun MoreFooter(loading: Boolean, error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(Spacing.l), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Column(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(ru.colabike.core.designsystem.R.string.cola_retry))
                }
            }
        }
    }
}
