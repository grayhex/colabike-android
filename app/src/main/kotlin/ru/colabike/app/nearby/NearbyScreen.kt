package ru.colabike.app.nearby

import androidx.annotation.StringRes
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import ru.colabike.core.designsystem.component.ColaSwitchRow
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.NearbyChoices
import ru.colabike.core.model.NearbySettings
import ru.colabike.core.model.NearbySource
import ru.colabike.core.model.radiusChoices

/** What the screen can ask for; the route turns each into a call (or the system's question). */
data class NearbyActions(
    val onBack: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onEnabled: (Boolean) -> Unit = {},
    val onHorizon: (Int) -> Unit = {},
    val onToggle: (NearbyGroup, String) -> Unit = { _, _ -> },
    /** The person agreed to the explanation: ask the system and read the place. */
    val onLocate: () -> Unit = {},
    val onRadius: (Int) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onReplaceYes: () -> Unit = {},
    val onReplaceNo: () -> Unit = {},
    val onRemoveArea: () -> Unit = {},
    val onForget: () -> Unit = {},
    /** Null where the rides are off: there is nothing to show. */
    val onOpenOffers: (() -> Unit)? = null,
)

private val ContentWidth = 600.dp

/**
 * Profile → Notifications → Rides near me. What the person decided about the area and the kinds of
 * rides, said plainly: the area is a grid square and not a point, this is not a consent to push or
 * to publishing an intention, and "forget everything" is always one step away. A change is shown
 * only after the server took it.
 */
@Composable
fun NearbyScreen(state: NearbyUiState, actions: NearbyActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.nearby_title), onBack = actions.onBack)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NearbyUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is NearbyUiState.Failed ->
                    ErrorState(
                        state.message.resolve(),
                        onRetry = actions.onRetryLoad,
                        modifier = Modifier.fillMaxSize(),
                    )
                is NearbyUiState.Loaded -> Settings(state, actions)
            }
        }
    }
}

@Composable
private fun Settings(state: NearbyUiState.Loaded, actions: NearbyActions) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("nearby"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Text(
                    stringResource(R.string.nearby_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Outcome(state)
                SwitchCard(state, actions)
                AreaCard(state, actions)
                state.draft?.let { DraftCard(state, it, actions) }
                HorizonCard(state, actions)
                FiltersCard(state, actions)
                actions.onOpenOffers?.let { open ->
                    ColaListItem(
                        title = stringResource(R.string.nearby_offers_open),
                        supporting = stringResource(R.string.nearby_offers_open_hint),
                        icon = ColaIcons.Route,
                        onClick = open,
                        modifier = Modifier.testTag("nearby:offers"),
                    )
                }
                ForgetCard(state, actions)
            }
        }
    }
    if (state.askReplace) ReplaceDialog(actions)
}

/** The result of the last change, announced when it appears. */
@Composable
private fun Outcome(state: NearbyUiState.Loaded) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (state.saving) {
            Text(
                stringResource(R.string.nearby_saving),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.locating) {
            Text(
                stringResource(R.string.nearby_locating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("nearby:locating"),
            )
        }
        state.notice?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("nearby:notice"),
            )
        }
        state.problem?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("nearby:problem"),
            )
        }
    }
}

@Composable
private fun SwitchCard(state: NearbyUiState.Loaded, actions: NearbyActions) {
    val settings = state.settings
    Section(R.string.nearby_switch_title) {
        ColaSwitchRow(
            title = stringResource(R.string.nearby_switch),
            supporting =
                stringResource(
                    if (settings.available) R.string.nearby_switch_hint
                    else R.string.nearby_off_by_operator
                ),
            checked = settings.enabled,
            onCheckedChange = actions.onEnabled,
            // The operator's switch allows only turning it off.
            enabled = !state.busy && (settings.available || settings.enabled),
            modifier = Modifier.testTag("nearby:switch"),
        )
    }
}

