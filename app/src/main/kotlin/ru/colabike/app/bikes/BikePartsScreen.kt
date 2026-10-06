package ru.colabike.app.bikes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeComponent

/** What the build screen can ask for. */
data class BikePartsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpenPart: (String) -> Unit = {},
    val onMove: (key: String, up: Boolean) -> Unit = { _, _ -> },
    val onOpenGarage: () -> Unit = {},
)

private val ContentWidth = 600.dp

/**
 * The build of one's own bike. A tap on a part changes it; "Add" makes a new one; the groups that
 * have a key of their own can be put in the order the page shows them, with buttons rather than a
 * drag, so that it works with TalkBack and a keyboard as it is.
 */
@Composable
fun BikePartsScreen(state: BikePartsUiState, actions: BikePartsActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.parts_title), onBack = actions.onBack)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                BikePartsUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is BikePartsUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetry, Modifier.fillMaxSize())
                BikePartsUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.bike_unavailable_title),
                        message = stringResource(R.string.bike_cannot_edit),
                        icon = ColaIcons.Bike,
                        actionLabel = stringResource(R.string.bike_open_garage),
                        onAction = actions.onOpenGarage,
                        modifier = Modifier.fillMaxSize().testTag("parts:unavailable"),
                    )
                is BikePartsUiState.Ready -> Ready(state, actions)
            }
        }
    }
}

@Composable
private fun Ready(state: BikePartsUiState.Ready, actions: BikePartsActions) {
    val sections = orderComponents(state.bike.components, state.bike.groupOrder)
    LazyColumn(
        Modifier.fillMaxSize().testTag("parts"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Button(
                    onClick = actions.onAdd,
                    modifier = Modifier.fillMaxWidth().testTag("parts:add"),
                ) {
                    Text(stringResource(R.string.parts_add))
                }
                state.problem?.let {
                    Text(
                        it.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("parts:problem"),
                    )
                }
                if (sections.isEmpty()) {
                    Text(
                        stringResource(R.string.parts_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("parts:empty"),
                    )
                }
                sections.forEach { (section, parts) ->
                    SectionCard(section, parts, actions)
                }
                if (state.groups.size > 1) GroupOrderCard(state, actions)
            }
        }
    }
}

@Composable
private fun SectionCard(section: String, parts: List<BikeComponent>, actions: BikePartsActions) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(
                when (section) {
                    "build" -> R.string.bike_build
                    "accessories" -> R.string.bike_accessories
                    else -> R.string.bike_other_components
                }
            ),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        ColaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.s)) {
                parts.forEachIndexed { index, part ->
                    val startsGroup = index == 0 || parts[index - 1].groupKey() != part.groupKey()
                    if (index > 0)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (startsGroup) {
                        Eyebrow(
                            part.category,
                            maxLines = 2,
                            modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
                        )
                    }
                    PartRow(part, locale, onClick = { actions.onOpenPart(part.id) })
                }
            }
        }
    }
}

private fun BikeComponent.groupKey() = groupId.ifBlank { category }

@Composable
private fun PartRow(part: BikeComponent, locale: java.util.Locale, onClick: () -> Unit) {
    val change = stringResource(R.string.parts_change_named, part.name)
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = change, onClick = onClick)
            .padding(vertical = Spacing.s)
            .testTag("parts:part:${part.id}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(part.name, style = MaterialTheme.typography.bodyMedium)
            if (part.notes.isNotBlank()) {
                Text(
                    part.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            part.priceRub?.let {
                Text(
                    priceText(it, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painterResource(ColaIcons.Edit),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GroupOrderCard(state: BikePartsUiState.Ready, actions: BikePartsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(R.string.parts_order),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.parts_order_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.xs)) {
                state.groups.forEachIndexed { index, group ->
                    if (index > 0)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().testTag("parts:group:${group.key}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            group.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(vertical = Spacing.s),
                        )
                        IconButton(
                            onClick = { actions.onMove(group.key, true) },
                            enabled = index > 0 && !state.ordering,
                            modifier = Modifier.testTag("parts:up:${group.key}"),
                        ) {
                            Icon(
                                painterResource(ColaIcons.ArrowUp),
                                contentDescription =
                                    stringResource(R.string.parts_move_up, group.title),
                            )
                        }
                        IconButton(
                            onClick = { actions.onMove(group.key, false) },
                            enabled = index < state.groups.lastIndex && !state.ordering,
                            modifier = Modifier.testTag("parts:down:${group.key}"),
                        ) {
                            Icon(
                                painterResource(ColaIcons.ArrowDown),
                                contentDescription =
                                    stringResource(R.string.parts_move_down, group.title),
                            )
                        }
                    }
                }
            }
        }
    }
}
