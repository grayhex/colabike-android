package ru.colabike.app.intents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import ru.colabike.app.R
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.rides.map.SketchRouteMaps
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaRadioRow
import ru.colabike.core.designsystem.component.ColaSelectField
import ru.colabike.core.designsystem.component.ColaSwitchRow
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.PillShape
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.IntentDraft
import ru.colabike.core.model.IntentFold
import ru.colabike.core.model.IntentProblem
import ru.colabike.core.model.IntentReadiness
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.IntentWindowDraft

/** What the form can ask for; the route turns a date or a time into the platform's dialog. */
data class IntentEditorActions(
    val onBack: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onReadiness: (IntentReadiness) -> Unit = {},
    val onAreaLabel: (String) -> Unit = {},
    val onOpenArea: () -> Unit = {},
    val onCancelArea: () -> Unit = {},
    val onConfirmArea: () -> Unit = {},
    val onLocateArea: () -> Unit = {},
    val onCancelLocation: () -> Unit = {},
    val onAreaDraftLabel: (String) -> Unit = {},
    val onAreaCenter: (GeoPoint) -> Unit = {},
    val onAreaRadius: (Int) -> Unit = {},
    val onRemoveAreaGeometry: () -> Unit = {},
    val onPurpose: (String) -> Unit = {},
    val onPace: (String) -> Unit = {},
    val onSurface: (String) -> Unit = {},
    val onMeetNewPeople: (Boolean) -> Unit = {},
    val onVisibility: (IntentVisibility) -> Unit = {},
    val onAllowSuggestions: (Boolean) -> Unit = {},
    val onAddWindow: () -> Unit = {},
    val onRemoveWindow: (Int) -> Unit = {},
    val onPickDate: (Int, LocalDate) -> Unit = { _, _ -> },
    val onPickStart: (Int, LocalTime) -> Unit = { _, _ -> },
    val onPickEnd: (Int, LocalTime) -> Unit = { _, _ -> },
    val onFold: (Int, Boolean, IntentFold) -> Unit = { _, _, _ -> },
    val onSave: () -> Unit = {},
    val onOpenIntents: () -> Unit = {},
    /** The platform's dialogs; the screen only says what it wants chosen. */
    val askDate: (LocalDate, (LocalDate) -> Unit) -> Unit = { _, _ -> },
    val askTime: (LocalTime, (LocalTime) -> Unit) -> Unit = { _, _ -> },
)

private val ContentWidth = 600.dp

/**
 * The form of an intention. The window and the area come first, prefilled; what makes it more exact
 * is folded away. Publishing to the community is a choice of its own, said in words next to the
 * choice: it is not what saving does by default.
 */
@Composable
fun IntentEditorScreen(
    state: IntentEditorUiState,
    editing: Boolean,
    actions: IntentEditorActions,
    maps: RouteMaps = SketchRouteMaps,
) {
    (state as? IntentEditorUiState.Editing)?.areaPicker?.let { IntentAreaPicker(it, maps, actions) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title =
                    stringResource(
                        if (editing) R.string.intent_editor_edit else R.string.intent_editor_new
                    ),
                subtitle = stringResource(R.string.intent_editor_subtitle),
                onBack = actions.onBack,
            )
        },
        // The main action stays on the bottom edge, over the keyboard and the gesture bar, wherever
        // the form is scrolled to.
        bottomBar = {
            if (state is IntentEditorUiState.Editing) PublishBar(state, editing, actions)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                IntentEditorUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is IntentEditorUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetryLoad, Modifier.fillMaxSize())
                IntentEditorUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.intent_unavailable_title),
                        message = stringResource(R.string.intent_cannot_edit),
                        icon = ColaIcons.Route,
                        actionLabel = stringResource(R.string.intent_open_list),
                        onAction = actions.onOpenIntents,
                        modifier = Modifier.fillMaxSize().testTag("intent-editor:unavailable"),
                    )
                is IntentEditorUiState.Editing -> Form(state, actions)
            }
        }
    }
}

@Composable
private fun Form(state: IntentEditorUiState.Editing, actions: IntentEditorActions) {
    val form = state.form
    LazyColumn(
        Modifier.fillMaxSize().testTag("intent-editor"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                WindowsCard(state, actions)
                AreaCard(state, actions)
                PurposeCard(state, actions)
                ReadinessCard(form.readiness, !state.saving, actions)
                MoreCard(state, actions)
                VisibilityCard(state, actions)
            }
        }
    }
}

