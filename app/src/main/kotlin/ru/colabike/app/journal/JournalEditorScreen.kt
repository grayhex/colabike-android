package ru.colabike.app.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaRadioRow
import ru.colabike.core.designsystem.component.ColaSwitchRow
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.component.journalKindLabel
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.JournalCatalog
import ru.colabike.core.model.JournalProblem
import ru.colabike.core.model.JournalRules
import ru.colabike.core.model.JournalStatus

/** What the form can ask for; the route wires each to the ViewModel. */
data class JournalEditorActions(
    val onBack: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onKind: (String) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onBody: (String) -> Unit = {},
    val onStatus: (JournalStatus) -> Unit = {},
    val onPublic: (Boolean) -> Unit = {},
    val onDate: (String) -> Unit = {},
    val onToday: () -> Unit = {},
    val onMileage: (String) -> Unit = {},
    val onInstallation: (String) -> Unit = {},
    val onComponent: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenGarage: () -> Unit = {},
)

private val ContentWidth = 600.dp

/**
 * The form of a journal entry: what it is about, the text, when and how far, the parts it names and
 * who sees it. Everything the server would refuse is found before a request and shown above the
 * button that was pressed.
 */
@Composable
fun JournalEditorScreen(
    state: JournalEditorUiState,
    editing: Boolean,
    actions: JournalEditorActions,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title =
                    stringResource(
                        if (editing) R.string.journal_editor_edit else R.string.journal_editor_new
                    ),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                JournalEditorUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is JournalEditorUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetryLoad, Modifier.fillMaxSize())
                JournalEditorUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.journal_unavailable_title),
                        message = stringResource(R.string.journal_cannot_edit),
                        icon = ColaIcons.Bike,
                        actionLabel = stringResource(R.string.bike_open_garage),
                        onAction = actions.onOpenGarage,
                        modifier = Modifier.fillMaxSize().testTag("journal-editor:unavailable"),
                    )
                is JournalEditorUiState.Editing -> Form(state, editing, actions)
            }
        }
    }
}

