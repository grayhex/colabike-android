package ru.colabike.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.theme.PillShape
import ru.colabike.core.designsystem.theme.Spacing

/** A top-level section: its label and the outline / filled glyph pair. */
@Immutable
data class ColaNavItem(
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
)

/** Up to this many sections, every label is shown; beyond it only the selected one is. */
private const val MaxLabelledItems = 3

/** The share of the bar the selected item takes, relative to 1 for each of the others. */
private const val SelectedWeight = 2f

/** How far the soft light behind the selected icon reaches, as a share of the icon box. */
private const val GlowReach = 0.8f

/**
 * The reference "tab bar" for a phone: a hairline pill floating above the bottom edge, outline
 * icons at rest, the filled glyph with a soft light behind it when selected. Each item keeps its
 * label for TalkBack (role tab, selected state). With up to [MaxLabelledItems] items every label is
 * shown under its icon; with more, only the selected item shows its label and gets the room for it
 * (a phone cannot fit five Russian labels at a readable size). The caller reports a tap on the
 * selected item too ([onSelect] gets the same index): that is the reselect.
 */
@Composable
fun ColaNavigationBar(
    items: List<ColaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
    ) {
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            // The one element that floats above the page, so the one that may cast a shadow.
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs).selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val labelAll = items.size <= MaxLabelledItems
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    BarItem(
                        item,
                        selected = selected,
                        showLabel = labelAll || selected,
                        weight = if (selected && !labelAll) SelectedWeight else 1f,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BarItem(
    item: ColaNavItem,
    selected: Boolean,
    showLabel: Boolean,
    weight: Float,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (selected) scheme.primary else scheme.onSurfaceVariant
    Column(
        modifier =
            Modifier.weight(weight)
                .heightIn(min = 56.dp)
                .clip(MaterialTheme.shapes.medium)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                // A hidden label is still the item's name for TalkBack.
                .semantics { if (!showLabel) contentDescription = item.label }
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(40.dp).glow(scheme.primary, visible = selected),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (selected) item.selectedIcon else item.icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        if (showLabel) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The reference's one allowed "emission": a soft light behind the active icon, static. */
private fun Modifier.glow(color: Color, visible: Boolean): Modifier =
    if (!visible) this
    else
        drawBehind {
            val radius = size.maxDimension * GlowReach
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)),
                radius = radius,
            )
        }

/**
 * The same sections as a rail for medium and expanded windows, on the bare canvas. Material's rail
 * with the theme's colours: a sand wash behind the selected item.
 */
@Composable
fun ColaNavigationRail(
    items: List<ColaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier,
        containerColor = Color.Transparent,
        header = header,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        painterResource(if (selected) item.selectedIcon else item.icon),
                        contentDescription = null,
                    )
                },
                label = { Text(item.label) },
                alwaysShowLabel = true,
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = scheme.onPrimaryContainer,
                        selectedTextColor = scheme.primary,
                        indicatorColor = scheme.primaryContainer,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
