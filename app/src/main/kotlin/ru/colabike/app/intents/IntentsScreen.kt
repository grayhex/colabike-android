package ru.colabike.app.intents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import ru.colabike.app.R
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.resolve
import ru.colabike.app.ui.zoneLabel
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PersonByline
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.RideIntent
import ru.colabike.core.model.RidePassport

/** What the list can ask for. */
data class IntentsActions(
    val onBack: () -> Unit = {},
    val onSegment: (IntentSegment) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpen: (RideIntent) -> Unit = {},
    val onCreate: () -> Unit = {},
)

private val ContentWidth = 640.dp

/**
 * "I want to ride": what the people the viewer can see published, and the viewer's own. An
 * intention is not an event: nobody answers "going" to it; a card opens the author and the terms.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IntentsScreen(state: IntentsUiState, actions: IntentsActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.intents_title), onBack = actions.onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FlowRow(
                Modifier.padding(horizontal = Spacing.screen).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                IntentSegment.entries.forEach { segment ->
                    ColaFilterChip(
                        selected = state.segment == segment,
                        onClick = { actions.onSegment(segment) },
                        label =
                            stringResource(
                                when (segment) {
                                    IntentSegment.Community -> R.string.intents_segment_community
                                    IntentSegment.Mine -> R.string.intents_segment_mine
                                }
                            ),
                        modifier = Modifier.testTag("intents:segment:${segment.name.lowercase()}"),
                    )
                }
            }
            Button(
                onClick = actions.onCreate,
                modifier =
                    Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s)
                        .testTag("intents:create"),
            ) {
                Text(stringResource(R.string.intents_create))
            }
            IntentList(state, actions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntentList(state: IntentsUiState, actions: IntentsActions) {
    val page = state.page
    when {
        page.loading -> LoadingState(Modifier.fillMaxSize())
        page.error != null ->
            ErrorState(page.error.resolve(), onRetry = actions.onRetry, Modifier.fillMaxSize())
        page.isEmpty ->
            EmptyState(
                title = stringResource(emptyTitle(state.segment)),
                message = stringResource(emptyMessage(state.segment)),
                icon = ColaIcons.Route,
                modifier = Modifier.fillMaxSize().testTag("intents:empty"),
            )
        else ->
            PullToRefreshBox(isRefreshing = page.refreshing, onRefresh = actions.onRefresh) {
                Intents(page, actions)
            }
    }
}

@Composable
private fun Intents(page: PagedState<RideIntent>, actions: IntentsActions) {
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(list) { snapshotFlow { nearEnd }.collect { if (it) actions.onLoadMore() } }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().testTag("intents:list"),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        page.refreshError?.let { error ->
            item { RetryRow(error.resolve(), actions.onRefresh) }
        }
        items(page.items, key = { it.id }) { intent ->
            IntentCard(
                intent,
                onClick = { actions.onOpen(intent) },
                modifier = Modifier.widthIn(max = ContentWidth).testTag("intent:${intent.id}"),
            )
        }
        if (page.loadingMore || page.moreError != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(Spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    if (page.loadingMore) CircularProgressIndicator()
                    else RetryRow(page.moreError!!.resolve(), actions.onLoadMore)
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

private fun emptyTitle(segment: IntentSegment) =
    when (segment) {
        IntentSegment.Community -> R.string.intents_empty_community_title
        IntentSegment.Mine -> R.string.intents_empty_mine_title
    }

private fun emptyMessage(segment: IntentSegment) =
    when (segment) {
        IntentSegment.Community -> R.string.intents_empty_community
        IntentSegment.Mine -> R.string.intents_empty_mine
    }

/** The first window as text in the intention's own zone, and how many more there are. */
@Composable
internal fun windowsLine(intent: RideIntent, showZone: Boolean = true): String {
    val locale = LocalConfiguration.current.locales[0]
    val first = intent.windows.minByOrNull { it.startsAt } ?: return ""
    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val start = first.startsAt.atZone(intent.timeZone)
    val end = first.endsAt.atZone(intent.timeZone)
    val line =
        if (start.toLocalDate() == end.toLocalDate()) {
            stringResource(
                R.string.intent_window,
                day.format(start),
                time.format(start),
                time.format(end),
            )
        } else {
            stringResource(
                R.string.intent_window_days,
                day.format(start),
                time.format(start),
                day.format(end),
                time.format(end),
            )
        }
    val phone = ZoneId.systemDefault()
    val different =
        phone.rules.getOffset(first.startsAt) != intent.timeZone.rules.getOffset(first.startsAt) ||
            phone.rules.getOffset(first.endsAt) != intent.timeZone.rules.getOffset(first.endsAt)
    val zone =
        listOf(first.startsAt, first.endsAt)
            .map { zoneLabel(intent.timeZone, it, locale) }
            .distinct()
            .joinToString(" / ")
    val withZone = if (different && showZone) "$line · $zone" else line
    val more = intent.windows.size - 1
    return if (more > 0) stringResource(R.string.intent_more_windows, withZone, more) else withZone
}