@Composable
private fun AreaCard(state: NearbyUiState.Loaded, actions: NearbyActions) {
    val settings = state.settings
    val area = settings.area
    val canAsk = !state.busy && settings.available
    var explaining by rememberSaveable { mutableStateOf(false) }
    Section(R.string.nearby_area_title) {
        if (area == null) {
            Text(
                stringResource(R.string.nearby_area_none),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("nearby:area-none"),
            )
        } else {
            Text(
                stringResource(R.string.nearby_area_radius, area.radiusM / 1000),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("nearby:area"),
            )
            Text(
                sourceLine(settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("nearby:area-source"),
            )
        }
        if (settings.expired) {
            Text(
                stringResource(R.string.nearby_area_expired),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("nearby:expired"),
            )
        }
        Text(
            stringResource(R.string.nearby_area_site_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { explaining = true },
            enabled = canAsk,
            modifier = Modifier.testTag("nearby:locate"),
        ) {
            Text(stringResource(R.string.nearby_locate))
        }
        if (area != null) {
            TextButton(
                onClick = actions.onRemoveArea,
                enabled = !state.busy,
                modifier = Modifier.testTag("nearby:remove-area"),
            ) {
                Text(stringResource(R.string.nearby_area_remove))
            }
        }
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.nearby_location_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.nearby_location_dialog_body,
                        settings.limits.deviceTtlHours,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        explaining = false
                        actions.onLocate()
                    },
                    modifier = Modifier.testTag("nearby:locate-allow"),
                ) {
                    Text(stringResource(R.string.nearby_location_dialog_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { explaining = false }) {
                    Text(stringResource(R.string.nearby_location_dialog_later))
                }
            },
        )
    }
}

