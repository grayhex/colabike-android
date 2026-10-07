package ru.colabike.app.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import ru.colabike.app.R
import ru.colabike.app.catalog.BuiltinCatalog
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaComboField
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeRules
import ru.colabike.core.model.BuildCandidate
import ru.colabike.core.model.ComponentDictionary
import ru.colabike.core.model.ComponentProblem
import ru.colabike.core.model.ResolveRequest
import ru.colabike.core.model.SiteCatalog
import ru.colabike.core.model.SourceKind

/** What the wizard can ask for; the route wires each to the ViewModel. */
data class BikeWizardActions(
    val onBack: () -> Unit = {},
    val onSearchText: (String) -> Unit = {},
    val onGarageName: (String) -> Unit = {},
    val onPageUrl: (String) -> Unit = {},
    val onTogglePage: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onSearchPage: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onChoose: (BuildCandidate) -> Unit = {},
    val onStop: () -> Unit = {},
    val onManual: () -> Unit = {},
    val onOpenSource: (String) -> Unit = {},
    val onAddPart: () -> Unit = {},
    val onRemovePart: (Long) -> Unit = {},
    val onPartCategory: (Long, String) -> Unit = { _, _ -> },
    val onPartName: (Long, String) -> Unit = { _, _ -> },
    val onPartPrice: (Long, String) -> Unit = { _, _ -> },
    val onNext: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onSaveWithoutSource: () -> Unit = {},
    val onSearchAgain: () -> Unit = {},
    val onUseWeight: () -> Unit = {},
    val onUseColor: () -> Unit = {},
    val onUseLink: () -> Unit = {},
    val onRetryCatalog: () -> Unit = {},
    /** The fields of the last step are the form of a bike's own: the same actions. */
    val form: BikeEditorActions = BikeEditorActions(),
)

private val ContentWidth = 600.dp

/**
 * The wizard of a new bike: a search line, the build that was found (or none) to check part by
 * part, and the details of the bike. The first step has what a person who knows the model needs
 * (one line, one button); everything else is one tap away.
 */
@Composable
fun BikeWizardScreen(
    state: WizardUiState,
    actions: BikeWizardActions,
    catalog: SiteCatalog = BuiltinCatalog.value,
    /** The site's dictionaries could not be read: the lists are short, typing still works. */
    catalogFailed: Boolean = false,
) {
    val step = state.step.ordinal + 1
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                ColaTopBar(
                    title = stringResource(R.string.wizard_title),
                    subtitle =
                        stringResource(
                            R.string.wizard_step,
                            step,
                            stringResource(state.step.label()),
                        ),
                    onBack = actions.onBack,
                )
                // The step is said in words above; the bar only shows how far it is.
                LinearProgressIndicator(
                    progress = { step / WizardStep.entries.size.toFloat() },
                    drawStopIndicator = {},
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = Spacing.screen)
                            .clearAndSetSemantics {}
                            .testTag("wizard:progress"),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().imePadding().testTag("wizard"),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        if (catalogFailed) CatalogNotice(actions.onRetryCatalog)
                        when (state.step) {
                            WizardStep.Search -> SearchStep(state, actions, catalog)
                            WizardStep.Build -> BuildStep(state, actions, catalog)
                            WizardStep.Details -> DetailsStep(state, actions, catalog)
                        }
                    }
                }
            }
        }
    }
    Question(state, actions)
}

private fun WizardStep.label(): Int =
    when (this) {
        WizardStep.Search -> R.string.wizard_step_search
        WizardStep.Build -> R.string.wizard_step_build
        WizardStep.Details -> R.string.wizard_step_details
    }

@Composable
private fun CatalogNotice(onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("wizard:catalog-failed"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            stringResource(R.string.wizard_catalog_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry, modifier = Modifier.testTag("wizard:catalog-retry")) {
            Text(stringResource(R.string.wizard_catalog_retry))
        }
    }
}

// --- step 1: search
// -------------------------------------------------------------------------------

