package ru.colabike.app.intents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.app.messages.WriteState
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.IntentStatus
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.RideIntent

/** What an intention's page can ask for. */
data class IntentActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenAuthor: (String) -> Unit = {},
    /** Null where the chat is off or the viewer is a guest; "Write" is then not offered. */
    val onWrite: (() -> Unit)? = null,
    val onEdit: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onOpenIntents: () -> Unit = {},
)

private val ContentWidth = 600.dp

/**
 * One intention: who, when, where and what kind, and what the viewer can do. Someone else's is read
 * and answered by writing to its author (the app sends nothing for the person); one's own can be
 * changed, cancelled (it stays in the list) or deleted. There is no "going" here: an intention is
 * not an event.
 */
@Composable
fun IntentScreen(state: IntentUiState, writing: WriteState, actions: IntentActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.intent_title), onBack = actions.onBack)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                IntentUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is IntentUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetry, Modifier.fillMaxSize())
                IntentUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.intent_unavailable_title),
                        message = stringResource(R.string.intent_unavailable),
                        icon = ColaIcons.Route,
                        actionLabel = stringResource(R.string.intent_open_list),
                        onAction = actions.onOpenIntents,
                        modifier = Modifier.fillMaxSize().testTag("intent:unavailable"),
                    )
                is IntentUiState.Loaded -> Details(state, writing, actions)
            }
        }
    }
}

@Composable
private fun Details(state: IntentUiState.Loaded, writing: WriteState, actions: IntentActions) {
    val intent = state.intent
    LazyColumn(
        Modifier.fillMaxSize().testTag("intent:page"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Outcome(state, writing)
                Summary(intent)
                Terms(intent)
                if (intent.own) OwnActions(state, actions)
                else OthersActions(intent, writing, actions)
            }
        }
    }
}

/** What went wrong with the last action, announced when it appears. */
@Composable
private fun Outcome(state: IntentUiState.Loaded, writing: WriteState) {
    val message = state.problem ?: (writing as? WriteState.Failed)?.message
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        if (state.busy) {
            Text(
                stringResource(R.string.intent_working),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("intent:problem"),
            )
        }
    }
}

@Composable
private fun Summary(intent: RideIntent) {
    val who = intent.author.displayName
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                if (intent.own) stringResource(R.string.intent_yours) else who,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }.testTag("intent:who"),
            )
            Text(
                stringResource(intent.readinessLabel()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (intent.status != IntentStatus.Active) {
                Text(
                    stringResource(intent.statusLabel()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("intent:status"),
                )
            }
        }
    }
}

@Composable
private fun Terms(intent: RideIntent) {
    val passport = intent.passport
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Eyebrow(
                stringResource(R.string.intent_when),
                modifier = Modifier.semantics { heading() },
            )
            intent.windows
                .sortedBy { it.startsAt }
                .forEach { window ->
                    Text(
                        windowsLine(intent.copy(windows = listOf(window))),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            Text(
                stringResource(R.string.intent_zone, intent.timeZone.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Eyebrow(
                stringResource(R.string.intent_where),
                modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
            )
            Text(
                passport.areaLabel ?: stringResource(R.string.intent_area_unknown),
                style = MaterialTheme.typography.bodyLarge,
            )
            val kinds =
                listOfNotNull(
                    passport.purpose?.let { stringResource(purposeLabel(it)) },
                    passport.pace?.let { stringResource(paceLabel(it)) },
                    passport.surface?.let { stringResource(surfaceLabel(it)) },
                )
            if (kinds.isNotEmpty()) {
                Text(kinds.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            if (intent.meetNewPeople == true) {
                Text(
                    stringResource(R.string.intent_meet_new),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun OthersActions(intent: RideIntent, writing: WriteState, actions: IntentActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(R.string.intent_not_an_event),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        actions.onWrite?.let { write ->
            Button(
                onClick = write,
                enabled = writing != WriteState.Opening,
                modifier = Modifier.testTag("intent:write"),
            ) {
                Text(stringResource(R.string.intent_write, intent.author.displayName))
            }
        }
        OutlinedButton(
            onClick = { actions.onOpenAuthor(intent.author.id.value) },
            modifier = Modifier.testTag("intent:author"),
        ) {
            Text(stringResource(R.string.intent_open_author))
        }
    }
}

@Composable
private fun OwnActions(state: IntentUiState.Loaded, actions: IntentActions) {
    val intent = state.intent
    var asking by rememberSaveable { mutableStateOf<Ask?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(
                if (intent.visibility == IntentVisibility.Community)
                    R.string.intent_visible_community
                else R.string.intent_visible_private
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (intent.editable) {
            Button(
                onClick = actions.onEdit,
                enabled = !state.busy,
                modifier = Modifier.testTag("intent:edit"),
            ) {
                Text(stringResource(R.string.intent_edit))
            }
            OutlinedButton(
                onClick = { asking = Ask.Cancel },
                enabled = !state.busy,
                modifier = Modifier.testTag("intent:cancel"),
            ) {
                Text(stringResource(R.string.intent_cancel))
            }
        }
        TextButton(
            onClick = { asking = Ask.Delete },
            enabled = !state.busy,
            modifier = Modifier.testTag("intent:delete"),
        ) {
            Text(stringResource(R.string.intent_delete))
        }
    }
    asking?.let { ask ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = {
                Text(
                    stringResource(
                        if (ask == Ask.Cancel) R.string.intent_cancel_title
                        else R.string.intent_delete_title
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (ask == Ask.Cancel) R.string.intent_cancel_body
                        else R.string.intent_delete_body
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = null
                        if (ask == Ask.Cancel) actions.onCancel() else actions.onDelete()
                    },
                    modifier = Modifier.testTag("intent:confirm"),
                ) {
                    Text(
                        stringResource(
                            if (ask == Ask.Cancel) R.string.intent_cancel
                            else R.string.intent_delete
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = null }) {
                    Text(stringResource(R.string.intent_keep))
                }
            },
        )
    }
}

private enum class Ask {
    Cancel,
    Delete,
}
