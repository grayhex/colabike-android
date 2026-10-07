package ru.colabike.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.theme.PillShape

/**
 * A filter as the reference draws it: a hairline pill at rest, a solid lime pill with dark text
 * when selected (the one big fill the reference allows besides the main action), with a thin icon
 * in front if [icon] is given. Material gives it the 48 dp touch target and the selected state for
 * TalkBack.
 */
@Composable
fun ColaFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon =
            icon?.let {
                { Icon(painterResource(it), contentDescription = null, Modifier.size(18.dp)) }
            },
        modifier = modifier,
        shape = PillShape,
        colors = chipColors(),
        border = chipBorder(selected),
    )
}

/**
 * A filter that opens a list of values: its label is the value chosen now, a chevron says that it
 * opens. [active] is a value other than the usual one (the pill is lime, as a chosen chip is);
 * [description] is what TalkBack says for the whole thing ("Category: Road"). The values are the
 * [menu]: [ColaDropdownItem]s, and the function it is given closes the list. A long label is cut to
 * one line, never wrapped: a row of these stays one row.
 */
@Composable
fun ColaDropdownChip(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    description: String? = null,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                Icon(
                    painterResource(ColaIcons.ArrowDown),
                    contentDescription = null,
                    Modifier.size(18.dp),
                )
            },
            modifier =
                Modifier.semantics {
                    if (description != null) contentDescription = description
                    role = Role.DropdownList
                },
            shape = PillShape,
            colors = chipColors(),
            border = chipBorder(active),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
    }
}

/**
 * One value of a [ColaDropdownChip]: its words, and a check mark when it is chosen. A list that
 * lets several be chosen leaves itself open on a tap; the caller closes it when it is meant to.
 */
@Composable
fun ColaDropdownItem(label: String, chosen: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                painterResource(ColaIcons.Check),
                contentDescription = null,
                tint = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.semantics { selected = chosen },
    )
}

@Composable
private fun chipColors() =
    MaterialTheme.colorScheme.let { scheme ->
        FilterChipDefaults.filterChipColors(
            containerColor = scheme.surfaceContainer,
            labelColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.primary,
            selectedLabelColor = scheme.onPrimary,
            iconColor = scheme.onSurfaceVariant,
            selectedLeadingIconColor = scheme.onPrimary,
            selectedTrailingIconColor = scheme.onPrimary,
        )
    }

@Composable
private fun chipBorder(selected: Boolean) =
    FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        selectedBorderColor = Color.Transparent,
    )