/** Where the area in force came from and, for a phone's, how long it lasts. */
@Composable
private fun sourceLine(settings: NearbySettings): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (settings.source) {
        NearbySource.Manual ->
            settings.area?.label?.let { stringResource(R.string.nearby_area_manual_label, it) }
                ?: stringResource(R.string.nearby_area_manual)
        NearbySource.Device ->
            settings.expiresAt?.let {
                stringResource(R.string.nearby_area_device_until, formatMoment(it, locale))
            } ?: stringResource(R.string.nearby_area_device)
        null -> stringResource(R.string.nearby_area_none)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DraftCard(
    state: NearbyUiState.Loaded,
    draft: DeviceAreaDraft,
    actions: NearbyActions,
) {
    Section(R.string.nearby_draft_title, Modifier.testTag("nearby:draft")) {
        Text(
            stringResource(R.string.nearby_draft_hint, state.settings.limits.deviceTtlHours),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.nearby_radius_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            state.settings.limits.radiusChoices().forEach { radius ->
                ColaFilterChip(
                    selected = radius == draft.radiusM,
                    onClick = { if (!state.busy) actions.onRadius(radius) },
                    label = stringResource(R.string.nearby_km, radius / 1000),
                    modifier = Modifier.testTag("nearby:radius:${radius / 1000}"),
                )
            }
        }
        Button(
            onClick = actions.onConfirm,
            enabled = !state.busy,
            modifier = Modifier.testTag("nearby:confirm"),
        ) {
            Text(stringResource(R.string.nearby_confirm))
        }
        TextButton(
            onClick = actions.onDiscard,
            enabled = !state.busy,
            modifier = Modifier.testTag("nearby:discard"),
        ) {
            Text(stringResource(R.string.nearby_cancel))
        }
    }
}

@Composable
private fun ReplaceDialog(actions: NearbyActions) {
    AlertDialog(
        onDismissRequest = actions.onReplaceNo,
        title = { Text(stringResource(R.string.nearby_replace_title)) },
        text = { Text(stringResource(R.string.nearby_replace_body)) },
        confirmButton = {
            TextButton(
                onClick = actions.onReplaceYes,
                modifier = Modifier.testTag("nearby:replace-yes"),
            ) {
                Text(stringResource(R.string.nearby_replace_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onReplaceNo) {
                Text(stringResource(R.string.nearby_replace_no))
            }
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HorizonCard(state: NearbyUiState.Loaded, actions: NearbyActions) {
    val current = state.settings.horizonDays
    Section(R.string.nearby_horizon_title) {
        Text(
            stringResource(R.string.nearby_horizon_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // A value set on the site that is not on offer is still shown, as chosen.
            (NearbyChoices.horizonsDays + current).distinct().sorted().forEach { days ->
                ColaFilterChip(
                    selected = days == current,
                    onClick = { if (!state.busy && days != current) actions.onHorizon(days) },
                    label = stringResource(R.string.nearby_days, days),
                    modifier = Modifier.testTag("nearby:horizon:$days"),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FiltersCard(state: NearbyUiState.Loaded, actions: NearbyActions) {
    val preferences = state.settings.preferences
    Section(R.string.nearby_filters_title) {
        Text(
            stringResource(R.string.nearby_filters_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceGroup(
            R.string.nearby_purpose,
            NearbyGroup.Purposes,
            NearbyChoices.purposes,
            preferences.purposes,
            ::purposeLabel,
            state.busy,
            actions,
        )
        ChoiceGroup(
            R.string.nearby_pace,
            NearbyGroup.Paces,
            NearbyChoices.paces,
            preferences.paces,
            ::paceLabel,
            state.busy,
            actions,
        )
        ChoiceGroup(
            R.string.nearby_surface,
            NearbyGroup.Surfaces,
            NearbyChoices.surfaces,
            preferences.surfaces,
            ::surfaceLabel,
            state.busy,
            actions,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChoiceGroup(
    @StringRes title: Int,
    group: NearbyGroup,
    keys: List<String>,
    selected: Set<String>,
    label: (String) -> Int,
    busy: Boolean,
    actions: NearbyActions,
) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        keys.forEach { key ->
            ColaFilterChip(
                selected = key in selected,
                onClick = { if (!busy) actions.onToggle(group, key) },
                label = stringResource(label(key)),
                modifier = Modifier.testTag("nearby:${group.name.lowercase()}:$key"),
            )
        }
    }
}

@Composable
private fun ForgetCard(state: NearbyUiState.Loaded, actions: NearbyActions) {
    var asking by rememberSaveable { mutableStateOf(false) }
    Section(R.string.nearby_forget_title) {
        Text(
            stringResource(R.string.nearby_forget_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { asking = true },
            enabled = !state.busy,
            modifier = Modifier.testTag("nearby:forget"),
        ) {
            Text(stringResource(R.string.nearby_forget))
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.nearby_forget_dialog_title)) },
            text = { Text(stringResource(R.string.nearby_forget_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        actions.onForget()
                    },
                    modifier = Modifier.testTag("nearby:forget-yes"),
                ) {
                    Text(stringResource(R.string.nearby_forget))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) {
                    Text(stringResource(R.string.nearby_cancel))
                }
            },
        )
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ColaCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Eyebrow(stringResource(title), modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

private fun formatMoment(moment: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(moment)

@StringRes
private fun purposeLabel(key: String): Int =
    when (key) {
        "leisure" -> R.string.nearby_purpose_leisure
        "social" -> R.string.nearby_purpose_social
        "training" -> R.string.nearby_purpose_training
        "exploration" -> R.string.nearby_purpose_exploration
        else -> R.string.nearby_purpose_adventure
    }

@StringRes
private fun paceLabel(key: String): Int =
    when (key) {
        "relaxed" -> R.string.nearby_pace_relaxed
        "moderate" -> R.string.nearby_pace_moderate
        else -> R.string.nearby_pace_sporty
    }

@StringRes
private fun surfaceLabel(key: String): Int =
    when (key) {
        "asphalt" -> R.string.nearby_surface_asphalt
        "gravel" -> R.string.nearby_surface_gravel
        "trail" -> R.string.nearby_surface_trail
        else -> R.string.nearby_surface_mixed
    }