/**
 * The bar at the bottom: what the form found wrong or the server refused (it is where the finger
 * is, not at the top of a form that has been scrolled away), and the one main action. A form that
 * will be seen by the community says "publish"; a private one only saves.
 */
@Composable
private fun PublishBar(
    state: IntentEditorUiState.Editing,
    editing: Boolean,
    actions: IntentEditorActions,
) {
    val label =
        when {
            state.saving -> R.string.intent_saving
            editing -> R.string.intent_save
            state.form.visibility == IntentVisibility.Community -> R.string.intent_publish
            else -> R.string.intent_save_plans
        }
    Box(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = ContentWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Outcome(state, actions)
            Button(
                onClick = actions.onSave,
                enabled = !state.saving,
                shape = PillShape,
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("intent-editor:save"),
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                Icon(
                    painterResource(ColaIcons.ArrowBack),
                    contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.m).size(24.dp).rotate(180f),
                )
            }
        }
    }
}

/** The form's findings and the server's refusal, announced when they appear. */
@Composable
private fun Outcome(state: IntentEditorUiState.Editing, actions: IntentEditorActions) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        state.problems.forEach { problem ->
            Text(
                stringResource(problem.message()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("intent-editor:problem"),
            )
        }
        state.problem?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("intent-editor:refused"),
            )
            if (state.canReload) {
                TextButton(
                    onClick = actions.onReload,
                    modifier = Modifier.testTag("intent-editor:reload"),
                ) {
                    Text(stringResource(R.string.intent_reload))
                }
            }
        }
    }
}

