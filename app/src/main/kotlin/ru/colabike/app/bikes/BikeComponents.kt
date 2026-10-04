package ru.colabike.app.bikes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeComponent

/** One section of the build ("Комплектация", "Аксессуары"), its components in the owner's order. */
data class ComponentSection(val section: String, val components: List<BikeComponent>)

/**
 * Orders the components the way the owner arranged them. Sections come in a fixed order (build,
 * accessories, anything newer last). Within a section the components of one group (`groupId`, or
 * the category when the owner gave none) stay together; groups named in [groupOrder] come first in
 * that order, the others after them by their first component's `sortOrder`.
 */
fun orderComponents(
    components: List<BikeComponent>,
    groupOrder: List<String>,
): List<ComponentSection> {
    val sections = listOf("build", "accessories")
    return components
        .groupBy { it.section.takeIf { s -> s in sections } ?: "other" }
        .entries
        .sortedBy { (section, _) -> sections.indexOf(section).takeIf { it >= 0 } ?: sections.size }
        .map { (section, inSection) ->
            val groups = inSection.groupBy { it.groupId.ifBlank { it.category } }
            val ordered =
                groups.entries.sortedWith(
                    compareBy(
                        { (key, _) ->
                            groupOrder.indexOf(key).takeIf { it >= 0 } ?: groupOrder.size
                        },
                        { (_, members) -> members.minOf { it.sortOrder } },
                    )
                )
            ComponentSection(
                section,
                ordered.flatMap { (_, members) -> members.sortedBy { it.sortOrder } },
            )
        }
}

/** The price as people write it: "12 500 ₽", kopecks only when there are some. */
@Composable
fun priceText(rub: Double, locale: Locale): String {
    val format =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = if (rub % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = maximumFractionDigits
        }
    return stringResource(R.string.bike_price_value, format.format(rub))
}

/**
 * A hairline card of a section's components. A group is named once, by a caption over its rows, so
 * the words get the whole width instead of a narrow column beside them.
 */
@Composable
fun ComponentsCard(
    components: List<BikeComponent>,
    locale: Locale,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenModel: (modelId: String) -> Unit = {},
) {
    ColaCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.s)) {
            components.forEachIndexed { index, component ->
                val startsGroup =
                    index == 0 || components[index - 1].groupKey() != component.groupKey()
                if (index > 0) {
                    HorizontalDivider(
                        color =
                            MaterialTheme.colorScheme.outlineVariant.let {
                                if (startsGroup) it else it.copy(alpha = SOFT_DIVIDER)
                            }
                    )
                }
                if (startsGroup) {
                    Eyebrow(
                        component.category,
                        maxLines = 2,
                        modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
                    )
                }
                ComponentRow(component, locale, onOpenLink, onOpenModel)
            }
        }
    }
}

private fun BikeComponent.groupKey() = groupId.ifBlank { category }

/** Within a group the dividers are fainter than between groups. */
private const val SOFT_DIVIDER = 0.5f

@Composable
private fun ComponentRow(
    component: BikeComponent,
    locale: Locale,
    onOpenLink: (String) -> Unit,
    onOpenModel: (modelId: String) -> Unit,
) {
    val modelId = component.modelId
    val openModel = stringResource(R.string.component_open_named, component.name)
    Row(
        Modifier.fillMaxWidth()
            .then(
                // A component chosen from the catalog has a page of its own: the whole row opens
                // it.
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
            Text(component.name, style = MaterialTheme.typography.bodyMedium)
            if (component.notes.isNotBlank()) {
                Text(
                    component.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
