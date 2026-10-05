package ru.colabike.app.participation

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AgreementChange
import ru.colabike.core.model.ParticipationResponse
import ru.colabike.core.model.ParticipationState
import ru.colabike.core.model.RequestedDateStatus
import ru.colabike.core.model.RideParticipation
import ru.colabike.core.model.ViewerRole

/** What the page can ask for. */
data class ParticipationActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRespond: (ParticipationResponse) -> Unit = {},
    val onOpenRide: (String) -> Unit = {},
    val onOpenAuthor: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenRides: () -> Unit = {},
)

private val ContentWidth = 600.dp

/**
 * One date of a plan for the person: who organizes it, when (in the plan's own zone, and in the
 * phone's if it differs), what is agreed, what changed since the person answered, and the answers
 * the server will take. An answer is a tap and nothing else; a cancelled date has no buttons.
 */
@Composable
fun ParticipationScreen(state: ParticipationUiState, actions: ParticipationActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.participation_title),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ParticipationUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is ParticipationUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetry, Modifier.fillMaxSize())
                ParticipationUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.participation_unavailable_title),
                        message = stringResource(R.string.participation_unavailable),
                        icon = ColaIcons.Route,
                        actionLabel = stringResource(R.string.participation_open_rides),
                        onAction = actions.onOpenRides,
                        modifier = Modifier.fillMaxSize().testTag("participation:unavailable"),
                    )
                is ParticipationUiState.Loaded -> Page(state, actions)
            }
        }
    }
}

@Composable
private fun Page(state: ParticipationUiState.Loaded, actions: ParticipationActions) {
    val p = state.participation
    LazyColumn(
        Modifier.fillMaxSize().testTag("participation"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Outcome(state)
                Header(p)
                DateNotice(p)
                if (state.changedMeanwhile) Meanwhile()
                if (p.state == ParticipationState.Reconfirm || p.changedAfterAnswer) {
                    Changed(p)
                }
                Terms(p)
                MyAnswer(state, actions)
                ColaListItem(
                    title = stringResource(R.string.participation_open_ride),
                    supporting = stringResource(R.string.participation_open_ride_hint),
                    icon = ColaIcons.Route,
                    onClick = { actions.onOpenRide(p.rideId) },
                    modifier = Modifier.testTag("participation:ride"),
                )
                if (p.role != ViewerRole.Organizer) {
                    ColaListItem(
                        title = stringResource(R.string.participation_reminders),
                        supporting = stringResource(R.string.participation_reminders_hint),
                        icon = ColaIcons.Notifications,
                        onClick = actions.onOpenSettings,
                        modifier = Modifier.testTag("participation:reminders"),
                    )
                }
            }
        }
    }
}

/** What the last answer came to, announced when it appears. */
@Composable
private fun Outcome(state: ParticipationUiState.Loaded) {
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        if (state.sending != null) {
            Text(
                stringResource(R.string.participation_sending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.notice?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("participation:notice"),
            )
        }
        state.problem?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("participation:problem"),
            )
        }
    }
}

@Composable
private fun Header(p: RideParticipation) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                p.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }.testTag("participation:title"),
            )
            Text(
                stringResource(R.string.participation_organizer, p.author.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The date the person came for; if it is another than the one to answer for, both.
            val moment = p.requested?.at ?: p.scheduledAt
            if (moment != null) {
                Text(
                    momentLine(moment, p.timeZone),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("participation:when"),
                )
                zoneNote(moment, p.timeZone)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("participation:zone"),
                    )
                }
            }
            val next = p.scheduledAt
            if (next != null && moment != null && next != moment) {
                Text(
                    stringResource(R.string.participation_next_date, momentLine(next, p.timeZone)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("participation:next"),
                )
            }
        }
    }
}

/**
 * What became of the date the person came for: cancelled (this date, or the whole plan), moved, or
 * past. A cancelled date never offers an answer and never shows a place.
 */
@Composable
private fun DateNotice(p: RideParticipation) {
    val text =
        when {
            p.planCancelled -> R.string.participation_plan_cancelled
            p.dateCancelled ->
                if (p.scheduledAt != null) R.string.participation_date_cancelled_goes_on
                else R.string.participation_date_cancelled
            p.requested?.status == RequestedDateStatus.Past -> R.string.participation_date_past
            p.requested?.status == RequestedDateStatus.Moved -> R.string.participation_date_moved
            else -> null
        } ?: return
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().testTag("participation:date-notice"),
    )
}

@Composable
private fun Meanwhile() {
    Text(
        stringResource(R.string.participation_changed_meanwhile),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().testTag("participation:meanwhile"),
    )
}

