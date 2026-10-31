package com.github.irdcat.workout.application.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.TextStyles

/** Outlined text fields on the canvas colour, accented when focused. */
@Composable
fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Colors.Canvas,
    unfocusedContainerColor = Colors.Canvas,
    focusedBorderColor = Colors.Primary,
    unfocusedBorderColor = Colors.Border,
    cursorColor = Colors.Primary,
    focusedTextColor = Colors.TextPrimary,
    unfocusedTextColor = Colors.TextPrimary,
    focusedLeadingIconColor = Colors.TextSecondary,
    unfocusedLeadingIconColor = Colors.TextSecondary,
    focusedPlaceholderColor = Colors.TextSecondary,
    unfocusedPlaceholderColor = Colors.TextSecondary,
    disabledContainerColor = Colors.Canvas,
    disabledBorderColor = Colors.Border,
    disabledTextColor = Colors.TextSecondary,
    disabledLeadingIconColor = Colors.TextSecondary,
    disabledPlaceholderColor = Colors.TextSecondary,
)

/**
 * A field and an add button for adding names to a list, offering [suggestions] while typing.
 * Done on the keyboard adds too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWithSuggestionsField(
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    suggestions: List<String>,
    onPickSuggestion: (String) -> Unit,
    placeholder: String,
    /** The add button's content description. */
    addDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    // Shown while the field has focus and there's something to offer; closing it doesn't stop typing.
    var dismissed by remember { mutableStateOf(false) }
    val expanded = enabled && focused && !dismissed && suggestions.isNotEmpty()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { dismissed = !it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    dismissed = false
                    onInputChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                    .onFocusChanged { focused = it.isFocused },
                enabled = enabled,
                placeholder = { Text(placeholder) },
                singleLine = true,
                textStyle = TextStyles.bodyMd.copy(fontSize = 16.sp),
                shape = Shapes.xl,
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { dismissed = true },
                containerColor = Colors.Surface2,
            ) {
                suggestions.forEach { name ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = highlighted(name, input.trim()),
                                style = TextStyles.bodyMd.copy(fontSize = 16.sp),
                                color = Colors.TextPrimary,
                            )
                        },
                        onClick = { onPickSuggestion(name) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        AddButton(onClick = onAdd, description = addDescription, enabled = enabled)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, description: String, enabled: Boolean) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        shape = Shapes.xl,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Colors.Surface2,
            contentColor = Colors.TextPrimary,
            disabledContainerColor = Colors.Surface2,
            disabledContentColor = Colors.TextSecondary,
        ),
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = description)
    }
}

/** [name] with the part matching [query] in bold. */
private fun highlighted(name: String, query: String): AnnotatedString = buildAnnotatedString {
    val start = if (query.isEmpty()) -1 else name.indexOf(query, ignoreCase = true)
    if (start < 0) return@buildAnnotatedString append(name)
    append(name.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name.substring(start, start + query.length)) }
    append(name.substring(start + query.length))
}

/** A name in a list, with a button that takes it out. */
@Composable
fun RemovableChip(name: String, removeDescription: String, onRemove: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(Colors.Surface2, Shapes.pill)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = TextStyles.bodyNoteStrong,
            color = Colors.TextPrimary,
            maxLines = 1,
        )
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Colors.TextSecondary,
                disabledContentColor = Colors.TextSecondary,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = removeDescription,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
