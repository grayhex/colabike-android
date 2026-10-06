package ru.colabike.app.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.ComponentCatalog
import ru.colabike.core.model.ComponentProblem

/** What the part form can ask for. */
data class PartEditorActions(
    val onBack: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onCategory: (String) -> Unit = {},
    val onName: (String) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onPrice: (String) -> Unit = {},
    val onUrl: (String) -> Unit = {},
    val onSection: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenGarage: () -> Unit = {},
)

private val ContentWidth = 600.dp

/** The form of a part of the build: where it goes, what it is, and what the owner says of it. */
@Composable
fun BikePartEditorScreen(state: PartEditorUiState, editing: Boolean, actions: PartEditorActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title =
                    stringResource(
                        if (editing) R.string.part_editor_edit else R.string.part_editor_new
                    ),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                PartEditorUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is PartEditorUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetryLoad, Modifier.fillMaxSize())
                PartEditorUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.part_unavailable_title),
                        message = stringResource(R.string.part_cannot_edit),
                        icon = ColaIcons.Bike,
                        actionLabel = stringResource(R.string.bike_open_garage),
                        onAction = actions.onOpenGarage,
                        modifier = Modifier.fillMaxSize().testTag("part-editor:unavailable"),
                    )
                is PartEditorUiState.Editing -> Form(state, editing, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(state: PartEditorUiState.Editing, editing: Boolean, actions: PartEditorActions) {
    val form = state.form
    val idle = !state.saving && !state.deleting
    LazyColumn(
        Modifier.fillMaxSize().imePadding().testTag("part-editor"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                ColaCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(Spacing.l),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Eyebrow(
                            stringResource(R.string.part_section_what),
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(R.string.part_field_section),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            listOf(
                                    ComponentCatalog.SECTION_BUILD to R.string.bike_build,
                                    ComponentCatalog.SECTION_ACCESSORIES to
                                        R.string.bike_accessories,
                                )
                                .forEach { (key, label) ->
                                    ColaFilterChip(
                                        selected = form.section == key,
                                        onClick = { if (idle) actions.onSection(key) },
                                        label = stringResource(label),
                                        modifier = Modifier.testTag("part-editor:section:$key"),
                                    )
                                }
                        }
                        Field(
                            R.string.part_field_category,
                            form.category,
                            actions.onCategory,
                            "category",
                            idle,
                            state.has(
                                ComponentProblem.NoCategory,
                                ComponentProblem.CategoryTooLong,
                            ),
                        )
                        if (state.suggestions.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                state.suggestions.forEach { suggestion ->
                                    ColaFilterChip(
                                        selected = false,
                                        onClick = { if (idle) actions.onCategory(suggestion) },
                                        label = suggestion,
                                        modifier =
                                            Modifier.testTag("part-editor:suggest:$suggestion"),
                                    )
                                }
                            }
                        }
                        Field(
                            R.string.part_field_name,
                            form.name,
                            actions.onName,
                            "name",
                            idle,
                            state.has(ComponentProblem.NoName, ComponentProblem.NameTooLong),
                        )
                    }
                }
                ColaCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(Spacing.l),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Eyebrow(
                            stringResource(R.string.part_section_details),
                            modifier = Modifier.semantics { heading() },
                        )
                        Field(
                            R.string.part_field_notes,
                            form.notes,
                            actions.onNotes,
                            "notes",
                            idle,
                            state.has(ComponentProblem.NotesTooLong),
                            singleLine = false,
                        )
                        Field(
                            R.string.part_field_price,
                            form.price,
                            actions.onPrice,
                            "price",
                            idle,
                            state.has(ComponentProblem.PriceInvalid),
                            keyboard = KeyboardType.Decimal,
                        )
                        Field(
                            R.string.part_field_url,
                            form.url,
                            actions.onUrl,
                            "url",
                            idle,
                            state.has(ComponentProblem.LinkInvalid),
                            keyboard = KeyboardType.Uri,
                            hint = R.string.bike_field_link_hint,
                        )
                    }
                }
                // Next to the button that was just pressed, as in the form of a bike.
                Column(
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    state.problems.forEach { problem ->
                        Text(
                            stringResource(problem.message()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("part-editor:problem"),
                        )
                    }
                    state.problem?.let {
                        Text(
                            it.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("part-editor:refused"),
                        )
                        if (state.canReload) {
                            TextButton(
                                onClick = actions.onReload,
                                modifier = Modifier.testTag("part-editor:reload"),
                            ) {
                                Text(stringResource(R.string.bike_reload))
                            }
                        }
                    }
                }
                Button(
                    onClick = actions.onSave,
                    enabled = idle,
                    modifier = Modifier.fillMaxWidth().testTag("part-editor:save"),
                ) {
                    Text(
                        stringResource(
                            if (state.saving) R.string.bike_saving else R.string.bike_save
                        )
                    )
                }
                if (editing) {
                    TextButton(
                        onClick = actions.onAskDelete,
                        enabled = idle,
                        modifier = Modifier.fillMaxWidth().testTag("part-editor:delete"),
                    ) {
                        Text(
                            stringResource(
                                if (state.deleting) R.string.bike_deleting else R.string.part_delete
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text(stringResource(R.string.part_delete_title)) },
            text = {
                Text(stringResource(R.string.part_delete_message, state.editing?.name.orEmpty()))
            },
            confirmButton = {
                TextButton(
                    onClick = actions.onConfirmDelete,
                    modifier = Modifier.testTag("part-editor:delete-confirm"),
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
                    modifier = Modifier.testTag("part-editor:delete-cancel"),
                ) {
                    Text(stringResource(R.string.bike_delete_cancel))
                }
            },
        )
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
        minLines = if (singleLine) 1 else 3,
        enabled = enabled,
        colors = colaTextFieldColors(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboard,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
        modifier = Modifier.fillMaxWidth().testTag("part-editor:$tag"),
    )
}

private fun PartEditorUiState.Editing.has(vararg found: ComponentProblem): Boolean = problems.any {
    it in found
}

private fun ComponentProblem.message(): Int =
    when (this) {
        ComponentProblem.NoCategory -> R.string.part_problem_no_category
        ComponentProblem.CategoryTooLong -> R.string.part_problem_category_long
        ComponentProblem.NoName -> R.string.part_problem_no_name
        ComponentProblem.NameTooLong -> R.string.part_problem_name_long
        ComponentProblem.NotesTooLong -> R.string.part_problem_notes_long
        ComponentProblem.LinkInvalid -> R.string.bike_problem_link
        ComponentProblem.PriceInvalid -> R.string.bike_problem_price
    }
