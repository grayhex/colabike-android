package ru.colabike.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.theme.ColaMotion
import ru.colabike.core.designsystem.theme.LocalReducedMotion
import ru.colabike.core.designsystem.theme.PillShape
import ru.colabike.core.designsystem.theme.Spacing

/** A top-level section: its label and the outline / filled glyph pair. */
@Immutable
data class ColaNavItem(
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val compactLabel: String = label,
)

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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelSmall
            val needed =
                items.maxOfOrNull {
                    with(density) { measurer.measure(it.compactLabel, style).size.width.toDp() } +
                        Spacing.s
                } ?: Spacing.touch
            val columns =
                if (needed * items.size <= maxWidth) items.size.coerceAtLeast(1)
                else ((items.size + 1) / 2).coerceAtLeast(1)
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                items.chunked(columns).forEachIndexed { row, group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        group.forEachIndexed { column, item ->
                            val index = row * columns + column
                            BarItem(
                                item,
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarItem(item: ColaNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val label by
        animateColorAsState(
            if (selected) scheme.primary else scheme.onSurfaceVariant,
            if (reduced) snap() else ColaMotion.effects(),
            label = "navigation label",
        )
    val icon by
        animateColorAsState(
            if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            if (reduced) snap() else ColaMotion.effects(),
            label = "navigation icon",
        )
    val plate by
        animateColorAsState(
            if (selected) scheme.primaryContainer else Color.Transparent,
            if (reduced) snap() else ColaMotion.effects(),
            label = "navigation selection",
        )
    Column(
        modifier =
            modifier
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = item.label }
                .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 32.dp)
                .background(
                    plate,
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
            item.compactLabel,
            style = MaterialTheme.typography.labelSmall,
            color = label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
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
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val railWidth =
        (items.maxOfOrNull {
                with(density) { measurer.measure(it.label, style).size.width.toDp() } + Spacing.xl
            } ?: 80.dp)
            .coerceAtLeast(80.dp)
    Column(
        modifier
            .width(railWidth)
            .windowInsetsPadding(NavigationRailDefaults.windowInsets)
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        header?.invoke(this)
        items.forEachIndexed { index, item ->
            BarItem(
                item.copy(compactLabel = item.label),
                index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