@Composable
private fun Form(
    state: JournalEditorUiState.Editing,
    editing: Boolean,
    actions: JournalEditorActions,
) {
    LazyColumn(
        Modifier.fillMaxSize().imePadding().testTag("journal-editor"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                MainCard(state, actions)
                WhenCard(state, actions)
                PartsCard(state, actions)
                AudienceCard(state, actions)
                // Next to the button that was just pressed: at the top of a form this long the
                // findings would be out of sight.
                Outcome(state, actions)
                Button(
                    onClick = actions.onSave,
                    enabled = !state.saving && !state.deleting,
                    modifier = Modifier.fillMaxWidth().testTag("journal-editor:save"),
                ) {
                    Text(
                        stringResource(
                            if (state.saving) R.string.journal_editor_saving
                            else R.string.journal_editor_save
                        )
                    )
                }
                if (editing) {
                    TextButton(
                        onClick = actions.onAskDelete,
                        enabled = !state.saving && !state.deleting,
                        modifier = Modifier.fillMaxWidth().testTag("journal-editor:delete"),
                    ) {
                        Text(
                            stringResource(
                                if (state.deleting) R.string.journal_deleting
                                else R.string.journal_delete
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
    if (state.confirmingDelete) {
        val title = state.editing?.summary?.title.orEmpty()
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text(stringResource(R.string.journal_delete_title)) },
            text = { Text(stringResource(R.string.journal_delete_message, title)) },
            confirmButton = {
                TextButton(
                    onClick = actions.onConfirmDelete,
                    modifier = Modifier.testTag("journal-editor:delete-confirm"),
                ) {
                    Text(
                        stringResource(R.string.bike_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = actions.onCancelDelete,
                    modifier = Modifier.testTag("journal-editor:delete-cancel"),
                ) {
                    Text(stringResource(R.string.bike_delete_cancel))
                }
            },
        )
    }
}

/** The form's findings and the server's refusal, announced when they appear. */
@Composable
private fun Outcome(state: JournalEditorUiState.Editing, actions: JournalEditorActions) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        state.problems.forEach { problem ->
            Text(
                stringResource(problem.message()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("journal-editor:problem"),
            )
        }
        state.problem?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("journal-editor:refused"),
            )
            if (state.canReload) {
                TextButton(
                    onClick = actions.onReload,
                    modifier = Modifier.testTag("journal-editor:reload"),
                ) {
                    Text(stringResource(R.string.journal_reload))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainCard(state: JournalEditorUiState.Editing, actions: JournalEditorActions) {
    val form = state.form
    val idle = !state.saving && !state.deleting
    Section(R.string.journal_section_main) {
        Text(
            stringResource(R.string.journal_field_kind),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            JournalCatalog.kinds.forEach { key ->
                ColaFilterChip(
                    selected = key == form.kind,
                    onClick = { if (idle) actions.onKind(key) },
                    label = journalKindLabel(key).orEmpty(),
                    modifier = Modifier.testTag("journal-editor:kind:$key"),
                )
            }
        }
        Field(
            R.string.journal_field_title,
            form.title,
            actions.onTitle,
            "title",
            idle,
            error = state.has(JournalProblem.TitleTooLong, JournalProblem.NoTitle),
        )
        Field(
            R.string.journal_field_body,
            form.body,
            actions.onBody,
            "body",
            idle,
            error = state.has(JournalProblem.BodyTooLong, JournalProblem.NoBody),
            singleLine = false,
            hint = R.string.journal_field_body_hint,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenCard(state: JournalEditorUiState.Editing, actions: JournalEditorActions) {
    val form = state.form
    val idle = !state.saving && !state.deleting
    Section(R.string.journal_section_when) {
        Field(
            R.string.journal_field_date,
            form.eventDate,
            actions.onDate,
            "date",
            idle,
            error = state.has(JournalProblem.DateInvalid),
            keyboard = KeyboardType.Number,
            hint = R.string.journal_field_date_hint,
        )
        TextButton(
            onClick = actions.onToday,
            enabled = idle,
            modifier = Modifier.testTag("journal-editor:today"),
        ) {
            Text(stringResource(R.string.journal_today))
        }
        Field(
            R.string.journal_field_mileage,
            form.mileage,
            actions.onMileage,
            "mileage",
            idle,
            error = state.has(JournalProblem.MileageInvalid),
            keyboard = KeyboardType.Number,
        )
        // How an installation went is said of a build only.
        if (form.kind == JournalCatalog.BUILD) {
            Text(
                stringResource(R.string.journal_install_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                JournalCatalog.installationResults.forEach { key ->
                    ColaFilterChip(
                        selected = key == form.installationResult,
                        onClick = { if (idle) actions.onInstallation(key) },
                        label = stringResource(installLabel(key)),
                        modifier = Modifier.testTag("journal-editor:install:$key"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PartsCard(state: JournalEditorUiState.Editing, actions: JournalEditorActions) {
    val idle = !state.saving && !state.deleting
    Section(R.string.journal_section_parts) {
        Text(
            stringResource(R.string.journal_parts_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.choices.isEmpty()) {
            Text(
                stringResource(R.string.journal_parts_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("journal-editor:parts-empty"),
            )
        } else {
            Text(
                stringResource(
                    R.string.journal_parts_chosen,
                    state.form.componentIds.size,
                    JournalRules.MAX_COMPONENTS,
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("journal-editor:parts-count"),
            )
            state.choices.forEach { part ->
                PartRow(
                    part = part,
                    checked = part.id in state.form.componentIds,
                    gone = state.bike.components.none { it.id == part.id },
                    enabled = idle,
                    onToggle = { actions.onComponent(part.id) },
                )
            }
        }
        if (state.has(JournalProblem.TooManyComponents)) {
            Text(
                stringResource(JournalProblem.TooManyComponents.message()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PartRow(
    part: BikeComponent,
    checked: Boolean,
    gone: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .testTag("journal-editor:part:${part.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(part.name, style = MaterialTheme.typography.bodyMedium)
            val note = if (gone) stringResource(R.string.journal_part_gone) else part.category
            if (note.isNotBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AudienceCard(state: JournalEditorUiState.Editing, actions: JournalEditorActions) {
    val form = state.form
    val idle = !state.saving && !state.deleting
    Section(R.string.journal_section_audience) {
        listOf(JournalStatus.Draft, JournalStatus.Published).forEach { status ->
            val draft = status == JournalStatus.Draft
            ColaRadioRow(
                title =
                    stringResource(
                        if (draft) R.string.journal_status_draft
                        else R.string.journal_status_published
                    ),
                supporting =
                    stringResource(
                        if (draft) R.string.journal_status_draft_hint
                        else R.string.journal_status_published_hint
                    ),
                selected = form.status == status,
                onSelect = { actions.onStatus(status) },
                enabled = idle,
                modifier =
                    Modifier.testTag(
                        "journal-editor:status:${if (draft) "draft" else "published"}"
                    ),
            )
        }
        ColaSwitchRow(
            title = stringResource(R.string.journal_public),
            supporting = stringResource(R.string.journal_public_hint),
            checked = form.isPublic,
            onCheckedChange = actions.onPublic,
            enabled = idle && form.status == JournalStatus.Published,
            modifier = Modifier.testTag("journal-editor:public"),
        )
        if (state.hiddenByBike) {
            Text(
                stringResource(R.string.journal_hidden_by_bike),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("journal-editor:hidden-by-bike"),
            )
        }
    }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    tag: String,
    enabled: Boolean,
    error: Boolean,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    hint: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = hint?.let { { Text(stringResource(it)) } },
        isError = error,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 8,
        enabled = enabled,
        colors = colaTextFieldColors(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboard,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
        modifier = Modifier.fillMaxWidth().testTag("journal-editor:$tag"),
    )
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Eyebrow(stringResource(title), modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

private fun JournalEditorUiState.Editing.has(vararg found: JournalProblem): Boolean = problems.any {
    it in found
}

/** The words for how an installation went; shared with the entry's page. */
internal fun installLabel(key: String): Int =
    when (key) {
        "direct" -> R.string.journal_install_direct
        "modified" -> R.string.journal_install_modified
        else -> R.string.journal_install_failed
    }

private fun JournalProblem.message(): Int =
    when (this) {
        JournalProblem.TitleTooLong -> R.string.journal_problem_title_long
        JournalProblem.BodyTooLong -> R.string.journal_problem_body_long
        JournalProblem.NoTitle -> R.string.journal_problem_no_title
        JournalProblem.NoBody -> R.string.journal_problem_no_body
        JournalProblem.MileageInvalid -> R.string.journal_problem_mileage
        JournalProblem.DateInvalid -> R.string.journal_problem_date
        JournalProblem.TooManyComponents -> R.string.journal_problem_parts
    }
