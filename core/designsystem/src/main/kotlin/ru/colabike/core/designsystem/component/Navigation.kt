package ru.colabike.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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

/**
 * The section names are short labels under icons, a fifth of the screen wide each: at the largest
 * system font they would be cut. They stop growing at 1.15 times; TalkBack reads them in full
 * whatever the size.
 */
@Composable
private fun CappedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides
            Density(density.density, density.fontScale.coerceAtMost(NavFontScale)),
        content = content,
    )
}

private const val NavFontScale = 1.15f

/**
 * The bottom bar of a phone: embedded in the bottom edge under a hairline, not floating. Every
 * section shows its label, always. The selected one is lime, with a soft muted plate under its
 * icon. Each item keeps its label for TalkBack (role tab, selected state). The caller reports a tap
 * on the selected item too ([onSelect] gets the same index): that is the reselect.
 */
@Composable
fun ColaNavigationBar(
    items: List<ColaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(scheme.background)
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
    ) {
        HorizontalDivider(color = scheme.outlineVariant)
        CappedFontScale {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.xs).selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                items.forEachIndexed { index, item ->
                    BarItem(item, selected = index == selectedIndex, onClick = { onSelect(index) })
                }
            }
        }
    }
}

@Composable
private fun RowScope.BarItem(item: ColaNavItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = if (selected) scheme.primary else scheme.onSurfaceVariant
    val icon = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Column(
        modifier =
            Modifier.weight(1f)
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 32.dp)
                .background(
                    if (selected) scheme.primaryContainer else Color.Transparent,
                    PillShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(item.icon),
                contentDescription = null,
                tint = icon,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The same sections as a rail for medium and expanded windows, on the bare canvas. Material's rail
 * with the theme's colours: the soft lime plate behind the selected item.
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
    CappedFontScale {
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
                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
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
}
