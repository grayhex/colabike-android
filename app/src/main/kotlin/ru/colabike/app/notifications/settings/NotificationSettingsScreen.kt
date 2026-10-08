package ru.colabike.app.notifications.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import ru.colabike.app.R
import ru.colabike.app.push.PushAvailability
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaRadioRow
import ru.colabike.core.designsystem.component.ColaSwitchRow
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.HaloTone
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.CategorySetting
import ru.colabike.core.model.CircleMode
import ru.colabike.core.model.MuteKind
import ru.colabike.core.model.NotificationMute
import ru.colabike.core.model.NotificationSettings

/** What the screen can ask for; the route turns each into a call (or a system dialog). */
data class NotificationSettingsActions(
    val onBack: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onRetryChange: () -> Unit = {},
    val onPush: (Boolean) -> Unit = {},
    val onEmail: (Boolean) -> Unit = {},
    val onCategoryPush: (String, Boolean) -> Unit = { _, _ -> },
    val onCategoryEmail: (String, Boolean) -> Unit = { _, _ -> },
    val onReminders: (Boolean) -> Unit = {},
    val onQuietEnabled: (Boolean) -> Unit = {},
    val onPickQuietFrom: () -> Unit = {},
    val onPickQuietTo: () -> Unit = {},
    val onQuietCancellations: (Boolean) -> Unit = {},
    val onUsePhoneZone: () -> Unit = {},
    val onPause: (Duration) -> Unit = {},
    val onResume: () -> Unit = {},
    val onCircle: (CircleMode) -> Unit = {},
    val onConsidering: (Boolean) -> Unit = {},
    val onAddMember: (String) -> Unit = {},
    val onRemoveMember: (String) -> Unit = {},
    val onRemoveMute: (NotificationMute) -> Unit = {},
    val onAskPermission: () -> Unit = {},
    val onOpenSystemSettings: () -> Unit = {},
    /** Rides near me, in settings of its own; null where the rides are switched off. */
    val onOpenNearby: (() -> Unit)? = null,
)

private val ContentWidth = 600.dp

/** The pauses on offer, shortest first. */
private val Pauses =
    listOf(
        R.string.notif_settings_pause_hour to Duration.ofHours(1),
        R.string.notif_settings_pause_8h to Duration.ofHours(8),
        R.string.notif_settings_pause_day to Duration.ofDays(1),
        R.string.notif_settings_pause_3d to Duration.ofDays(3),
        R.string.notif_settings_pause_week to Duration.ofDays(7),
    )

/**
 * Profile → Notifications. Two parts, said apart: what the account decided (the same on the site
 * and on every phone) and what this phone allows (the system's, which the server can neither grant
 * nor read). A change is shown only after the server took it; a failure says so and can be retried.
 */
@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    actions: NotificationSettingsActions,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.notif_settings_title),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NotificationSettingsUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is NotificationSettingsUiState.Failed ->
                    ErrorState(
                        state.message.resolve(),
                        onRetry = actions.onRetryLoad,
                        modifier = Modifier.fillMaxSize(),
                    )
                is NotificationSettingsUiState.Loaded -> Settings(state, actions)
            }
        }
    }
}

@Composable
private fun Settings(
    state: NotificationSettingsUiState.Loaded,
    actions: NotificationSettingsActions,
) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("notif-settings"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Text(
                    stringResource(R.string.notif_settings_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Outcome(state, actions)
                AccountPart(state, actions)
                PhonePart(state, actions)
            }
        }
    }
}

