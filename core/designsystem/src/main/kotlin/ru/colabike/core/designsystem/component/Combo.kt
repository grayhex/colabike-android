package ru.colabike.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** One value of a [ColaDropdownField]: the key an API takes, and the words for it. */
@Immutable data class ColaChoice(val key: String, val label: String)

/**
 * A field that is filled in by choosing from a short list (a kind of bike, its subtype): the label
 * and the value chosen, a chevron, and the list under it. [clearLabel], when given, is the first
 * line of the list and takes the value back ("not chosen"): without it a value, once chosen, can
 * only be changed. The list is a menu, so it is read out as one and its lines are 48 dp tall.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColaDropdownField(
    label: String,
    value: String?,
    choices: List<ColaChoice>,
    onChoose: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    clearLabel: String? = null,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = choices.firstOrNull { it.key == value }?.label ?: value.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it && enabled },
    ) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            isError = isError,
            singleLine = true,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            colors = colaTextFieldColors(),
            // The caller's modifier (its test tag, its width) belongs to the field itself.
            modifier =
                modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            if (clearLabel != null) {
                DropdownMenuItem(
                    text = {
                        Text(clearLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = {
                        onChoose(null)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            choice.label,
                            color =
                                if (choice.key == value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onChoose(choice.key)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * A text field that offers values of a dictionary as they are typed: a brand, a model, a size, the
 * name of a part. The person is never held to the list: what is typed is the value, and a line of
 * the list only types it for them. [suggestions] are chosen by the caller (it knows the
 * dictionary); with none, the field is a plain text field and no list opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColaComboField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var expanded by remember { mutableStateOf(false) }
    val open = expanded && enabled && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it && enabled }) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            enabled = enabled,
            isError = isError,
            singleLine = singleLine,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon =
                if (suggestions.isEmpty()) null
                else {
                    { ExposedDropdownMenuDefaults.TrailingIcon(open) }
                },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = colaTextFieldColors(),
            modifier =
                modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