/** "The terms changed - confirm": what changed, and what the person said to the earlier terms. */
@Composable
private fun Changed(p: RideParticipation) {
    ColaCard(Modifier.fillMaxWidth().testTag("participation:changed")) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                stringResource(R.string.participation_changed_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { heading() },
            )
            p.previousResponse?.let {
                Text(
                    stringResource(R.string.participation_previous, stringResource(it.label())),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (p.agreement.changes.isNotEmpty()) {
                val names =
                    p.agreement.changes.map {
                        stringResource(it.label()).lowercase(Locale.getDefault())
                    }
                Text(
                    stringResource(R.string.participation_changed_what, names.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.participation_changed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Terms(p: RideParticipation) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Eyebrow(
                stringResource(R.string.participation_terms),
                modifier = Modifier.semantics { heading() },
            )
            p.meetingPoint?.let {
                Text(
                    stringResource(R.string.participation_meeting, it),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("participation:meeting"),
                )
            }
            if (p.meetingHidden && !p.planCancelled && !p.dateCancelled) {
                Text(
                    stringResource(R.string.participation_meeting_hidden),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("participation:meeting-hidden"),
                )
            }
            p.passport?.areaLabel?.let {
                Text(
                    stringResource(R.string.participation_area, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.participation_counts, p.going, p.maybe),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("participation:counts"),
            )
            if (p.recruitmentClosed) {
                Text(
                    stringResource(R.string.participation_closed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("participation:closed"),
                )
            }
            p.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyAnswer(state: ParticipationUiState.Loaded, actions: ParticipationActions) {
    val p = state.participation
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Eyebrow(
                stringResource(R.string.participation_mine),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(p.state.label()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("participation:state"),
            )
            if (p.allowed.isEmpty()) {
                Text(
                    stringResource(whyNoAnswer(p)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("participation:no-answer"),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    ParticipationResponse.entries
                        .filter { it in p.allowed }
                        .forEach { response ->
                            ColaFilterChip(
                                selected = p.response == response,
                                onClick = {
                                    if (state.sending == null) actions.onRespond(response)
                                },
                                label = stringResource(response.label()),
                                modifier =
                                    Modifier.testTag("participation:${response.name.lowercase()}"),
                            )
                        }
                }
                Text(
                    stringResource(R.string.participation_answer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Labels and time ---------------------------------------------------------------------------------

/** The date and the hour in the plan's own zone, with the zone said: nothing is guessed. */
@Composable
private fun momentLine(moment: Instant, zone: ZoneId): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
            .withLocale(locale)
    return stringResource(
        R.string.participation_moment,
        formatter.format(moment.atZone(zone)),
        zone.id,
    )
}

/** The same moment on the phone's clock, if the phone is in another zone; else nothing. */
@Composable
private fun zoneNote(moment: Instant, zone: ZoneId): String? {
    val phone = ZoneId.systemDefault()
    if (phone.rules.getOffset(moment) == zone.rules.getOffset(moment)) return null
    val locale = LocalConfiguration.current.locales[0]
    val formatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
    return stringResource(R.string.participation_phone_time, formatter.format(moment.atZone(phone)))
}

private fun ParticipationResponse.label(): Int =
    when (this) {
        ParticipationResponse.Accepted -> R.string.participation_going
        ParticipationResponse.Maybe -> R.string.participation_maybe
        ParticipationResponse.Declined -> R.string.participation_declined
    }

private fun ParticipationState.label(): Int =
    when (this) {
        ParticipationState.Organizer -> R.string.participation_state_organizer
        ParticipationState.Accepted -> R.string.participation_state_accepted
        ParticipationState.Maybe -> R.string.participation_state_maybe
        ParticipationState.Declined -> R.string.participation_state_declined
        ParticipationState.Reconfirm -> R.string.participation_state_reconfirm
        ParticipationState.Invited -> R.string.participation_state_invited
        ParticipationState.None -> R.string.participation_state_none
        ParticipationState.Unknown -> R.string.participation_state_none
    }

private fun AgreementChange.label(): Int =
    when (this) {
        AgreementChange.Start -> R.string.participation_change_start
        AgreementChange.Place -> R.string.participation_change_place
        AgreementChange.Route -> R.string.participation_change_route
    }

/** Why there is nothing to answer with: in words, not a silent absence of buttons. */
private fun whyNoAnswer(p: RideParticipation): Int =
    when {
        p.role == ViewerRole.Organizer -> R.string.participation_no_answer_organizer
        p.planCancelled || p.dateCancelled -> R.string.participation_no_answer_cancelled
        p.requested?.status == RequestedDateStatus.Past || p.scheduledAt == null ->
            R.string.participation_no_answer_past
        p.recruitmentClosed -> R.string.participation_no_answer_closed
        else -> R.string.participation_no_answer
    }
