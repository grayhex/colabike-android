package ru.colabike.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import ru.colabike.core.designsystem.theme.Spacing

/** What a disabled choice looks like: still readable, plainly not available. */
private const val DisabledAlpha = 0.5f

/**
 * A setting that is on or off: a title, one line of explanation and a switch at the end. The whole
 * row is the touch target (at least 48 dp tall) and TalkBack reads title, explanation and state as
 * one phrase with the role "switch". A row with no [onCheckedChange] only shows the state.
 */
@Composable
fun ColaSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val interactive = enabled && onCheckedChange != null
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .then(
                if (onCheckedChange != null)
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                else Modifier
            )
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ChoiceText(
            title,
            supporting,
            dimmed = !(interactive || onCheckedChange == null),
            Modifier.weight(1f),
        )
        // The row handles the touch; the switch only draws the state.
        Switch(checked = checked, onCheckedChange = null, enabled = interactive)
    }
}

/**
 * One of several mutually exclusive choices: a radio button, a title and one line of explanation.
 * The whole row is the touch target and TalkBack reads it as a radio button with its state.
 */
@Composable
fun ColaRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        ChoiceText(title, supporting, dimmed = !enabled, Modifier.weight(1f))
    }
}

/**
 * The words of a choice. A choice that cannot be pressed is dimmed through the colour of its text,
 * not through a transparent layer: a layer is drawn off-screen, and a capture of it can come out
 * empty.
 */
@Composable
private fun ChoiceText(title: String, supporting: String?, dimmed: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (dimmed) DisabledAlpha else 1f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface.copy(alpha = alpha),
        )
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}
