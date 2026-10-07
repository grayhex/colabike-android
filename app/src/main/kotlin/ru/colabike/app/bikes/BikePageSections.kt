package ru.colabike.app.bikes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.Avatar
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LikeButton
import ru.colabike.core.designsystem.component.journalKindLabel
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.Person

/**
 * The owner's two small actions under the photo: "+ Фото" adds one (the system picker opens at
 * once), "Управлять" opens the photos of the bike (cover, delete, the rest). With no photo there is
 * nothing to manage, and only the first stays.
 */
@Composable
internal fun PhotoActions(
    hasPhotos: Boolean,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addDescription = stringResource(R.string.bike_photo_add_description)
    val manageDescription = stringResource(R.string.bike_photos_manage_description)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onAdd,
            modifier =
                Modifier.heightIn(min = Spacing.touch).testTag("bike:photo-add").semantics {
                    contentDescription = addDescription
                },
        ) {
            Icon(painterResource(ColaIcons.Add), contentDescription = null, Modifier.size(20.dp))
            Text(stringResource(R.string.bike_photo_add), Modifier.padding(start = Spacing.s))
        }
        if (hasPhotos) {
            TextButton(
                onClick = onManage,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                modifier =
                    Modifier.heightIn(min = Spacing.touch).testTag("bike:photos").semantics {
                        contentDescription = manageDescription
                    },
            ) {
                Icon(
                    painterResource(ColaIcons.Image),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.bike_photos_manage),
                    Modifier.padding(start = Spacing.s),
                )
            }
        }
    }
}

/**
 * Who rides the bike, the like and the share on one line; at a large system font the like and the
 * share go under the name, where it has the whole width and is not broken in the middle of a word.
 * The name opens the person; a like is given on the others' public bikes only (the count shows on
 * the rest); the share goes where the bike is public.
 */
