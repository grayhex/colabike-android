package ru.colabike.app.bikes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.app.catalog.BuiltinCatalog
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaChoice
import ru.colabike.core.designsystem.component.ColaComboField
import ru.colabike.core.designsystem.component.ColaDropdownField
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
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeProblem
import ru.colabike.core.model.CatalogOption
import ru.colabike.core.model.ClassificationDraft
import ru.colabike.core.model.PriceVisibility
import ru.colabike.core.model.SiteCatalog

/** What the form can ask for; the route wires each to the ViewModel. */
data class BikeEditorActions(
    val onBack: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onBrand: (String) -> Unit = {},
    val onModel: (String) -> Unit = {},
    val onTrim: (String) -> Unit = {},
    val onYear: (String) -> Unit = {},
    val onCategory: (String) -> Unit = {},
    val onSubtype: (String) -> Unit = {},
    val onSuspension: (String) -> Unit = {},
    val onConstruction: (String) -> Unit = {},
    val onUse: (String) -> Unit = {},
    val onElectric: (Boolean) -> Unit = {},
    val onFatbike: (Boolean) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onColor: (String) -> Unit = {},
    val onSize: (String) -> Unit = {},
    val onWeight: (String) -> Unit = {},
    val onMileage: (String) -> Unit = {},
    val onLink: (String) -> Unit = {},
    val onPrice: (String) -> Unit = {},
    val onPriceVisibility: (PriceVisibility) -> Unit = {},
    val onFormer: (Boolean) -> Unit = {},
    val onPublic: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenGarage: () -> Unit = {},
)

private val ContentWidth = 600.dp

/**
 * The form of a bike. The name, the year and the type come first, what makes the page richer after
 * them; showing the bike to everyone is a choice of its own, said in words next to the choice and
 * never what saving does by default.
 */
@Composable
fun BikeEditorScreen(
    state: BikeEditorUiState,
    editing: Boolean,
    actions: BikeEditorActions,
    catalog: SiteCatalog = BuiltinCatalog.value,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title =
                    stringResource(
                        if (editing) R.string.bike_editor_edit else R.string.bike_editor_new
                    ),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                BikeEditorUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is BikeEditorUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetryLoad, Modifier.fillMaxSize())
                BikeEditorUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.bike_unavailable_title),
                        message = stringResource(R.string.bike_cannot_edit),
                        icon = ColaIcons.Bike,
                        actionLabel = stringResource(R.string.bike_open_garage),
                        onAction = actions.onOpenGarage,
                        modifier = Modifier.fillMaxSize().testTag("bike-editor:unavailable"),
                    )
                is BikeEditorUiState.Editing -> Form(state, editing, actions, catalog)
            }
        }
    }
}

