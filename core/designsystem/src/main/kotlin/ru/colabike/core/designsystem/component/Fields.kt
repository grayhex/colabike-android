package ru.colabike.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

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