@Composable
internal fun AuthorRow(
    author: Person?,
    liked: Boolean,
    likes: Int,
    busy: Boolean,
    onToggleLike: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onOpenAuthor: (ref: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stacked = LocalDensity.current.fontScale >= LargeFont
    val actions: @Composable () -> Unit = {
        LikeButton(liked = liked, count = likes, onToggle = onToggleLike, busy = busy)
        if (onShare != null) {
            IconButton(onClick = onShare) {
                Icon(
                    painterResource(ColaIcons.Share),
                    contentDescription =
                        stringResource(ru.colabike.core.designsystem.R.string.cola_share),
                )
            }
        }
    }
    val person: @Composable (Modifier) -> Unit = { personModifier ->
        if (author != null) {
            val open = stringResource(R.string.bike_open_author, author.displayName)
            Row(
                personModifier
                    .heightIn(min = Spacing.touch)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button, onClickLabel = open) {
                        onOpenAuthor(author.id.value)
                    }
                    .padding(end = Spacing.s)
                    .testTag("bike:author"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Avatar(author.displayName, author.avatarUrl, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        author.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            ru.colabike.core.designsystem.R.string.cola_username,
                            author.username,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Spacer(personModifier)
        }
    }
    if (stacked) {
        Column(modifier.fillMaxWidth()) {
            person(Modifier.fillMaxWidth())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                actions()
            }
        }
    } else {
        Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            person(Modifier.weight(1f))
            actions()
        }
    }
}

/** From this font scale on the author's row gives the like and the share a line of their own. */
private const val LargeFont = 1.3f

/**
 * The line under the name: what kind of bike it is and when it is from. The brand and model come
 * first only when the owner's name for the bike does not already say them, so nothing is said
 * twice.
 */
internal fun bikeInfoLine(bike: BikeDetail): String {
    val summary = bike.summary
    val model = "${summary.brand} ${summary.model} ${bike.trim}".trim().replace(Spaces, " ")
    val brandModel = "${summary.brand} ${summary.model}".trim().replace(Spaces, " ")
    val named = brandModel.isBlank() || summary.name.contains(brandModel, ignoreCase = true)
    return listOfNotNull(
            model.takeIf { !named && it.isNotBlank() },
            *BikeLabels.badges(summary.classification).toTypedArray(),
            summary.year?.toString(),
        )
        .joinToString(" · ")
}

private val Spaces = Regex("\\s+")

/** The facts the owner gave, as label and value; what is empty is not here. */
@Composable
internal fun passport(bike: BikeDetail, locale: Locale): List<Pair<String, String>> =
    listOfNotNull(
        bike.weightKg?.let {
            stringResource(R.string.bike_weight) to
                stringResource(
                    R.string.bike_weight_value,
                    NumberFormat.getNumberInstance(locale).format(it),
                )
        },
        bike.mileageKm
            .takeIf { it > 0 }
            ?.let {
                stringResource(R.string.bike_mileage) to
                    stringResource(R.string.bike_mileage_value, it)
            },
        bike.size.takeIf { it.isNotBlank() }?.let { stringResource(R.string.bike_size) to it },
        bike.color.takeIf { it.isNotBlank() }?.let { stringResource(R.string.bike_color) to it },
        // The price arrives only when the owner shows it (or it is the owner's own).
        bike.priceRub?.let { stringResource(R.string.bike_price) to priceText(it, locale) },
    )

/** The passport in a line that wraps: a quiet label, then the value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PassportLine(facts: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        modifier.fillMaxWidth().testTag("bike:passport"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        facts.forEach { (label, value) ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = quiet)) { append("$label ") }
                    append(value)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { contentDescription = "$label $value" },
            )
        }
    }
}

/**
 * The build, shut at first. The title row opens and closes it in place; the pencil (the owner's) is
 * a button of its own and goes to the editor. Inside, every component stands under its own category
 * in the owner's order; the accessories and the rest come after the build under a small caption.
 */
@Composable
internal fun EquipmentCard(
    sections: List<ComponentSection>,
    locale: Locale,
    onEdit: (() -> Unit)?,
    onOpenLink: (String) -> Unit,
    onOpenModel: (modelId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandLabel = stringResource(R.string.bike_equipment_expand)
    val collapseLabel = stringResource(R.string.bike_equipment_collapse)
    val editDescription = stringResource(R.string.bike_equipment_edit)
    val openState = stringResource(R.string.bike_equipment_expanded)
    val shutState = stringResource(R.string.bike_equipment_collapsed)
    ColaCard(modifier.fillMaxWidth().testTag("bike:equipment")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = HEADER_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f)
                    .heightIn(min = HEADER_HEIGHT)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) collapseLabel else expandLabel,
                    ) {
                        expanded = !expanded
                    }
                    .semantics { stateDescription = if (expanded) openState else shutState }
                    .padding(start = Spacing.card),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(
                    painterResource(ColaIcons.Build),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    stringResource(R.string.bike_build),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (onEdit != null) {
                if (expanded) {
                    TextButton(
                        onClick = onEdit,
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                        modifier =
                            Modifier.heightIn(min = Spacing.touch).testTag("bike:parts").semantics {
                                contentDescription = editDescription
                            },
                    ) {
                        Icon(
                            painterResource(ColaIcons.Edit),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(R.string.bike_equipment_edit_short),
                            Modifier.padding(start = Spacing.s),
                        )
                    }
                } else {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("bike:parts")) {
                        Icon(
                            painterResource(ColaIcons.Edit),
                            contentDescription = stringResource(R.string.bike_equipment_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // The arrow is the same switch as the row; TalkBack has the row.
            Box(
                Modifier.size(Spacing.touch)
                    .testTag("bike:equipment-toggle")
                    .clearAndSetSemantics {}
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (expanded) ColaIcons.ArrowUp else ColaIcons.ArrowDown),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.xs)) {
                if (sections.isEmpty()) {
                    Text(
                        stringResource(R.string.bike_equipment_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.m),
                    )
                }
                sections.forEach { (section, components) ->
                    if (section != "build") {
                        Text(
                            stringResource(
                                if (section == "accessories") R.string.bike_accessories
                                else R.string.bike_other_components
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
                        )
                    }
                    components.forEachIndexed { index, component ->
                        if (index > 0) {
                            val sameGroup = components[index - 1].groupKey() == component.groupKey()
                            HorizontalDivider(
                                color =
                                    MaterialTheme.colorScheme.outlineVariant.let {
                                        if (sameGroup) it.copy(alpha = SOFT_DIVIDER) else it
                                    }
                            )
                        }
                        EquipmentRow(component, locale, onOpenLink, onOpenModel)
                    }
                }
            }
        }
    }
}

/** The title row of the build: a touch target with a little to spare. */
private val HEADER_HEIGHT = 52.dp

private fun BikeComponent.groupKey() = groupId.ifBlank { category }

/** Within a group the dividers are fainter than between groups. */
private const val SOFT_DIVIDER = 0.5f

/** Lines of a component's note shown before "Подробнее". */
private const val NOTE_LINES = 2

/**
 * One component: its own category over its name, the note (only when it says something the name
 * does not, and shut to two lines), the price where it is shown. A component of the catalog opens
 * its page with the whole row; the maker's link is a button of its own.
 */
@Composable
private fun EquipmentRow(
    component: BikeComponent,
    locale: Locale,
    onOpenLink: (String) -> Unit,
    onOpenModel: (modelId: String) -> Unit,
) {
    val modelId = component.modelId
    val openModel = stringResource(R.string.component_open_named, component.name)
    val note =
        component.notes.trim().takeIf { it.isNotBlank() && !it.equals(component.name.trim(), true) }
    Row(
        Modifier.fillMaxWidth()
            .then(
                if (modelId != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = openModel) {
                        onOpenModel(modelId)
                    }
                } else Modifier
            )
            .padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Eyebrow(component.category, maxLines = 2)
            // A long name is read in full: it is what the owner wrote.
            Text(component.name, style = MaterialTheme.typography.bodyLarge)
            if (note != null) ShutNote(note)
            component.priceRub?.let {
                Text(
                    priceText(it, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (modelId != null) {
            Icon(
                painterResource(ColaIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        component.url?.let { url ->
            IconButton(onClick = { onOpenLink(url) }) {
                Icon(
                    painterResource(ColaIcons.OpenInNew),
                    contentDescription =
                        stringResource(R.string.bike_component_link, component.name),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** A note that shows its first two lines and offers the rest, only when there is a rest. */
@Composable
private fun ShutNote(text: String) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (open) Int.MAX_VALUE else NOTE_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!open) overflows = it.hasVisualOverflow },
    )
    if (overflows || open) {
        TextButton(
            onClick = { open = !open },
            modifier = Modifier.heightIn(min = Spacing.touch),
        ) {
            Text(stringResource(if (open) R.string.bike_note_less else R.string.bike_note_more))
        }
    }
}

/**
 * The journal on the bike's page: the heading with the owner's small "+ Запись" (a record of this
 * very bike), the latest entry, "Все записи". An empty journal is one quiet line; a failed one says
 * so with a retry and leaves the rest of the page alone.
 */
@Composable
internal fun JournalSection(
    state: JournalPreviewUiState,
    locale: Locale,
    onNew: (() -> Unit)?,
    onOpenAll: () -> Unit,
    onOpenEntry: (JournalId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("bike:journal")) {
        Row(
            // Without the owner's button the heading is only a heading, and needs no finger's room.
            Modifier.fillMaxWidth().heightIn(min = if (onNew != null) Spacing.touch else 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.journal_section),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onNew != null) {
                val description = stringResource(R.string.journal_add_description)
                TextButton(
                    onClick = onNew,
                    modifier =
                        Modifier.heightIn(min = Spacing.touch)
                            .testTag("bike:journal-new")
                            .semantics {
                                contentDescription = description
                            },
                ) {
                    Icon(
                        painterResource(ColaIcons.Add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(stringResource(R.string.journal_add), Modifier.padding(start = Spacing.s))
                }
            }
        }
        when (state) {
            JournalPreviewUiState.Loading ->
                Box(
                    Modifier.fillMaxWidth()
                        .padding(vertical = Spacing.m)
                        .testTag("bike:journal-loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            is JournalPreviewUiState.Failed ->
                Column(
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.journal_preview_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.message.resolve(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = Spacing.touch),
                    ) {
                        Text(stringResource(ru.colabike.core.designsystem.R.string.cola_retry))
                    }
                }
            is JournalPreviewUiState.Ready -> {
                val latest = state.latest
                if (latest == null) {
                    Text(
                        stringResource(R.string.journal_preview_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                } else {
                    JournalPreviewRow(latest, locale, onClick = { onOpenEntry(latest.id) })
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = Spacing.touch)
                            .clickable(role = Role.Button, onClick = onOpenAll)
                            .padding(horizontal = Spacing.xs)
                            .testTag("bike:journal-all"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.journal_all),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painterResource(ColaIcons.ChevronRight),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val DayMonth = "d MMM"

/** The latest entry as a row: the day, a hairline, the title and the kind, the way in. */
@Composable
private fun JournalPreviewRow(entry: JournalSummary, locale: Locale, onClick: () -> Unit) {
    val day: LocalDate =
        entry.eventDate ?: LocalDate.ofInstant(entry.createdAt, ZoneId.systemDefault())
    val dayText = DateTimeFormatter.ofPattern(DayMonth, locale).format(day).trimEnd('.')
    val kind = journalKindLabel(entry.kind)
    val draft =
        if (entry.status == JournalStatus.Draft) {
            stringResource(ru.colabike.core.designsystem.R.string.cola_journal_draft)
        } else null
    val secondary = listOfNotNull(draft, kind).joinToString(" · ").ifBlank { entry.excerpt.trim() }
    val openLabel = stringResource(ru.colabike.core.designsystem.R.string.cola_open)
    val description =
        stringResource(
            R.string.journal_preview_open,
            listOfNotNull(entry.title, secondary.takeIf { it.isNotBlank() }, "$dayText ${day.year}")
                .joinToString(", "),
        )
    ColaCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.fillMaxWidth().testTag("bike:journal-entry").clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = openLabel) {
                    onClick()
                    true
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    day.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VerticalDivider(
                Modifier.height(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (secondary.isNotBlank()) {
                    Text(
                        secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painterResource(ColaIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A quiet row that goes somewhere: an icon, the words, the way in. */
@Composable
internal fun LinkRow(
    title: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    external: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(
            painterResource(if (external) ColaIcons.OpenInNew else ColaIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
