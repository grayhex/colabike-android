package ru.colabike.app.devices

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Clock
import java.time.Instant
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.HaloTone
import ru.colabike.core.designsystem.component.IconHalo
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.SessionKind
import ru.colabike.core.model.SessionPlatform

@Composable
fun DevicesRoute(sessions: AccountSessionsRepository, clock: Clock, onBack: () -> Unit) {
    val viewModel = viewModel { DevicesViewModel(sessions) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = remember(clock) { clock.instant() }
    DevicesScreen(
        state = state,
        now = now,
        onBack = onBack,
        onRetry = viewModel::load,
        onEnd = viewModel::askToEnd,
        onConfirmEnd = viewModel::confirmEnd,
        onDismissQuestion = viewModel::dismissQuestion,
    )
}

/**
 * Where the account is signed in. This device comes first and is marked; every other place has an
 * "end session" action that asks for confirmation first.
 */
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    now: Instant,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEnd: (String) -> Unit,
    onConfirmEnd: () -> Unit,
    onDismissQuestion: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.devices_title), onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                DevicesUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is DevicesUiState.Failed ->
                    ErrorState(
                        state.message.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                is DevicesUiState.Loaded -> {
                    SessionList(state, now, onEnd)
                    state.confirming?.let { session ->
                        EndSessionDialog(session, onConfirmEnd, onDismissQuestion)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionList(state: DevicesUiState.Loaded, now: Instant, onEnd: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = Spacing.screen,
                vertical = Spacing.s,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ListWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    stringResource(R.string.devices_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Announced when it appears: the result of ending a session.
                state.notice?.let {
                    Text(
                        it.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        items(state.sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                now = now,
                ending = session.id in state.ending,
                onEnd = { onEnd(session.id) },
                modifier = Modifier.widthIn(max = ListWidth),
            )
        }
        item { Box(Modifier.padding(bottom = Spacing.xxl)) }
    }
}

private val ListWidth = 560.dp

@Composable
private fun SessionCard(
    session: AccountSession,
    now: Instant,
    ending: Boolean,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = session.title()
    // One card, read as one phrase; the "end" button inside it is a node of its own.
    ColaCard(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                IconHalo(
                    session.icon(),
                    tone = if (session.isCurrent) HaloTone.Primary else HaloTone.Secondary,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        session.supporting(now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (session.isCurrent) {
                        PillBadge(
                            stringResource(R.string.devices_this_device),
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
            if (!session.isCurrent) {
                val label = stringResource(R.string.devices_end_named, title)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onEnd,
                        enabled = !ending,
                        modifier =
                            Modifier.heightIn(min = Spacing.touch).semantics {
                                contentDescription = label
                            },
                    ) {
                        Text(stringResource(R.string.devices_end))
                    }
                }
            }
        }
    }
}

@Composable
private fun EndSessionDialog(
    session: AccountSession,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.devices_end_title)) },
        text = { Text(stringResource(R.string.devices_end_message, session.title())) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.devices_end), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.devices_cancel))
            }
        },
    )
}

/**
 * What to call the place: the device's own name, else the browser and system, else a plain word.
 */
@Composable
private fun AccountSession.title(): String =
    deviceName
        ?: describeUserAgent(userAgent).label
        ?: stringResource(
            if (kind == SessionKind.Browser) R.string.devices_browser else R.string.devices_unknown
        )

@Composable
private fun AccountSession.supporting(now: Instant): String {
    val context = LocalContext.current
    val parts = buildList {
        when (kind) {
            SessionKind.Browser -> add(stringResource(R.string.devices_browser))
            SessionKind.Device ->
                add(
                    stringResource(
                        when (platform) {
                            SessionPlatform.Android -> R.string.devices_android
                            SessionPlatform.Ios -> R.string.devices_ios
                            else -> R.string.devices_app
                        }
                    ) + (appVersion?.let { " $it" }.orEmpty())
                )
            SessionKind.Unknown -> Unit
        }
        add(
            if (isCurrent) stringResource(R.string.devices_online_now)
            else
                stringResource(
                    R.string.devices_last_seen,
                    lastSeen(context, lastSeenAt, now),
                )
        )
    }
    return parts.joinToString(" · ")
}

private fun lastSeen(context: android.content.Context, at: Instant, now: Instant): String =
    if (at.isAfter(now.minusSeconds(60))) context.getString(R.string.devices_just_now)
    else
        DateUtils.getRelativeTimeSpanString(
                at.toEpochMilli(),
                now.toEpochMilli(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            .toString()

private fun AccountSession.icon(): Int =
    when {
        kind == SessionKind.Browser -> ColaIcons.Computer
        platform == SessionPlatform.Android || platform == SessionPlatform.Ios ->
            ColaIcons.Smartphone
        else -> ColaIcons.Devices
    }