@Composable
private fun WindowsCard(state: IntentEditorUiState.Editing, actions: IntentEditorActions) {
    val form = state.form
    val idle = !state.saving
    FormSection(R.string.intent_when) {
        form.windows.forEachIndexed { index, window ->
            WindowRow(index, window, form.windows.size > 1, idle, state.problems, actions)
        }
        Text(
            stringResource(R.string.intent_zone, form.timeZone.id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("intent-editor:zone"),
        )
        if (form.windows.size < IntentDraft.MAX_WINDOWS) {
            TextButton(
                onClick = actions.onAddWindow,
                enabled = idle,
                modifier = Modifier.testTag("intent-editor:add-window"),
            ) {
                Icon(
                    painterResource(ColaIcons.AddCircle),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    stringResource(R.string.intent_add_window),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }
    }
}

@Composable
private fun WindowRow(
    index: Int,
    window: IntentWindowDraft,
    removable: Boolean,
    enabled: Boolean,
    problems: List<IntentProblem>,
    actions: IntentEditorActions,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val date = window.start.toLocalDate()
    val nextDay = window.end.toLocalDate() != date
    val times: @Composable (Modifier) -> Unit = { modifier ->
        ColaSelectField(
            text = stringResource(R.string.intent_from, clock.format(window.start)),
            icon = ColaIcons.Schedule,
            onClick = {
                actions.askTime(window.start.toLocalTime()) { actions.onPickStart(index, it) }
            },
            enabled = enabled,
            modifier = modifier.testTag("intent-editor:from:$index"),
        )
        ColaSelectField(
            text = stringResource(R.string.intent_to, clock.format(window.end)),
            icon = ColaIcons.Schedule,
            onClick = {
                actions.askTime(window.end.toLocalTime()) { actions.onPickEnd(index, it) }
            },
            enabled = enabled,
            modifier = modifier.testTag("intent-editor:to:$index"),
        )
    }
    Column(
        Modifier.testTag("intent-editor:window:$index"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ColaSelectField(
            text = day.format(date),
            icon = ColaIcons.Calendar,
            onClick = { actions.askDate(date) { actions.onPickDate(index, it) } },
            enabled = enabled,
            dropdown = true,
            modifier = Modifier.testTag("intent-editor:date:$index"),
        )
        // Two fields side by side; at a large system font each needs the whole width.
        if (LocalDensity.current.fontScale > LargeFont) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                times(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                times(Modifier.weight(1f))
            }
        }
        if (removable) {
            TextButton(
                onClick = { actions.onRemoveWindow(index) },
                enabled = enabled,
                modifier = Modifier.testTag("intent-editor:remove-window:$index"),
            ) {
                Text(stringResource(R.string.intent_remove_window))
            }
        }
        if (nextDay) {
            Text(
                stringResource(R.string.intent_ends_next_day, day.format(window.end.toLocalDate())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (problems.any { it is IntentProblem.TimeIsRepeated && it.index == index }) {
            RepeatedHour(index, window, enabled, actions)
        }
    }
}

/** The night the clocks go back: the same time happens twice and the person says which. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatedHour(
    index: Int,
    window: IntentWindowDraft,
    enabled: Boolean,
    actions: IntentEditorActions,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        listOf(true to window.startFold, false to window.endFold).forEach { (start, chosen) ->
            Text(
                stringResource(if (start) R.string.intent_fold_start else R.string.intent_fold_end),
                style = MaterialTheme.typography.bodySmall,
            )
            IntentFold.entries.forEach { fold ->
                ColaFilterChip(
                    selected = chosen == fold,
                    onClick = { if (enabled) actions.onFold(index, start, fold) },
                    label =
                        stringResource(
                            if (fold == IntentFold.Earlier) R.string.intent_fold_earlier
                            else R.string.intent_fold_later
                        ),
                    modifier = Modifier.testTag("intent-editor:fold:$index:$start:${fold.name}"),
                )
            }
        }
    }
}

@Composable
private fun AreaCard(state: IntentEditorUiState.Editing, actions: IntentEditorActions) {
    val focus = LocalFocusManager.current
    val form = state.form
    FormSection(R.string.intent_where) {
        OutlinedTextField(
            value = form.areaLabel,
            onValueChange = actions.onAreaLabel,
            placeholder = { Text(stringResource(R.string.intent_area_label)) },
            leadingIcon = { Icon(painterResource(ColaIcons.Location), contentDescription = null) },
            supportingText = { Text(stringResource(R.string.intent_area_hint)) },
            singleLine = true,
            enabled = !state.saving,
            shape = MaterialTheme.shapes.small,
            colors = colaTextFieldColors(),
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("intent-editor:area"),
        )
        TextButton(
            onClick = {
                focus.clearFocus()
                actions.onOpenArea()
            },
            enabled = !state.saving,
            modifier = Modifier.testTag("intent-editor:choose-area"),
        ) {
            Text(
                stringResource(
                    if (form.base.area == null) R.string.intent_area_choose
                    else R.string.intent_area_edit
                )
            )
        }
        form.base.area?.let {
            Text(
                stringResource(R.string.intent_area_selected, areaRadiusKm(it.radiusM)),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = actions.onRemoveAreaGeometry,
                enabled = !state.saving,
                modifier = Modifier.testTag("intent-editor:remove-geometry"),
            ) {
                Text(stringResource(R.string.intent_area_remove_geometry))
            }
        }
    }
}

@Composable
private fun PurposeCard(state: IntentEditorUiState.Editing, actions: IntentEditorActions) {
    FormSection(R.string.nearby_purpose) {
        Chooser(
            keys = IntentDraft.purposes,
            selected = state.form.purpose,
            label = ::purposeLabel,
            icon = ::purposeIcon,
            tag = "purpose",
            enabled = !state.saving,
            onChoose = actions.onPurpose,
        )
    }
}

@Composable
private fun ReadinessCard(
    readiness: IntentReadiness,
    enabled: Boolean,
    actions: IntentEditorActions,
) {
    FormSection(R.string.intent_readiness) {
        ChoiceCard {
            IntentReadiness.entries.forEachIndexed { index, value ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ColaRadioRow(
                    title =
                        stringResource(
                            if (value == IntentReadiness.Ready) R.string.intent_ready
                            else R.string.intent_considering
                        ),
                    supporting =
                        stringResource(
                            if (value == IntentReadiness.Ready) R.string.intent_ready_hint
                            else R.string.intent_considering_hint
                        ),
                    selected = readiness == value,
                    onSelect = { actions.onReadiness(value) },
                    enabled = enabled,
                    modifier =
                        Modifier.testTag("intent-editor:readiness:${value.name.lowercase()}"),
                )
            }
        }
    }
}

/** The details that are folded away until asked for. */
@Composable
private fun MoreCard(state: IntentEditorUiState.Editing, actions: IntentEditorActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    FormSection(R.string.intent_more) {
        TextButton(
            onClick = { open = !open },
            modifier = Modifier.testTag("intent-editor:more"),
        ) {
            Text(stringResource(if (open) R.string.intent_more_hide else R.string.intent_more_show))
        }
        if (open) {
            Text(
                stringResource(R.string.nearby_pace),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Chooser(
                keys = IntentDraft.paces,
                selected = form.pace,
                label = ::paceLabel,
                tag = "pace",
                enabled = !state.saving,
                onChoose = actions.onPace,
            )
            Text(
                stringResource(R.string.nearby_surface),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
            )
            Chooser(
                keys = IntentDraft.surfaces,
                selected = form.surface,
                label = ::surfaceLabel,
                tag = "surface",
                enabled = !state.saving,
                onChoose = actions.onSurface,
            )
            ChoiceCard {
                ColaSwitchRow(
                    title = stringResource(R.string.intent_meet_new),
                    supporting = stringResource(R.string.intent_meet_new_hint),
                    checked = form.meetNewPeople,
                    onCheckedChange = actions.onMeetNewPeople,
                    enabled = !state.saving,
                    modifier = Modifier.testTag("intent-editor:meet-new"),
                )
            }
        }
    }
}

@Composable
private fun VisibilityCard(state: IntentEditorUiState.Editing, actions: IntentEditorActions) {
    val form = state.form
    FormSection(R.string.intent_visibility) {
        ChoiceCard {
            IntentVisibility.entries.forEachIndexed { index, value ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ColaRadioRow(
                    title =
                        stringResource(
                            if (value == IntentVisibility.Private)
                                R.string.intent_visibility_private
                            else R.string.intent_visibility_community
                        ),
                    supporting =
                        stringResource(
                            if (value == IntentVisibility.Private)
                                R.string.intent_visibility_private_hint
                            else R.string.intent_visibility_community_hint
                        ),
                    selected = form.visibility == value,
                    onSelect = { actions.onVisibility(value) },
                    enabled = !state.saving,
                    modifier =
                        Modifier.testTag("intent-editor:visibility:${value.name.lowercase()}"),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ColaSwitchRow(
                title = stringResource(R.string.intent_suggestions),
                supporting = stringResource(R.string.intent_suggestions_hint),
                checked = form.allowSuggestions,
                onCheckedChange = actions.onAllowSuggestions,
                enabled = !state.saving,
                modifier = Modifier.testTag("intent-editor:suggestions"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chooser(
    keys: List<String>,
    selected: String?,
    label: (String) -> Int,
    tag: String,
    enabled: Boolean,
    onChoose: (String) -> Unit,
    icon: ((String) -> Int)? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        keys.forEach { key ->
            ColaFilterChip(
                selected = key == selected,
                onClick = { if (enabled) onChoose(key) },
                label = stringResource(label(key)),
                icon = icon?.invoke(key),
                modifier = Modifier.testTag("intent-editor:$tag:$key"),
            )
        }
    }
}

/** What a purpose looks like on its chip. */
private fun purposeIcon(key: String): Int =
    when (key) {
        "leisure" -> ColaIcons.Forest
        "social" -> ColaIcons.Chat
        "training" -> ColaIcons.Fitness
        "exploration" -> ColaIcons.Map
        else -> ColaIcons.Landscape
    }

/** Choices that go together, in one card, a hairline between them. */
@Composable
private fun ChoiceCard(content: @Composable () -> Unit) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs)) { content() }
    }
}

/** A part of the form: its caption in capitals, and what is in it, on the canvas itself. */
@Composable
private fun FormSection(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Eyebrow(stringResource(title), modifier = Modifier.semantics { heading() })
        content()
    }
}

/** From this font scale on, the two time fields stand one under the other. */
private const val LargeFont = 1.3f

private fun IntentProblem.message(): Int =
    when (this) {
        IntentProblem.NoWindows -> R.string.intent_problem_no_windows
        IntentProblem.NoArea -> R.string.intent_problem_no_area
        IntentProblem.AreaTooLong -> R.string.intent_problem_area_long
        is IntentProblem.EndsBeforeStart -> R.string.intent_problem_ends_before
        is IntentProblem.TooLong -> R.string.intent_problem_too_long
        is IntentProblem.InThePast -> R.string.intent_problem_past
        is IntentProblem.TooFarAhead -> R.string.intent_problem_far
        is IntentProblem.TimeDoesNotExist -> R.string.intent_problem_skipped
        is IntentProblem.TimeIsRepeated -> R.string.intent_problem_repeated
        IntentProblem.Overlap -> R.string.intent_problem_overlap
    }
