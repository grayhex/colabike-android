package ru.colabike.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
    val scheme = MaterialTheme.colorScheme
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
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = scheme.surfaceContainer,
                labelColor = scheme.onSurfaceVariant,
                selectedContainerColor = scheme.primary,
                selectedLabelColor = scheme.onPrimary,
                iconColor = scheme.onSurfaceVariant,
                selectedLeadingIconColor = scheme.onPrimary,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = scheme.outlineVariant,
                selectedBorderColor = Color.Transparent,
            ),
    )
}