@Composable
private fun SearchStep(state: WizardUiState, actions: BikeWizardActions, catalog: SiteCatalog) {
    val resolving = state.phase as? SearchPhase.Resolving
    val idle = resolving == null
    Section(R.string.wizard_step_search) {
        ColaComboField(
            label = stringResource(R.string.wizard_line),
            value = state.searchText,
            onValueChange = actions.onSearchText,
            suggestions = catalog.searchSuggestions(state.searchText),
            enabled = idle,
            supportingText = stringResource(R.string.wizard_line_hint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
            modifier = Modifier.testTag("wizard:line"),
        )
        OutlinedTextField(
            value = state.garageName,
            onValueChange = actions.onGarageName,
            label = { Text(stringResource(R.string.wizard_garage_name)) },
            supportingText = { Text(stringResource(R.string.wizard_garage_name_hint)) },
            singleLine = true,
            enabled = idle,
            isError = state.garageName.length > BikeRules.MAX_NAME,
            colors = colaTextFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("wizard:garage-name"),
        )
        if (resolving != null) {
            Resolving(resolving.request, actions.onStop)
        } else {
            Button(
                onClick = actions.onSearch,
                modifier = Modifier.fillMaxWidth().testTag("wizard:search"),
            ) {
                Text(stringResource(R.string.wizard_search))
            }
            TextButton(
                onClick = actions.onManual,
                modifier = Modifier.fillMaxWidth().testTag("wizard:manual"),
            ) {
                Text(stringResource(R.string.wizard_manual))
            }
        }
        Disclosure(
            title = stringResource(R.string.wizard_page_open),
            summary = null,
            expanded = state.pageOpen,
            onToggle = actions.onTogglePage,
            modifier = Modifier.testTag("wizard:page-toggle"),
        )
        if (state.pageOpen) {
            OutlinedTextField(
                value = state.pageUrl,
                onValueChange = actions.onPageUrl,
                label = { Text(stringResource(R.string.wizard_page_url)) },
                singleLine = true,
                enabled = idle,
                colors = colaTextFieldColors(),
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { actions.onSearchPage() }),
                modifier = Modifier.fillMaxWidth().testTag("wizard:page-url"),
            )
            Button(
                onClick = actions.onSearchPage,
                enabled = idle && state.pageUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("wizard:page-read"),
            ) {
                Text(stringResource(R.string.wizard_page_read))
            }
        }
        (state.phase as? SearchPhase.Failed)?.let { Failure(it.failure, actions.onRetry) }
    }
    state.offer?.let { Offer(it, idle, actions.onChoose) }
}

