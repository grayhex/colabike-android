package ru.colabike.app.notifications

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.semantics.contentDescription
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
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.NotificationRow
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.ListingState
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationFilter
import ru.colabike.core.model.NotificationsRepository

@Composable
fun NotificationsRoute(
    repository: NotificationsRepository,
    site: SiteLinks,
    onBack: () -> Unit,
    onOpen: (Destination) -> Unit,
    onCount: (NotificationCount) -> Unit = {},
) {
    val viewModel = viewModel { NotificationsViewModel(repository, onCount) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val links = LocalLinkOpener.current
    val routes = remember(state.items, site) { state.items.associate { it.id to it.route(site) } }
    NotificationsScreen(
        state = state,
        ui = ui,
        opens = {
            routes[it.id].let { route -> route != null && route != NotificationRoute.Nowhere }
        },
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onToggleUnread = viewModel::toggleUnreadOnly,
        onCategory = viewModel::selectCategory,
        onReadAll = viewModel::readAll,
        onMarkRead = { viewModel.markRead(it, asked = true) },
        onDismissMessage = viewModel::dismissMessage,
        onOpen = { notification ->
            // Opening is what reads it; the server's answer, not this tap, makes it read here.
            viewModel.markRead(notification)
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
 * The inbox of ColaBike events: who did what to which object, narrowed by "unread" and by a
 * category. What is read is what the server confirmed: opening a notification, its button and "read
 * all" each ask the server, and a notification shows as read once it has answered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: PagedState<AppNotification>,
    ui: InboxUi,
    opens: (AppNotification) -> Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleUnread: () -> Unit,
    onCategory: (NotificationCategory?) -> Unit,
    onReadAll: () -> Unit,
    onMarkRead: (AppNotification) -> Unit,
    onDismissMessage: () -> Unit,
    onOpen: (AppNotification) -> Unit,
) {
    val canReadAll =
        ui.watermark != null &&
            (state.items.any { !it.read } || state.nextCursor != null) &&
            state.error == null
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.notifications_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Filters(ui.filter, onToggleUnread, onCategory)
            if (canReadAll) {
                val label = stringResource(R.string.notifications_read_all)
                val spoken =
                    ui.filter.category?.let {
                        stringResource(
                            R.string.notifications_read_all_in,
                            stringResource(it.label()),
                        )
                    } ?: label
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onReadAll,
                        enabled = !ui.readingAll,
                        modifier =
                            Modifier.heightIn(min = Spacing.touch)
                                .testTag("notifications:read_all")
                                .semantics { contentDescription = spoken },
                    ) {
                        Text(label)
                    }
                }
            }
            ui.message?.let { message ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissMessage) {
                        Text(stringResource(R.string.notifications_dismiss))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> LoadingState(Modifier.fillMaxSize())
                    state.error != null ->
                        ErrorState(
                            state.error.resolve(),
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    state.isEmpty ->
                        if (ui.filter.isNarrowed) {
                            EmptyState(
                                title =
                                    stringResource(
                                        if (ui.filter.unreadOnly) R.string.notifications_all_read
                                        else R.string.notifications_empty_filtered_title
                                    ),
                                message = stringResource(R.string.notifications_empty_filtered),
                                icon = ColaIcons.Notifications,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            EmptyState(
                                title = stringResource(R.string.notifications_empty_title),
                                message = stringResource(R.string.notifications_empty),
                                icon = ColaIcons.Notifications,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    else ->
                        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh) {
                            Inbox(
                                state,
                                opens,
                                onRefresh,
                                onRetry,
                                onLoadMore,
                                onOpen,
                                onMarkRead,
                            )
                        }
                }
            }
        }
    }
}

/** "Unread", then one category at a time (or all): the two narrow the inbox together. */
@Composable
private fun Filters(
    filter: NotificationFilter,
    onToggleUnread: () -> Unit,
    onCategory: (NotificationCategory?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("notifications:filters"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            ColaFilterChip(
                selected = filter.unreadOnly,
                onClick = onToggleUnread,
                label = stringResource(R.string.notifications_filter_unread),
                modifier = Modifier.testTag("notifications:filter:unread"),
            )
        }
        item {
            ColaFilterChip(
                selected = filter.category == null,
                onClick = { onCategory(null) },
                label = stringResource(R.string.notifications_filter_all),
                modifier = Modifier.testTag("notifications:filter:all"),
            )
        }
        items(NotificationCategory.Filterable) { category ->
            ColaFilterChip(
                selected = filter.category == category,
                onClick = { onCategory(category) },
                label = stringResource(category.label()),
                modifier = Modifier.testTag("notifications:filter:${category.key}"),
            )
        }
    }
}

@StringRes
private fun NotificationCategory.label(): Int =
    when (this) {
        NotificationCategory.Rides -> R.string.notifications_category_rides
        NotificationCategory.Discussions -> R.string.notifications_category_discussions
        NotificationCategory.Plans -> R.string.notifications_category_plans
        NotificationCategory.Intents -> R.string.notifications_category_intents
        NotificationCategory.Market -> R.string.notifications_category_market
        NotificationCategory.Chat -> R.string.notifications_category_chat
        NotificationCategory.Reactions -> R.string.notifications_category_reactions
        NotificationCategory.Site -> R.string.notifications_category_site
        NotificationCategory.Other -> R.string.notifications_category_other
    }

@Composable
private fun Inbox(
    state: PagedState<AppNotification>,
    opens: (AppNotification) -> Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onMarkRead: (AppNotification) -> Unit,
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
        items(state.items, key = { it.id }) { notification ->
            val (title, body) = notification.text(locale, formatter)
            NotificationRow(
                title = title,
                body = body,
                whenText = formatter.format(notification.createdAt),
                unread = !notification.read,
                actor = notification.actor,
                onClick = if (opens(notification)) ({ onOpen(notification) }) else null,
                onMarkRead = { onMarkRead(notification) },
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
        kind == "ride_invite" ->
            stringResource(R.string.notifications_ride_invite) to rideLine(about, formatter)
        kind == "ride_changed" ->
            stringResource(R.string.notifications_ride_changed) to rideLine(about, formatter)
        kind == "ride_cancelled" ->
            stringResource(R.string.notifications_ride_cancelled) to rideLine(about, formatter)
        kind == "ride_response" ->
            stringResource(R.string.notifications_ride_response) to rideLine(about, formatter)
        kind == "ride_reminder" ->
            stringResource(R.string.notifications_ride_reminder) to rideLine(about, formatter)
        kind == "plan_published" ->
            stringResource(R.string.notifications_plan_published) to rideLine(about, formatter)
        kind == "intent_published" ->
            stringResource(R.string.notifications_intent_published) to (who ?: about)
        kind == "session_reuse" ->
            stringResource(R.string.notifications_session) to
                stringResource(R.string.notifications_session_body)
        kind == "bike_week" -> stringResource(R.string.notifications_bike_week) to target.name
        else -> stringResource(R.string.notifications_generic) to about
    }
}

/** The ride and, when the notification names a date, that date: "Who · Ride. Sat, 10:00". */
private fun AppNotification.rideLine(about: String, formatter: DateTimeFormatter): String =
    listOfNotNull(about.takeIf { it.isNotBlank() }, target.occurrenceAt?.let(formatter::format))
        .joinToString(". ")

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