@Composable
internal fun intentFacts(intent: RideIntent): List<String> {
    val facts = mutableListOf<String>()
    facts += stringResource(intent.readinessLabel())
    intent.passport.areaLabel?.let { facts += it }
    intent.passport.purpose?.let { facts += stringResource(purposeLabel(it)) }
    return facts
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntentCard(intent: RideIntent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val who = intent.author.displayName
    val facts =
        intentFacts(intent) +
            listOfNotNull(
                intent.passport.pace?.let { stringResource(paceLabel(it)) },
                intent.passport.surface?.let { stringResource(surfaceLabel(it)) },
            ) +
            intentRanges(intent.passport)
    val windows = windowsLine(intent)
    val status = stringResource(intent.statusLabel())
    val description =
        listOfNotNull(
                stringResource(R.string.intent_by, who),
                *facts.toTypedArray(),
                windows.takeIf { it.isNotBlank() },
                status.takeIf { intent.own },
            )
            .joinToString(". ")
    val openLabel = stringResource(ru.colabike.core.designsystem.R.string.cola_open)
    ColaCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier =
            modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                onClick(label = openLabel) {
                    onClick()
                    true
                }
            },
    ) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                intent.passport.areaLabel ?: stringResource(R.string.intent_area_unknown),
                style = MaterialTheme.typography.titleLarge,
            )
            if (windows.isNotBlank()) Text(windows, style = MaterialTheme.typography.bodyMedium)
            PersonByline(intent.author, supporting = stringResource(intent.readinessLabel()))
            val routeFacts =
                listOfNotNull(
                    intent.passport.purpose?.let { stringResource(purposeLabel(it)) },
                    intent.passport.pace?.let { stringResource(paceLabel(it)) },
                    intent.passport.surface?.let { stringResource(surfaceLabel(it)) },
                )
            val allFacts = routeFacts + intentRanges(intent.passport)
            if (allFacts.isNotEmpty())
                Text(
                    allFacts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            if (intent.own) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Only the ranges the author actually supplied; shared by the card and its page. */
@Composable
internal fun intentRanges(passport: RidePassport): List<String> {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    return listOfNotNull(
        passport.distanceKm?.let {
            stringResource(R.string.ride_range_km, numbers.format(it.min), numbers.format(it.max))
        },
        passport.durationMinutes?.let {
            stringResource(
                R.string.ride_range_minutes,
                numbers.format(it.min),
                numbers.format(it.max),
            )
        },
        passport.speedKmh?.let {
            stringResource(
                R.string.ride_range_speed,
                numbers.format(it.min),
                numbers.format(it.max),
            )
        },
    )
}

// Labels ------------------------------------------------------------------------------------------

internal fun RideIntent.readinessLabel(): Int =
    when (readiness) {
        ru.colabike.core.model.IntentReadiness.Ready -> R.string.intent_ready
        ru.colabike.core.model.IntentReadiness.Considering -> R.string.intent_considering
    }

/** The state of the person's own intention in words (visibility, and whether it is still open). */
internal fun RideIntent.statusLabel(): Int =
    when (status) {
        ru.colabike.core.model.IntentStatus.Cancelled -> R.string.intent_status_cancelled
        ru.colabike.core.model.IntentStatus.Expired -> R.string.intent_status_expired
        else ->
            when (visibility) {
                ru.colabike.core.model.IntentVisibility.Private -> R.string.intent_status_private
                ru.colabike.core.model.IntentVisibility.Community ->
                    R.string.intent_status_community
            }
    }

internal fun purposeLabel(key: String): Int =
    when (key) {
        "leisure" -> R.string.nearby_purpose_leisure
        "social" -> R.string.nearby_purpose_social
        "training" -> R.string.nearby_purpose_training
        "exploration" -> R.string.nearby_purpose_exploration
        "adventure" -> R.string.nearby_purpose_adventure
        else -> R.string.intent_purpose_other
    }

internal fun paceLabel(key: String): Int =
    when (key) {
        "relaxed" -> R.string.nearby_pace_relaxed
        "moderate" -> R.string.nearby_pace_moderate
        "sporty" -> R.string.nearby_pace_sporty
        else -> R.string.intent_purpose_other
    }

internal fun surfaceLabel(key: String): Int =
    when (key) {
        "asphalt" -> R.string.nearby_surface_asphalt
        "gravel" -> R.string.nearby_surface_gravel
        "trail" -> R.string.nearby_surface_trail
        "mixed" -> R.string.nearby_surface_mixed
        else -> R.string.intent_purpose_other
    }