/** A search on its way, with the way to stop it: it can take over a minute. */
@Composable
private fun Resolving(request: ResolveRequest, onStop: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("wizard:resolving"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(
                stringResource(
                    when (request) {
                        is ResolveRequest.Search -> R.string.wizard_resolving
                        is ResolveRequest.Page -> R.string.wizard_resolving_page
                        is ResolveRequest.Variant -> R.string.wizard_resolving_variant
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth().testTag("wizard:stop")) {
            Text(stringResource(R.string.wizard_stop))
        }
    }
}

@Composable
private fun Failure(failure: SearchFailure, onRetry: () -> Unit) {
    val retry =
        when (failure) {
            is SearchFailure.Unavailable -> failure.retryable
            is SearchFailure.Failed -> true
            else -> false
        }
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            failure.text(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("wizard:failure"),
        )
        if (retry) {
            TextButton(onClick = onRetry, modifier = Modifier.testTag("wizard:retry")) {
                Text(stringResource(R.string.wizard_retry))
            }
        }
    }
}

@Composable
private fun SearchFailure.text(): String =
    when (this) {
        SearchFailure.NoQuery -> stringResource(R.string.wizard_fail_no_query)
        SearchFailure.NotFound -> stringResource(R.string.wizard_fail_not_found)
        SearchFailure.UnsupportedBrand -> stringResource(R.string.wizard_fail_unsupported)
        is SearchFailure.Unavailable ->
            stringResource(
                when (reason) {
                    "timeout" -> R.string.wizard_fail_timeout
                    "http_403" -> R.string.wizard_fail_http_403
                    "access_challenge" -> R.string.wizard_fail_challenge
                    "dns_failed" -> R.string.wizard_fail_dns
                    else -> R.string.wizard_fail_unavailable
                }
            )
        is SearchFailure.Unreadable ->
            stringResource(
                if (reason == "not_complete_bike") R.string.wizard_fail_not_complete
                else R.string.wizard_fail_unreadable
            )
        SearchFailure.Expired -> stringResource(R.string.wizard_fail_expired)
        SearchFailure.Declined -> stringResource(R.string.wizard_fail_declined)
        is SearchFailure.Failed -> message.resolve()
    }

/** The variants a search offered: the person chooses one, and the choice is of that one page. */
@Composable
private fun Offer(offer: WizardOffer, enabled: Boolean, onChoose: (BuildCandidate) -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("wizard:offer"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            stringResource(R.string.wizard_offer_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.wizard_offer_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        offer.sources?.let { sources ->
            Text(
                stringResource(R.string.wizard_offer_sources, sources.answered, sources.asked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A limited search is not called complete.
            if (!sources.complete) {
                Text(
                    stringResource(R.string.wizard_offer_limited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("wizard:offer-limited"),
                )
            }
        }
        offer.candidates.forEachIndexed { index, candidate ->
            CandidateCard(candidate, index, enabled, onChoose)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(
    candidate: BuildCandidate,
    index: Int,
    enabled: Boolean,
    onChoose: (BuildCandidate) -> Unit,
) {
    val year =
        candidate.year?.let { stringResource(R.string.wizard_candidate_year, it) }
            ?: stringResource(R.string.wizard_candidate_no_year)
    val source = sourceLabel(candidate.sourceKind, candidate.sourceHost)
    val warnings = candidate.warnings.mapNotNull { warningText(it) }
    ColaCard(Modifier.fillMaxWidth().testTag("wizard:candidate:$index")) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(
                Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(year, source).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                candidate.drivetrain?.let {
                    Text(
                        stringResource(R.string.wizard_candidate_drivetrain, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                candidate.quality?.let {
                    Text(
                        stringResource(
                            if (it.complete) R.string.wizard_candidate_complete
                            else R.string.wizard_candidate_partial,
                            it.recognizedComponents,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (candidate.otherHosts.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.wizard_candidate_other_hosts,
                            candidate.otherHosts.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (warnings.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    warnings.forEach { PillBadge(it, icon = ColaIcons.Info) }
                }
            }
            Button(
                onClick = { onChoose(candidate) },
                enabled = enabled && candidate.selectable,
                modifier = Modifier.fillMaxWidth().testTag("wizard:choose:$index"),
            ) {
                Text(stringResource(R.string.wizard_candidate_choose))
            }
        }
    }
}

/** The words for a warning the server names; one this app does not know is not shown raw. */
@Composable
private fun warningText(code: String): String? =
    when (code) {
        "identity_mismatch" -> stringResource(R.string.wizard_warning_identity)
        "conflicting_sources" -> stringResource(R.string.wizard_warning_conflicting)
        "multiple_builds" -> stringResource(R.string.wizard_warning_multiple)
        else -> null
    }

@Composable
private fun sourceLabel(kind: SourceKind?, host: String?): String? =
    when (kind) {
        SourceKind.Manufacturer -> stringResource(R.string.wizard_source_manufacturer)
        SourceKind.Distributor -> stringResource(R.string.wizard_source_distributor)
        SourceKind.Archive -> stringResource(R.string.wizard_source_archive)
        SourceKind.Store ->
            host?.let { stringResource(R.string.wizard_source_store, it) }
                ?: stringResource(R.string.wizard_source_store_plain)
        SourceKind.Web -> stringResource(R.string.wizard_source_web)
        SourceKind.Manual -> stringResource(R.string.wizard_source_manual)
        null -> host
    }

// --- step 2: the parts
// ----------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildStep(state: WizardUiState, actions: BikeWizardActions, catalog: SiteCatalog) {
    val found = state.found
    if (found != null) {
        val build = found.build
        Section(R.string.wizard_build_found) {
            Text(build.name, style = MaterialTheme.typography.titleMedium)
            sourceLabel(build.sourceKind, build.sourceHost)?.let {
                Text(
                    stringResource(R.string.wizard_build_source, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(
                    if (build.quality?.complete == true) R.string.wizard_build_complete
                    else R.string.wizard_build_partial
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            val notes = buildList {
                if (build.manualSelection)
                    add(stringResource(R.string.wizard_build_manual_selection))
                if (found.confirmed) add(stringResource(R.string.wizard_build_confirmed))
                if (build.sourceKind == SourceKind.Store)
                    add(stringResource(R.string.wizard_warning_store))
                val asked = state.query?.year
                val named = build.sourceYear
                if (build.yearMismatch && asked != null && named != null)
                    add(stringResource(R.string.wizard_warning_year, named, asked))
                build.warnings.mapNotNullTo(this) { warningText(it) }
            }
            if (notes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    notes.distinct().forEach { PillBadge(it, icon = ColaIcons.Info) }
                }
            }
            if (build.sourceUrl.isNotBlank()) {
                TextButton(
                    onClick = { actions.onOpenSource(build.sourceUrl) },
                    modifier = Modifier.testTag("wizard:source-open"),
                ) {
                    Icon(painterResource(ColaIcons.OpenInNew), contentDescription = null)
                    Text(
                        stringResource(R.string.wizard_build_open_source),
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
            if (build.unrecognized.isNotEmpty()) {
                var open by rememberSaveable { mutableStateOf(false) }
                Disclosure(
                    title =
                        stringResource(R.string.wizard_build_unrecognized, build.unrecognized.size),
                    summary = null,
                    expanded = open,
                    onToggle = { open = !open },
                    modifier = Modifier.testTag("wizard:unrecognized"),
                )
                if (open) {
                    build.unrecognized.forEach {
                        Text(
                            "${it.label}: ${it.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    } else {
        Text(
            stringResource(R.string.wizard_build_by_hand),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("wizard:by-hand"),
        )
    }
    if (state.parts.isNotEmpty()) {
        Text(
            stringResource(R.string.wizard_build_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val dictionary = catalog.components
    partGroups(state.parts, dictionary).forEach { group ->
        WizardGroupCard(group, state, dictionary, actions)
    }
    TextButton(
        onClick = actions.onAddPart,
        modifier = Modifier.fillMaxWidth().testTag("wizard:add-part"),
    ) {
        Icon(painterResource(ColaIcons.Add), contentDescription = null)
        Text(
            stringResource(R.string.wizard_part_add),
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
    Button(
        onClick = actions.onNext,
        modifier = Modifier.fillMaxWidth().testTag("wizard:next"),
    ) {
        Text(stringResource(R.string.wizard_next))
    }
}

/**
 * The parts of one group of the site's list, or the ones that belong to none, or are the person's.
 */
private class WizardGroup(val id: String, val parts: List<WizardPart>, val added: Boolean)

/**
 * The parts in the site's groups, in the site's order; those of no known group together as "other",
 * and the ones the person added last, in the order added: a row never moves while it is typed in.
 */
private fun partGroups(
    parts: List<WizardPart>,
    dictionary: ComponentDictionary,
): List<WizardGroup> {
    val drafted =
        parts
            .filterNot { it.added }
            .groupBy { part ->
                if (dictionary.groupName(part.groupId) != null) part.groupId else ""
            }
    val known = dictionary.groups.map { it.id }.filter { it in drafted }
    val other = if ("" in drafted) listOf("") else emptyList()
    val groups = (known + other).map { WizardGroup(it, drafted.getValue(it), added = false) }
    val own = parts.filter { it.added }
    return if (own.isEmpty()) groups else groups + WizardGroup("", own, added = true)
}

@Composable
private fun WizardGroupCard(
    group: WizardGroup,
    state: WizardUiState,
    dictionary: ComponentDictionary,
    actions: BikeWizardActions,
) {
    val name =
        when {
            group.added -> stringResource(R.string.wizard_group_added)
            else -> dictionary.groupName(group.id) ?: stringResource(R.string.wizard_group_other)
        }
    val tag = if (group.added) "added" else group.id.ifEmpty { "other" }
    var open by rememberSaveable(tag) { mutableStateOf(true) }
    ColaCard(Modifier.fillMaxWidth().testTag("wizard:group:$tag")) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            Disclosure(
                title = "$name · ${group.parts.size}",
                summary = null,
                expanded = open,
                onToggle = { open = !open },
            )
            if (open) {
                group.parts.forEachIndexed { index, part ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.s))
                    PartRow(
                        part,
                        state.partProblems[part.key].orEmpty(),
                        dictionary,
                        actions,
                        !state.saving,
                    )
                }
            }
        }
    }
}

@Composable
private fun PartRow(
    part: WizardPart,
    problems: List<ComponentProblem>,
    dictionary: ComponentDictionary,
    actions: BikeWizardActions,
    enabled: Boolean,
) {
    val key = part.key
    val large = LocalDensity.current.fontScale >= LargeFont
    val price: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = part.price,
            onValueChange = { actions.onPartPrice(key, it) },
            label = { Text(stringResource(R.string.wizard_part_price)) },
            singleLine = true,
            enabled = enabled,
            isError = ComponentProblem.PriceInvalid in problems,
            colors = colaTextFieldColors(),
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = modifier.testTag("wizard:part:$key:price"),
        )
    }
    val remove: @Composable () -> Unit = {
        val label = part.name.ifBlank { part.category }
        IconButton(
            onClick = { actions.onRemovePart(key) },
            enabled = enabled,
            modifier = Modifier.testTag("wizard:part:$key:remove"),
        ) {
            Icon(
                painterResource(ColaIcons.Delete),
                contentDescription =
                    if (label.isBlank()) stringResource(R.string.wizard_part_remove_empty)
                    else stringResource(R.string.wizard_part_remove, label),
            )
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (part.added) {
            // One the person adds names its own category, from the site's list or not.
            ColaComboField(
                label = stringResource(R.string.wizard_part_category),
                value = part.category,
                onValueChange = { actions.onPartCategory(key, it) },
                suggestions =
                    if (part.category.isBlank())
                        dictionary.buildCategories.take(ComponentDictionary.MAX_SUGGESTIONS)
                    else dictionary.suggestions(part.category),
                enabled = enabled,
                isError =
                    problems.any {
                        it == ComponentProblem.NoCategory || it == ComponentProblem.CategoryTooLong
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.testTag("wizard:part:$key:category"),
            )
        } else {
            // A part the specification drafted has its category from the site: it is said, not
            // asked, and its price and its removal stand on the same line.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    part.category,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).testTag("wizard:part:$key:category"),
                )
                price(Modifier.width(if (large) PriceWidthLarge else PriceWidth))
                remove()
            }
        }
        ColaComboField(
            label = stringResource(R.string.wizard_part_name),
            value = part.name,
            onValueChange = { actions.onPartName(key, it) },
            suggestions =
                if (part.name.isBlank()) dictionary.namesOf(part.category).take(MAX_NAMES)
                else dictionary.nameSuggestions(part.category, part.name),
            enabled = enabled,
            isError =
                problems.any {
                    it == ComponentProblem.NoName || it == ComponentProblem.NameTooLong
                },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.testTag("wizard:part:$key:name"),
        )
        if (part.added) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                price(Modifier.weight(1f))
                remove()
            }
        }
        if (problems.isNotEmpty()) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                problems.forEach {
                    Text(
                        stringResource(it.message()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("wizard:part:$key:problem"),
                    )
                }
            }
        }
    }
}

private val PriceWidth = 112.dp
private val PriceWidthLarge = 150.dp

/** From this font scale on the fields have more room (the pairs of the form stand apart). */
private const val LargeFont = 1.3f

private const val MAX_NAMES = 8

// --- step 3: the details
// --------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(state: WizardUiState, actions: BikeWizardActions, catalog: SiteCatalog) {
    // The cards of the bike's form are the ones of the editor: one set of fields, one set of rules.
    val editing =
        BikeEditorUiState.Editing(
            form = state.form,
            saving = state.saving,
            problems = state.problems,
            problem = state.problem,
        )
    MainCard(editing, actions.form, catalog)
    TypeCard(editing, actions.form, catalog)
    val suggested = state.found?.build?.suggested
    val weight = suggested?.weightKg?.takeIf { state.form.weight.isBlank() }?.let { plain(it) }
    val color = suggested?.color?.takeIf { it.isNotBlank() && state.form.color.isBlank() }
    val link =
        suggested?.manufacturerUrl?.takeIf {
            it.isNotBlank() && state.form.manufacturerUrl.isBlank() && BikeRules.isLink(it)
        }
    if (weight != null || color != null || link != null) {
        Section(R.string.wizard_suggest_title) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (weight != null)
                    ColaFilterChip(
                        selected = false,
                        onClick = actions.onUseWeight,
                        label = stringResource(R.string.wizard_suggest_weight, weight),
                        modifier = Modifier.testTag("wizard:suggest-weight"),
                    )
                if (color != null)
                    ColaFilterChip(
                        selected = false,
                        onClick = actions.onUseColor,
                        label = stringResource(R.string.wizard_suggest_color, color),
                        modifier = Modifier.testTag("wizard:suggest-color"),
                    )
                if (link != null)
                    ColaFilterChip(
                        selected = false,
                        onClick = actions.onUseLink,
                        label = stringResource(R.string.wizard_suggest_link),
                        modifier = Modifier.testTag("wizard:suggest-link"),
                    )
            }
        }
    }
    DetailsCard(editing, actions.form, catalog)
    PriceCard(editing, actions.form)
    AudienceCard(editing, actions.form)
    Outcome(state, actions)
    Button(
        onClick = actions.onNext,
        enabled = !state.saving,
        modifier = Modifier.fillMaxWidth().testTag("wizard:save"),
    ) {
        Text(stringResource(if (state.saving) R.string.wizard_saving else R.string.wizard_save))
    }
}

private fun plain(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/**
 * The findings, the server's refusal and a lost preview, announced where the button was pressed.
 */
@Composable
private fun Outcome(state: WizardUiState, actions: BikeWizardActions) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        state.problems.forEach { problem ->
            Text(
                stringResource(problem.message()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("wizard:problem"),
            )
        }
        state.problem?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("wizard:refused"),
            )
        }
        if (state.previewGone) {
            Column(
                Modifier.fillMaxWidth().testTag("wizard:preview-gone"),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    stringResource(R.string.wizard_preview_gone_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.wizard_preview_gone),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = actions.onSaveWithoutSource,
                    modifier = Modifier.fillMaxWidth().testTag("wizard:save-without-source"),
                ) {
                    Text(stringResource(R.string.wizard_preview_save_without))
                }
                TextButton(
                    onClick = actions.onSearchAgain,
                    modifier = Modifier.fillMaxWidth().testTag("wizard:search-again"),
                ) {
                    Text(stringResource(R.string.wizard_preview_search_again))
                }
            }
        }
    }
}

// --- questions
// ------------------------------------------------------------------------------------

/** The question the wizard is asking, if any; "yes" and "no" are the ViewModel's. */
@Composable
private fun Question(state: WizardUiState, actions: BikeWizardActions) {
    val question = state.question ?: return
    when (question) {
        is WizardQuestion.Identity -> {
            val build = question.build
            val named = build.sourceYear
            val text =
                when {
                    question.onSave ->
                        stringResource(
                            R.string.wizard_q_identity_save,
                            build.name,
                            listOf(
                                    state.form.brand,
                                    state.form.model,
                                    state.form.trim,
                                    state.form.year,
                                )
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString(" "),
                        )
                    named != null ->
                        stringResource(
                            R.string.wizard_q_identity_found_year,
                            build.name,
                            named,
                            state.searchText.trim(),
                        )
                    else ->
                        stringResource(
                            R.string.wizard_q_identity_found,
                            build.name,
                            state.searchText.trim(),
                        )
                }
            Ask(
                title = R.string.wizard_q_identity_title,
                text = text,
                yes = if (question.onSave) R.string.wizard_q_save else R.string.wizard_q_use,
                no = R.string.wizard_q_dont,
                onYes = actions.onConfirm,
                onNo = actions.onDecline,
            )
        }
        is WizardQuestion.ReplaceParts ->
            Ask(
                title = R.string.wizard_q_replace_title,
                text = stringResource(R.string.wizard_q_replace),
                yes = R.string.wizard_q_replace_yes,
                no = R.string.wizard_q_replace_no,
                onYes = actions.onConfirm,
                onNo = actions.onDecline,
            )
        WizardQuestion.Discard ->
            Ask(
                title = R.string.wizard_q_leave_title,
                text = stringResource(R.string.wizard_q_leave),
                yes = R.string.wizard_q_leave_yes,
                no = R.string.wizard_q_leave_no,
                onYes = actions.onLeave,
                onNo = actions.onDecline,
            )
    }
}

@Composable
private fun Ask(
    title: Int,
    text: String,
    yes: Int,
    no: Int,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(stringResource(title)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onYes, modifier = Modifier.testTag("wizard:question:yes")) {
                Text(stringResource(yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onNo, modifier = Modifier.testTag("wizard:question:no")) {
                Text(stringResource(no))
            }
        },
    )
}