@Composable
private fun Form(
    state: BikeEditorUiState.Editing,
    editing: Boolean,
    actions: BikeEditorActions,
    catalog: SiteCatalog,
) {
    LazyColumn(
        Modifier.fillMaxSize().imePadding().testTag("bike-editor"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                MainCard(state, actions, catalog)
                TypeCard(state, actions, catalog)
                DetailsCard(state, actions, catalog)
                PriceCard(state, actions)
                AudienceCard(state, actions)
                // Next to the button that was just pressed: at the top of a form this long the
                // findings would be out of sight.
                Outcome(state, actions)
                Button(
                    onClick = actions.onSave,
                    enabled = !state.saving && !state.deleting,
                    modifier = Modifier.fillMaxWidth().testTag("bike-editor:save"),
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
                        enabled = !state.saving && !state.deleting,
                        modifier = Modifier.fillMaxWidth().testTag("bike-editor:delete"),
                    ) {
                        Text(
                            stringResource(
                                if (state.deleting) R.string.bike_deleting else R.string.bike_delete
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
    if (state.confirmingDelete) {
        val name = state.editing?.summary?.name.orEmpty()
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text(stringResource(R.string.bike_delete_title)) },
            text = { Text(stringResource(R.string.bike_delete_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = actions.onConfirmDelete,
                    modifier = Modifier.testTag("bike-editor:delete-confirm"),
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
                    modifier = Modifier.testTag("bike-editor:delete-cancel"),
                ) {
                    Text(stringResource(R.string.bike_delete_cancel))
                }
            },
        )
    }
}

/** The form's findings and the server's refusal, announced when they appear. */
@Composable
private fun Outcome(state: BikeEditorUiState.Editing, actions: BikeEditorActions) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        state.problems.forEach { problem ->
            Text(
                stringResource(problem.message()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("bike-editor:problem"),
            )
        }
        state.problem?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("bike-editor:refused"),
            )
            if (state.canReload) {
                TextButton(
                    onClick = actions.onReload,
                    modifier = Modifier.testTag("bike-editor:reload"),
                ) {
                    Text(stringResource(R.string.bike_reload))
                }
            }
        }
    }
}

@Composable
internal fun MainCard(
    state: BikeEditorUiState.Editing,
    actions: BikeEditorActions,
    catalog: SiteCatalog,
) {
    val form = state.form
    val idle = !state.saving && !state.deleting
    Section(R.string.bike_section_main) {
        Field(
            R.string.bike_field_name,
            form.name,
            actions.onName,
            "name",
            idle,
            state.has(BikeProblem.NoName, BikeProblem.NameTooLong),
        )
        // The brand and the model are the site's lists, typed into: what is not in them is as good.
        Combo(
            R.string.bike_field_brand,
            form.brand,
            actions.onBrand,
            "brand",
            idle,
            state.has(BikeProblem.BrandTooLong),
            suggestions = catalog.brandSuggestions(form.brand).withoutExact(form.brand),
        )
        Combo(
            R.string.bike_field_model,
            form.model,
            actions.onModel,
            "model",
            idle,
            state.has(BikeProblem.ModelTooLong),
            suggestions = catalog.modelSuggestions(form.brand, form.model).withoutExact(form.model),
        )
        FieldPair(
            first = {
                Field(
                    R.string.bike_field_trim,
                    form.trim,
                    actions.onTrim,
                    "trim",
                    idle,
                    state.has(BikeProblem.TrimTooLong),
                )
            },
            second = {
                Field(
                    R.string.bike_field_year,
                    form.year,
                    actions.onYear,
                    "year",
                    idle,
                    state.has(BikeProblem.NoYear, BikeProblem.YearOutOfRange),
                    keyboard = KeyboardType.Number,
                )
            },
        )
    }
}

/** The kind of bike and its subtype side by side; the rest of the type is one tap away. */
@Composable
internal fun TypeCard(
    state: BikeEditorUiState.Editing,
    actions: BikeEditorActions,
    catalog: SiteCatalog,
) {
    val type = state.form.classification
    val idle = !state.saving && !state.deleting
    val types = catalog.classification
    // What the bike already says stays in sight; the form starts shut only when it says nothing.
    var more by rememberSaveable { mutableStateOf(type.hasFeatures()) }
    Section(R.string.bike_section_type) {
        FieldPair(
            // A half of a phone's width cannot show "Шоссе / гравел" beside the chevron.
            sideBySideFrom = TypePairWidth,
            first = {
                ColaDropdownField(
                    label = stringResource(R.string.bike_type_category),
                    value = type.category.ifEmpty { null },
                    choices =
                        types.categories
                            .map { ColaChoice(it.key, it.name) }
                            .keeping(type.category) {
                                BikeLabels.category(it) ?: it
                            },
                    onChoose = { key -> key?.let(actions.onCategory) },
                    enabled = idle,
                    isError = state.has(BikeProblem.NoCategory),
                    modifier = Modifier.testTag("bike-editor:category"),
                )
            },
            second = {
                ColaDropdownField(
                    label = stringResource(R.string.bike_type_subtype),
                    value = type.subtype,
                    choices =
                        types
                            .subtypesOf(type.category)
                            .map { ColaChoice(it.key, it.name) }
                            .keeping(type.subtype.orEmpty(), BikeLabels::subtype),
                    // Choosing the one that is chosen again would take it back: it is left alone.
                    onChoose = { key ->
                        if (key == null) type.subtype?.let(actions.onSubtype)
                        else if (key != type.subtype) actions.onSubtype(key)
                    },
                    enabled = idle && types.subtypesOf(type.category).isNotEmpty(),
                    clearLabel = stringResource(R.string.bike_type_none),
                    modifier = Modifier.testTag("bike-editor:subtype"),
                )
            },
        )
        if (state.has(BikeProblem.NoCategory)) {
            Text(
                stringResource(BikeProblem.NoCategory.message()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Disclosure(
            title = stringResource(R.string.bike_type_features),
            summary = type.featuresSummary(),
            expanded = more,
            onToggle = { more = !more },
            modifier = Modifier.testTag("bike-editor:features"),
        )
        if (more) {
            Chooser(
                title = R.string.bike_type_suspension,
                keys = types.suspensions.map { it.key },
                selected = setOfNotNull(type.suspension),
                label = { key -> types.suspensions.nameOf(key) ?: BikeLabels.suspension(key) },
                tag = "suspension",
                enabled = idle,
                onChoose = actions.onSuspension,
            )
            Chooser(
                title = R.string.bike_type_construction,
                keys = types.constructions.map { it.key },
                selected = setOfNotNull(type.construction),
                label = { key -> types.constructions.nameOf(key) ?: BikeLabels.construction(key) },
                tag = "construction",
                enabled = idle,
                onChoose = actions.onConstruction,
            )
            Chooser(
                title = R.string.bike_type_uses,
                keys = types.uses.map { it.key },
                selected = type.uses.toSet(),
                label = { key -> types.uses.nameOf(key) ?: BikeLabels.use(key) },
                tag = "use",
                enabled = idle,
                onChoose = actions.onUse,
            )
            ColaSwitchRow(
                title = stringResource(R.string.bike_type_electric),
                checked = type.electric,
                onCheckedChange = actions.onElectric,
                enabled = idle,
                modifier = Modifier.testTag("bike-editor:electric"),
            )
            ColaSwitchRow(
                title = stringResource(R.string.bike_type_fatbike),
                checked = type.fatbike,
                onCheckedChange = actions.onFatbike,
                enabled = idle,
                modifier = Modifier.testTag("bike-editor:fatbike"),
            )
        }
    }
}

@Composable
internal fun DetailsCard(
    state: BikeEditorUiState.Editing,
    actions: BikeEditorActions,
    catalog: SiteCatalog,
) {
    val form = state.form
    val idle = !state.saving && !state.deleting
    Section(R.string.bike_section_details) {
        Field(
            R.string.bike_field_description,
            form.description,
            actions.onDescription,
            "description",
            idle,
            state.has(BikeProblem.DescriptionTooLong),
            singleLine = false,
        )
        Field(
            R.string.bike_field_color,
            form.color,
            actions.onColor,
            "color",
            idle,
            state.has(BikeProblem.ColorTooLong),
        )
        Combo(
            R.string.bike_field_size,
            form.size,
            actions.onSize,
            "size",
            idle,
            state.has(BikeProblem.SizeTooLong),
            // The frame sizes of the site, all of them while nothing is typed.
            suggestions =
                catalog.sizes
                    .filter { form.size.isBlank() || it.contains(form.size.trim(), true) }
                    .withoutExact(form.size),
        )
        Field(
            R.string.bike_field_weight,
            form.weight,
            actions.onWeight,
            "weight",
            idle,
            state.has(BikeProblem.WeightInvalid),
            keyboard = KeyboardType.Decimal,
        )
        Field(
            R.string.bike_field_mileage,
            form.mileage,
            actions.onMileage,
            "mileage",
            idle,
            state.has(BikeProblem.MileageInvalid),
            keyboard = KeyboardType.Number,
        )
        Field(
            R.string.bike_field_link,
            form.manufacturerUrl,
            actions.onLink,
            "link",
            idle,
            state.has(BikeProblem.LinkInvalid),
            keyboard = KeyboardType.Uri,
            hint = R.string.bike_field_link_hint,
        )
    }
}

@Composable
internal fun PriceCard(state: BikeEditorUiState.Editing, actions: BikeEditorActions) {
    val form = state.form
    val idle = !state.saving && !state.deleting
    val shown = form.priceVisibility
    Section(R.string.bike_section_price) {
        Field(
            R.string.bike_field_price,
            form.price,
            actions.onPrice,
            "price",
            idle,
            state.has(BikeProblem.PriceInvalid),
            keyboard = KeyboardType.Decimal,
            hint = R.string.bike_field_price_hint,
        )
        ColaSwitchRow(
            title = stringResource(R.string.bike_price_show_bike),
            checked = shown.bike,
            onCheckedChange = { actions.onPriceVisibility(shown.copy(bike = it)) },
            enabled = idle,
            modifier = Modifier.testTag("bike-editor:show-bike-price"),
        )
        ColaSwitchRow(
            title = stringResource(R.string.bike_price_show_components),
            checked = shown.components,
            onCheckedChange = { actions.onPriceVisibility(shown.copy(components = it)) },
            enabled = idle,
            modifier = Modifier.testTag("bike-editor:show-component-prices"),
        )
        ColaSwitchRow(
            title = stringResource(R.string.bike_price_show_accessories),
            checked = shown.accessories,
            onCheckedChange = { actions.onPriceVisibility(shown.copy(accessories = it)) },
            enabled = idle,
            modifier = Modifier.testTag("bike-editor:show-accessory-prices"),
        )
    }
}

@Composable
internal fun AudienceCard(state: BikeEditorUiState.Editing, actions: BikeEditorActions) {
    val form = state.form
    val idle = !state.saving && !state.deleting
    Section(R.string.bike_section_audience) {
        listOf(false, true).forEach { public ->
            ColaRadioRow(
                title =
                    stringResource(
                        if (public) R.string.bike_audience_public
                        else R.string.bike_audience_private
                    ),
                supporting =
                    stringResource(
                        if (public) R.string.bike_audience_public_hint
                        else R.string.bike_audience_private_hint
                    ),
                selected = form.isPublic == public,
                onSelect = { actions.onPublic(public) },
                enabled = idle,
                modifier =
                    Modifier.testTag("bike-editor:audience:${if (public) "public" else "private"}"),
            )
        }
        ColaSwitchRow(
            title = stringResource(R.string.bike_former),
            supporting = stringResource(R.string.bike_former_hint),
            checked = form.isFormer,
            onCheckedChange = actions.onFormer,
            enabled = idle,
            modifier = Modifier.testTag("bike-editor:former"),
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
        modifier = Modifier.fillMaxWidth().testTag("bike-editor:$tag"),
    )
}

@Composable
internal fun Combo(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    tag: String,
    enabled: Boolean,
    error: Boolean,
    suggestions: List<String>,
) {
    ColaComboField(
        label = stringResource(label),
        value = value,
        onValueChange = onChange,
        suggestions = suggestions,
        enabled = enabled,
        isError = error,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag("bike-editor:$tag"),
    )
}

/**
 * Two fields side by side; with a large font, or in a width where a half of it cannot show the
 * words of a list ([sideBySideFrom]), one under the other, whole.
 */
@Composable
internal fun FieldPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    sideBySideFrom: Dp = 0.dp,
) {
    BoxWithConstraints {
        if (LocalDensity.current.fontScale >= LargeFont || maxWidth < sideBySideFrom)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                first()
                second()
            }
        else
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
    }
}

/**
 * A line that opens and shuts what follows it; [summary] says what is in there while it is shut.
 */
@Composable
internal fun Disclosure(
    title: String,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state =
        stringResource(if (expanded) R.string.bike_type_expanded else R.string.bike_type_collapsed)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!expanded && summary != null)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        Icon(
            painterResource(if (expanded) ColaIcons.ArrowUp else ColaIcons.ArrowDown),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What the person typed leaves the list: a line equal to the text is not worth offering. */
private fun List<String>.withoutExact(typed: String): List<String> = filterNot {
    it.equals(typed.trim(), ignoreCase = true)
}

/** A value the bike has that the list no longer knows stays a choice: an edit must not drop it. */
private fun List<ColaChoice>.keeping(key: String, label: (String) -> String): List<ColaChoice> =
    if (key.isEmpty() || any { it.key == key }) this else this + ColaChoice(key, label(key))

private fun List<CatalogOption>.nameOf(key: String): String? = firstOrNull { it.key == key }?.name

private fun ClassificationDraft.hasFeatures(): Boolean =
    suspension != null || construction != null || uses.isNotEmpty() || electric || fatbike

private fun ClassificationDraft.featuresSummary(): String? =
    listOfNotNull(
            suspension?.let(BikeLabels::suspension),
            construction?.let(BikeLabels::construction),
            *uses.map(BikeLabels::use).toTypedArray(),
            "E-bike".takeIf { electric },
            "Fatbike".takeIf { fatbike },
        )
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

/** The width of a form from which the kind of bike and its subtype stand side by side. */
private val TypePairWidth = 420.dp

/** From this font scale on the pairs of fields stand one under the other. */
private const val LargeFont = 1.3f

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Chooser(
    title: Int,
    keys: List<String>,
    selected: Set<String>,
    label: (String) -> String,
    tag: String,
    enabled: Boolean,
    onChoose: (String) -> Unit,
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
                onClick = { if (enabled) onChoose(key) },
                label = label(key),
                modifier = Modifier.testTag("bike-editor:$tag:$key"),
            )
        }
    }
}

@Composable
internal fun Section(title: Int, content: @Composable () -> Unit) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Eyebrow(
                stringResource(title),
                maxLines = 2,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

internal fun BikeEditorUiState.Editing.has(vararg found: BikeProblem): Boolean = problems.any {
    it in found
}

internal fun BikeProblem.message(): Int =
    when (this) {
        BikeProblem.NoName -> R.string.bike_problem_no_name
        BikeProblem.NameTooLong -> R.string.bike_problem_name_long
        BikeProblem.BrandTooLong -> R.string.bike_problem_brand_long
        BikeProblem.ModelTooLong -> R.string.bike_problem_model_long
        BikeProblem.TrimTooLong -> R.string.bike_problem_trim_long
        BikeProblem.NoYear -> R.string.bike_problem_no_year
        BikeProblem.YearOutOfRange -> R.string.bike_problem_year
        BikeProblem.NoCategory -> R.string.bike_problem_no_category
        BikeProblem.DescriptionTooLong -> R.string.bike_problem_description_long
        BikeProblem.ColorTooLong -> R.string.bike_problem_color_long
        BikeProblem.SizeTooLong -> R.string.bike_problem_size_long
        BikeProblem.WeightInvalid -> R.string.bike_problem_weight
        BikeProblem.MileageInvalid -> R.string.bike_problem_mileage
        BikeProblem.LinkInvalid -> R.string.bike_problem_link
        BikeProblem.PriceInvalid -> R.string.bike_problem_price
    }
