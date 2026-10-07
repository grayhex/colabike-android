package ru.colabike.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.theme.Spacing

/**
 * Text fields in the reference's manner: no fill of their own (the canvas shows through, and the
 * label's notch in the border stays clean), rounded corners, the point colour for focus. The border
 * at rest is the `outline` role, not the hairline: a field's edge has to reach 3:1 against the
 * canvas (WCAG 1.4.11), which a decorative hairline does not.
 */
@Composable
fun colaTextFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        errorBorderColor = scheme.error,
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        cursorColor = scheme.primary,
    )
}

/** Rounded, nearly a pill at the usual field height, but with room for the floating label. */
val colaFieldShape: Shape
    @Composable get() = MaterialTheme.shapes.large

/** The height a search line and its buttons keep: the touch target, no less. */
private val SearchHeight = 48.dp

/**
 * The search line of a list: a raised surface under a visible edge, the lens, what is typed, a
 * clear button once there is text and, if the screen has a wider search, a divider and a "filters"
 * button at the end. The keyboard's search key only hides the keyboard: the list already follows
 * the text. Every part keeps its 48 dp touch target.
 */
@Composable
fun ColaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    filtersLabel: String? = null,
    onFilters: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.small
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = SearchHeight)
                    .background(scheme.surfaceContainer, shape)
                    .border(1.dp, if (focused) scheme.primary else scheme.outline, shape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(ColaIcons.Search),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.l, end = Spacing.m).size(24.dp),
                )
                Box(Modifier.weight(1f).padding(vertical = Spacing.m)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(painterResource(ColaIcons.Close), contentDescription = clearLabel)
                    }
                }
                if (onFilters != null && filtersLabel != null) {
                    VerticalDivider(Modifier.height(24.dp), color = scheme.outlineVariant)
                    IconButton(onClick = onFilters) {
                        Icon(painterResource(ColaIcons.Tune), contentDescription = filtersLabel)
                    }
                }
            }
        },
    )
}

/**
 * A field that is filled in by choosing, not typing: a date, a time. A raised surface under a
 * visible edge, a thin icon, the value, and a chevron-down at the end if [dropdown]. The whole row
 * is the button and at least 52 dp tall.
 */
@Composable
fun ColaSelectField(
    text: String,
    @androidx.annotation.DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dropdown: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val content = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.5f)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outline, shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.s),
        )
        if (dropdown) {
            Icon(
                painterResource(ColaIcons.ArrowDown),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