/** The result of the last change, announced when it appears; a failure carries "try again". */
@Composable
private fun Outcome(
    state: NotificationSettingsUiState.Loaded,
    actions: NotificationSettingsActions,
) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (state.saving) {
            Text(
                stringResource(R.string.notif_settings_saving),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.notice?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("notif-settings:notice"),
            )
        }
        state.problem?.let { problem ->
            Text(
                problem.message.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("notif-settings:problem"),
            )
            if (problem.retry != null) {
                TextButton(onClick = actions.onRetryChange) {
                    Text(stringResource(ru.colabike.core.designsystem.R.string.cola_retry))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The account
// ---------------------------------------------------------------------------------------------

@Composable
private fun AccountPart(
    state: NotificationSettingsUiState.Loaded,
    actions: NotificationSettingsActions,
) {
    val settings = state.settings
    val idle = !state.saving
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        PartHeading(R.string.notif_settings_account, R.string.notif_settings_account_hint)

        ChannelsCard(settings, idle, actions)
        CategoriesCard(settings, idle, actions)

        Section(R.string.notif_settings_pause_title) {
            val paused = settings.pausedUntil
            if (paused != null) {
                Text(
                    stringResource(
                        R.string.notif_settings_paused_until,
                        formatMoment(paused, state.phoneZone),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("notif-settings:paused"),
                )
                Text(
                    stringResource(R.string.notif_settings_pause_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = actions.onResume, enabled = idle) {
                    Text(stringResource(R.string.notif_settings_resume))
                }
            } else {
                Text(
                    stringResource(R.string.notif_settings_pause_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Pauses.forEach { (label, duration) ->
                        ColaFilterChip(
                            selected = false,
                            onClick = { if (idle) actions.onPause(duration) },
                            label = stringResource(label),
                        )
                    }
                }
            }
        }

        QuietHoursCard(state, actions)
        CircleCard(state, actions)
        actions.onOpenNearby?.let { open ->
            ColaListItem(
                title = stringResource(R.string.notif_settings_nearby),
                supporting = stringResource(R.string.notif_settings_nearby_hint),
                icon = ColaIcons.Location,
                onClick = open,
                modifier = Modifier.testTag("notif-settings:nearby"),
            )
        }
        MutesCard(settings, idle, actions)
    }
}

@Composable
private fun ChannelsCard(
    settings: NotificationSettings,
    idle: Boolean,
    actions: NotificationSettingsActions,
) {
    val channels = settings.channels
    Section(R.string.notif_settings_channels) {
        // The server can carry no push yet: the switch is off and says why, instead of promising.
        ColaSwitchRow(
            title = stringResource(R.string.notif_settings_push),
            supporting =
                stringResource(
                    if (channels.pushAvailable) R.string.notif_settings_push_hint
                    else R.string.notif_settings_push_unavailable
                ),
            checked = channels.pushEnabled,
            onCheckedChange = actions.onPush,
            enabled = idle && (channels.pushAvailable || channels.pushEnabled),
            modifier = Modifier.testTag("notif-settings:push"),
        )
        ColaSwitchRow(
            title = stringResource(R.string.notif_settings_email),
            supporting =
                stringResource(
                    when {
                        !channels.emailAvailable -> R.string.notif_settings_email_unavailable
                        !channels.emailVerified -> R.string.notif_settings_email_unverified
                        else -> R.string.notif_settings_email_hint
                    }
                ),
            checked = channels.emailEnabled,
            onCheckedChange = actions.onEmail,
            enabled =
                idle &&
                    ((channels.emailAvailable && channels.emailVerified) || channels.emailEnabled),
            modifier = Modifier.testTag("notif-settings:email"),
        )
        ColaSwitchRow(
            title = stringResource(R.string.notif_settings_reminders),
            supporting = stringResource(R.string.notif_settings_reminders_hint),
            checked = settings.reminders,
            onCheckedChange = actions.onReminders,
            enabled = idle,
            modifier = Modifier.testTag("notif-settings:reminders"),
        )
    }
}

@Composable
private fun CategoriesCard(
    settings: NotificationSettings,
    idle: Boolean,
    actions: NotificationSettingsActions,
) {
    val channels = settings.channels
    val categories = settings.categories.filter { it.push.supported || it.email.supported }
    if (categories.isEmpty()) return
    Section(R.string.notif_settings_categories) {
        Text(
            stringResource(R.string.notif_settings_categories_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        categories.forEach { category ->
            CategoryRows(
                category,
                channels.pushAvailable,
                channels.emailAvailable && channels.emailVerified,
                idle,
                actions,
            )
        }
    }
}

@Composable
private fun CategoryRows(
    category: CategorySetting,
    pushAvailable: Boolean,
    emailUsable: Boolean,
    idle: Boolean,
    actions: NotificationSettingsActions,
) {
    Column(Modifier.testTag("notif-settings:category:${category.key}")) {
        Text(
            category.label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Spacing.s),
        )
        if (category.push.supported) {
            ColaSwitchRow(
                title = stringResource(R.string.notif_settings_by_push),
                checked = category.push.enabled,
                onCheckedChange = { actions.onCategoryPush(category.key, it) },
                enabled = idle && (pushAvailable || category.push.enabled),
                modifier = Modifier.testTag("notif-settings:category:${category.key}:push"),
            )
        }
        if (category.email.supported) {
            ColaSwitchRow(
                title = stringResource(R.string.notif_settings_by_email),
                checked = category.email.enabled,
                onCheckedChange = { actions.onCategoryEmail(category.key, it) },
                enabled = idle && (emailUsable || category.email.enabled),
                modifier = Modifier.testTag("notif-settings:category:${category.key}:email"),
            )
        }
    }
}

@Composable
private fun QuietHoursCard(
    state: NotificationSettingsUiState.Loaded,
    actions: NotificationSettingsActions,
) {
    val settings = state.settings
    val quiet = settings.quietHours
    val idle = !state.saving
    Section(R.string.notif_settings_quiet_title) {
        ColaSwitchRow(
            title = stringResource(R.string.notif_settings_quiet),
            supporting = stringResource(R.string.notif_settings_quiet_hint),
            checked = quiet.enabled,
            onCheckedChange = actions.onQuietEnabled,
            enabled = idle,
            modifier = Modifier.testTag("notif-settings:quiet"),
        )
        if (quiet.enabled || settings.timeZone != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                TimeButton(
                    R.string.notif_settings_quiet_from,
                    quiet.from,
                    idle,
                    actions.onPickQuietFrom,
                    Modifier.weight(1f).testTag("notif-settings:quiet-from"),
                )
                TimeButton(
                    R.string.notif_settings_quiet_to,
                    quiet.to,
                    idle,
                    actions.onPickQuietTo,
                    Modifier.weight(1f).testTag("notif-settings:quiet-to"),
                )
            }
            val zone = settings.timeZone
            val locale = LocalConfiguration.current.locales[0]
            Text(
                if (zone != null)
                    stringResource(
                        R.string.notif_settings_zone,
                        zone.getDisplayName(TextStyle.FULL, locale),
                    )
                else
                    stringResource(
                        R.string.notif_settings_zone_none,
                        state.phoneZone.getDisplayName(TextStyle.FULL, locale),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("notif-settings:zone"),
            )
            if (zone != null && zone != state.phoneZone) {
                TextButton(onClick = actions.onUsePhoneZone, enabled = idle) {
                    Text(
                        stringResource(
                            R.string.notif_settings_zone_use_phone,
                            state.phoneZone.getDisplayName(TextStyle.FULL, locale),
                        )
                    )
                }
            }
            ColaSwitchRow(
                title = stringResource(R.string.notif_settings_quiet_cancellations),
                supporting = stringResource(R.string.notif_settings_quiet_cancellations_hint),
                checked = quiet.allowCancellations,
                onCheckedChange = actions.onQuietCancellations,
                enabled = idle,
                modifier = Modifier.testTag("notif-settings:quiet-cancellations"),
            )
        }
    }
}

@Composable
private fun TimeButton(
    @StringRes label: Int,
    time: LocalTime,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val text = "%02d:%02d".format(time.hour, time.minute)
    ColaListItem(
        title = text,
        supporting = stringResource(label),
        onClick = if (enabled) onClick else null,
        modifier = modifier,
        trailing = {},
    )
}

@Composable
private fun CircleCard(
    state: NotificationSettingsUiState.Loaded,
    actions: NotificationSettingsActions,
) {
    val settings = state.settings
    val idle = !state.saving
    Section(R.string.notif_settings_circle_title) {
        Text(
            stringResource(R.string.notif_settings_circle_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CircleMode.Choices.forEach { mode ->
            ColaRadioRow(
                title = stringResource(mode.title()),
                supporting = stringResource(mode.hint()),
                selected = settings.circleMode == mode,
                onSelect = { actions.onCircle(mode) },
                enabled = idle,
                modifier = Modifier.testTag("notif-settings:circle:${mode.key}"),
            )
        }
        ColaSwitchRow(
            title = stringResource(R.string.notif_settings_considering),
            supporting = stringResource(R.string.notif_settings_considering_hint),
            checked = settings.considering,
            onCheckedChange = actions.onConsidering,
            enabled = idle,
            modifier = Modifier.testTag("notif-settings:considering"),
        )
        if (settings.circleMode == CircleMode.Selected || settings.circleMembers.isNotEmpty()) {
            Text(
                stringResource(R.string.notif_settings_members),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
            )
            if (settings.circleMembers.isEmpty()) {
                Text(
                    stringResource(R.string.notif_settings_members_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            settings.circleMembers.forEach { person ->
                RemovableRow(
                    text = person.displayName,
                    removeLabel =
                        stringResource(R.string.notif_settings_member_remove, person.displayName),
                    enabled = idle,
                    onRemove = { actions.onRemoveMember(person.id.value) },
                    modifier = Modifier.testTag("notif-settings:member:${person.username}"),
                )
            }
            AddByName(
                label = stringResource(R.string.notif_settings_member_add_label),
                action = stringResource(R.string.notif_settings_member_add),
                enabled = idle,
                onAdd = actions.onAddMember,
            )
        }
    }
}

@Composable
private fun MutesCard(
    settings: NotificationSettings,
    idle: Boolean,
    actions: NotificationSettingsActions,
) {
    Section(R.string.notif_settings_mutes_title) {
        Text(
            stringResource(R.string.notif_settings_mutes_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.mutes.isEmpty()) {
            Text(
                stringResource(R.string.notif_settings_mutes_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("notif-settings:mutes-none"),
            )
        }
        settings.mutes.forEach { mute ->
            val name = mute.label ?: stringResource(R.string.notif_settings_mute_unnamed)
            RemovableRow(
                text = "$name · ${stringResource(mute.kind.title())}",
                removeLabel = stringResource(R.string.notif_settings_mute_remove, name),
                removeText = stringResource(R.string.notif_settings_mute_return),
                enabled = idle,
                onRemove = { actions.onRemoveMute(mute) },
                modifier = Modifier.testTag("notif-settings:mute:${mute.id}"),
            )
        }
    }
}

@Composable
private fun RemovableRow(
    text: String,
    removeLabel: String,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    removeText: String = stringResource(R.string.notif_settings_remove),
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(
            onClick = onRemove,
            enabled = enabled,
            // The visible word is short; TalkBack is told whom it applies to instead of it.
            modifier = Modifier.semantics { contentDescription = removeLabel },
        ) {
            Text(removeText, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun AddByName(label: String, action: String, enabled: Boolean, onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            colors = colaTextFieldColors(),
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
            modifier = Modifier.weight(1f).testTag("notif-settings:add-name"),
        )
        Button(
            onClick = {
                onAdd(name)
                name = ""
            },
            enabled = enabled && name.isNotBlank(),
            modifier = Modifier.testTag("notif-settings:add"),
        ) {
            Text(action)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// This phone
// ---------------------------------------------------------------------------------------------

@Composable
private fun PhonePart(
    state: NotificationSettingsUiState.Loaded,
    actions: NotificationSettingsActions,
) {
    val device = state.device
    var explaining by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        PartHeading(R.string.notif_settings_phone, R.string.notif_settings_phone_hint)
        if (device == null) {
            Text(
                stringResource(R.string.notif_settings_phone_unknown),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        // Android's permission and switch: the first thing that decides whether anything is shown.
        when {
            device.permission == OsPermission.NotAsked ->
                ColaListItem(
                    title = stringResource(R.string.notif_settings_os_ask),
                    supporting = stringResource(R.string.notif_settings_os_ask_hint),
                    icon = ColaIcons.Notifications,
                    onClick = { explaining = true },
                    modifier = Modifier.testTag("notif-settings:os-ask"),
                )
            device.permission == OsPermission.Denied || !device.appEnabled ->
                ColaListItem(
                    title = stringResource(R.string.notif_settings_os_off),
                    supporting = stringResource(R.string.notif_settings_os_off_hint),
                    icon = ColaIcons.Notifications,
                    tone = HaloTone.Secondary,
                    action = ListItemAction.External,
                    onClick = actions.onOpenSystemSettings,
                    modifier = Modifier.testTag("notif-settings:os-off"),
                )
            else ->
                ColaListItem(
                    title = stringResource(R.string.notif_settings_os_on),
                    supporting = stringResource(R.string.notif_settings_os_on_hint),
                    icon = ColaIcons.Notifications,
                    modifier = Modifier.testTag("notif-settings:os-on"),
                )
        }
        val channelNames = device.channelsOff.map { stringResource(it.title) }
        if (channelNames.isNotEmpty() && device.appEnabled) {
            ColaListItem(
                title = stringResource(R.string.notif_settings_channels_off),
                supporting = channelNames.joinToString(", "),
                icon = ColaIcons.Notifications,
                tone = HaloTone.Secondary,
                action = ListItemAction.External,
                onClick = actions.onOpenSystemSettings,
                modifier = Modifier.testTag("notif-settings:channels-off"),
            )
        }
        // Delivery: the provider on this phone, and (later) this phone's registration.
        ColaListItem(
            title = stringResource(R.string.notif_settings_delivery),
            supporting =
                stringResource(
                    when (device.provider) {
                        PushAvailability.Available -> R.string.notif_settings_delivery_ready
                        PushAvailability.NoDistributor ->
                            R.string.notif_settings_delivery_no_distributor
                        PushAvailability.NotConfigured -> R.string.notif_settings_delivery_not_built
                        PushAvailability.Failed -> R.string.notif_settings_delivery_failed
                    }
                ),
            icon = ColaIcons.Devices,
            modifier = Modifier.testTag("notif-settings:delivery"),
        )
        Text(
            stringResource(R.string.notif_settings_phone_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.notif_settings_os_dialog_title)) },
            text = { Text(stringResource(R.string.notif_settings_os_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        explaining = false
                        actions.onAskPermission()
                    },
                    modifier = Modifier.testTag("notif-settings:os-allow"),
                ) {
                    Text(stringResource(R.string.notif_settings_os_dialog_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { explaining = false }) {
                    Text(stringResource(R.string.notif_settings_os_dialog_later))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Parts
// ---------------------------------------------------------------------------------------------

@Composable
private fun PartHeading(@StringRes title: Int, @StringRes hint: Int) {
    Column(
        Modifier.padding(top = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Eyebrow(stringResource(title), modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

private fun formatMoment(moment: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru")).withZone(zone).format(moment)

@StringRes
private fun CircleMode.title(): Int =
    when (this) {
        CircleMode.Friends -> R.string.notif_settings_circle_friends
        CircleMode.Follows -> R.string.notif_settings_circle_follows
        CircleMode.Selected -> R.string.notif_settings_circle_selected
        CircleMode.Off -> R.string.notif_settings_circle_off
        CircleMode.Unknown -> R.string.notif_settings_circle_unknown
    }

@StringRes
private fun CircleMode.hint(): Int =
    when (this) {
        CircleMode.Friends -> R.string.notif_settings_circle_friends_hint
        CircleMode.Follows -> R.string.notif_settings_circle_follows_hint
        CircleMode.Selected -> R.string.notif_settings_circle_selected_hint
        CircleMode.Off -> R.string.notif_settings_circle_off_hint
        CircleMode.Unknown -> R.string.notif_settings_circle_unknown
    }

@StringRes
private fun MuteKind.title(): Int =
    when (this) {
        MuteKind.Author -> R.string.notif_settings_mute_author
        MuteKind.Ride -> R.string.notif_settings_mute_ride
        MuteKind.Discussion -> R.string.notif_settings_mute_discussion
        MuteKind.Unknown -> R.string.notif_settings_mute_other
    }
