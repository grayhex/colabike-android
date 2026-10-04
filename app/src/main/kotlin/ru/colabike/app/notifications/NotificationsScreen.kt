package ru.colabike.app.notifications

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import ru.colabike.app.R
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.navigation.Destination
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.NotificationRow
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.ListingState
import ru.colabike.core.model.NotificationsRepository

@Composable
fun NotificationsRoute(
    repository: NotificationsRepository,
    site: SiteLinks,
    onBack: () -> Unit,
    onOpen: (Destination) -> Unit,
) {
    val viewModel = viewModel { NotificationsViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val links = LocalLinkOpener.current
    val routes = remember(state.items, site) { state.items.associate { it.id to it.route(site) } }
    NotificationsScreen(
        state = state,
        opens = {
            routes[it.id].let { route -> route != null && route != NotificationRoute.Nowhere }
        },
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpen = { notification ->
            when (val route = routes[notification.id]) {
                is NotificationRoute.InApp -> onOpen(route.destination)
                is NotificationRoute.OnSite -> links.open(route.url)
                NotificationRoute.Nowhere,
                null -> Unit
            }
        },
    )
}

/**
 * The inbox of ColaBike events: who did what to which object. The server's unread state is shown as
 * it is; the screen does not mark anything as read, because API v1 has no such operation, and says
 * so once instead of pretending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: PagedState<AppNotification>,
    opens: (AppNotification) -> Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (AppNotification) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.notifications_title), onBack = onBack)
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
                state.isEmpty ->
                    EmptyState(
                        title = stringResource(R.string.notifications_empty_title),
                        message = stringResource(R.string.notifications_empty),
                        icon = ColaIcons.Notifications,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                        Inbox(state, opens, onRefresh, onRetry, onLoadMore, onOpen)
                    }
            }
        }
    }
}

@Composable
private fun Inbox(
    state: PagedState<AppNotification>,
    opens: (AppNotification) -> Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (AppNotification) -> Unit,
) {
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(list) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }
    val locale = LocalConfiguration.current.locales[0]
    val formatter =
        remember(locale) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
        }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().testTag("notifications:list"),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.refreshError?.let { error -> item { RetryRow(error.resolve(), onRefresh) } }
        item {
            Text(
                stringResource(R.string.notifications_not_read_here),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
            )
        }
        items(state.items, key = { it.id }) { notification ->
            val (title, body) = notification.text(locale, formatter)
            NotificationRow(
                title = title,
                body = body,
                whenText = formatter.format(notification.createdAt),
                unread = !notification.read,
                actor = notification.actor,
                onClick = if (opens(notification)) ({ onOpen(notification) }) else null,
                modifier =
                    Modifier.widthIn(max = ContentWidth).testTag("notification:${notification.id}"),
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

/**
 * The title and the line under it, for the kinds the app knows; anything else gets a general one.
 */
@Composable
private fun AppNotification.text(
    locale: Locale,
    formatter: DateTimeFormatter,
): Pair<String, String> {
    val who = actor?.displayName
    val about = listOfNotNull(who, target.name.takeIf { it.isNotBlank() }).joinToString(" · ")
    return when {
        kind == "follow" ->
            stringResource(R.string.notifications_follow) to
                (actor?.let { "${it.displayName} (@${it.username})" } ?: target.name)
        kind == "like" || kind.endsWith("_like") ->
            stringResource(R.string.notifications_like) to about
        kind == "comment" || kind.endsWith("_comment") ->
            stringResource(R.string.notifications_comment) to about
        kind == "reply" || kind.endsWith("_reply") ->
            stringResource(R.string.notifications_reply) to about
        kind == "market_expiring" -> {
            val state =
                stringResource(
                    when (target.state) {
                        ListingState.Closed -> R.string.notifications_market_closed
                        ListingState.Expired -> R.string.notifications_market_expired
                        ListingState.Extended -> R.string.notifications_market_extended
                        ListingState.Expiring,
                        ListingState.Unknown,
                        null -> R.string.notifications_market_expiring
                    }
                )
            val until =
                target.expiresAt
                    ?.takeIf { target.state == ListingState.Expiring }
                    ?.let {
                        stringResource(R.string.notifications_market_until, formatter.format(it))
                    }
            stringResource(R.string.notifications_market) to
                listOfNotNull(target.name.takeIf { it.isNotBlank() }, state, until)
                    .joinToString(". ")
        }
        kind == "session_reuse" ->
            stringResource(R.string.notifications_session) to
                stringResource(R.string.notifications_session_body)
        kind == "bike_week" -> stringResource(R.string.notifications_bike_week) to target.name
        else -> stringResource(R.string.notifications_generic) to about
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

private val ContentWidth = 640.dp
