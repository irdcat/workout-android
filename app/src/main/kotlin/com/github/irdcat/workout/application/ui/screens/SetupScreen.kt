package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

/** Setup backed by [SetupViewModel]; only navigation leaves this composable. */
@Composable
fun SetupRoute(
    onSaved: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.navigation) { event ->
        when (event) {
            SetupNavigation.Saved -> onSaved()
            SetupNavigation.SignedOut -> onSignedOut()
        }
    }
    SetupScreen(
        email = state.email,
        sheetUrl = state.sheetUrl,
        onSheetUrlChange = viewModel::onSheetUrlChange,
        sheetsFound = state.availableSheets?.size,
        sheetsToLoad = state.sheetsToLoad,
        blacklist = state.blacklist,
        onRemoveFromBlacklist = viewModel::onRemoveFromBlacklist,
        blacklistInput = state.blacklistInput,
        onBlacklistInputChange = viewModel::onBlacklistInputChange,
        onAddToBlacklist = viewModel::onAddToBlacklist,
        blacklistSuggestions = state.blacklistSuggestions,
        onPickBlacklistSuggestion = viewModel::onPickBlacklistSuggestion,
        writeResults = state.writeResults,
        onWriteResultsChange = viewModel::onWriteResultsChange,
        onSignOut = viewModel::onSignOut,
        onSave = viewModel::onSave,
        enabled = state.isLoaded,
    )
}

@Composable
fun SetupScreen(
    email: String,
    sheetUrl: String,
    onSheetUrlChange: (String) -> Unit,
    sheetsFound: Int?,
    sheetsToLoad: List<String>,
    blacklist: List<String>,
    onRemoveFromBlacklist: (String) -> Unit,
    blacklistInput: String,
    onBlacklistInputChange: (String) -> Unit,
    onAddToBlacklist: () -> Unit,
    writeResults: Boolean,
    onWriteResultsChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** Sheet names to offer while typing a blacklist entry. */
    blacklistSuggestions: List<String> = emptyList(),
    onPickBlacklistSuggestion: (String) -> Unit = {},
    /** False while the saved values load: everything but signing out is read-only. */
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Colors.Canvas,
    ) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Header()
                AccountCard(email = email, onSignOut = onSignOut)
                SheetLinkSection(
                    sheetUrl = sheetUrl,
                    onSheetUrlChange = onSheetUrlChange,
                    sheetsFound = sheetsFound,
                    sheetsToLoad = sheetsToLoad.size,
                    enabled = enabled,
                )
                BlacklistSection(
                    blacklist = blacklist,
                    onRemove = onRemoveFromBlacklist,
                    input = blacklistInput,
                    onInputChange = onBlacklistInputChange,
                    onAdd = onAddToBlacklist,
                    suggestions = blacklistSuggestions,
                    onPickSuggestion = onPickBlacklistSuggestion,
                    enabled = enabled,
                )
                if (sheetsToLoad.isNotEmpty()) {
                    WillLoadSection(sheets = sheetsToLoad)
                }
                WriteResultsCard(checked = writeResults, onCheckedChange = onWriteResultsChange, enabled = enabled)
            }
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = Shapes.xl,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.Primary,
                    contentColor = Colors.OnPrimary,
                    disabledContainerColor = Colors.Surface2,
                    disabledContentColor = Colors.TextSecondary,
                ),
            ) {
                Text(
                    text = "Save and load workouts",
                    style = KineticType.buttonLabel,
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "ONE-TIME SETUP",
            style = KineticType.labelCaps,
            color = Colors.TextSecondary,
        )
        Text(
            text = "YOUR SPREADSHEET",
            style = KineticType.headlineXl.copy(fontSize = 44.sp, lineHeight = 44.sp),
            color = Colors.TextPrimary,
        )
    }
}

@Composable
private fun AccountCard(email: String, onSignOut: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Colors.Surface2, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = email.firstOrNull()?.uppercase() ?: "",
                    style = KineticType.dataValue,
                    color = Colors.Primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = email,
                    style = KineticType.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                    color = Colors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Signed in with Google",
                    style = KineticType.bodySm,
                    color = Colors.TextSecondary,
                )
            }
            Button(
                onClick = onSignOut,
                modifier = Modifier.height(44.dp),
                shape = Shapes.xl,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.Surface2,
                    contentColor = Colors.TextPrimary,
                ),
            ) {
                Text(
                    text = "Sign out",
                    style = KineticType.bodyNoteStrong,
                )
            }
        }
    }
}

@Composable
private fun SheetLinkSection(
    sheetUrl: String,
    onSheetUrlChange: (String) -> Unit,
    sheetsFound: Int?,
    sheetsToLoad: Int,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Google Sheets link")
        OutlinedTextField(
            value = sheetUrl,
            onValueChange = onSheetUrlChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Link, contentDescription = null)
            },
            singleLine = true,
            textStyle = KineticType.bodyMd.copy(fontSize = 16.sp),
            shape = Shapes.xl,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        if (sheetsFound != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Colors.Primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "$sheetsFound sheets found · $sheetsToLoad will load",
                    style = KineticType.bodyNoteStrong,
                    color = Colors.Primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BlacklistSection(
    blacklist: List<String>,
    onRemove: (String) -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    suggestions: List<String>,
    onPickSuggestion: (String) -> Unit,
    enabled: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    // Shown while the field has focus and there's a sheet to offer; closing it doesn't stop typing.
    var dismissed by remember { mutableStateOf(false) }
    val expanded = enabled && focused && !dismissed && suggestions.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            FieldLabel("Sheet blacklist")
            Text(text = "Optional", style = KineticType.bodySm, color = Colors.TextSecondary)
        }
        Text(
            text = "Sheets with these names are not loaded.",
            style = KineticType.bodyNote,
            color = Colors.TextSecondary,
        )
        if (blacklist.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                blacklist.forEach { name ->
                    BlacklistChip(name = name, onRemove = { onRemove(name) }, enabled = enabled)
                }
            }
        }
        Row(
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
                    placeholder = { Text("Add a sheet name") },
                    singleLine = true,
                    textStyle = KineticType.bodyMd.copy(fontSize = 16.sp),
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
                                    style = KineticType.bodyMd.copy(fontSize = 16.sp),
                                    color = Colors.TextPrimary,
                                )
                            },
                            onClick = { onPickSuggestion(name) },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            FilledIconButton(
                onClick = onAdd,
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
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add to blacklist")
            }
        }
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

@Composable
private fun BlacklistChip(name: String, onRemove: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(Colors.Surface2, Shapes.pill)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = KineticType.bodyNoteStrong,
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
                contentDescription = "Remove $name from blacklist",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WillLoadSection(sheets: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "WILL LOAD · ${sheets.size}",
            style = KineticType.labelCaps,
            color = Colors.TextSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sheets.forEach { sheet ->
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .border(BorderStroke(1.dp, Colors.Border), Shapes.pill)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sheet,
                        style = KineticType.bodySm.copy(fontWeight = FontWeight.SemiBold),
                        color = Colors.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WriteResultsCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    Card {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Write results to the sheet",
                    style = KineticType.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                    color = Colors.TextPrimary,
                )
                Text(
                    text = "Logged sets and notes go into each exercise's WYNIK cell. Needs edit access.",
                    style = KineticType.bodySm,
                    color = Colors.TextSecondary,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Colors.Primary,
                    checkedThumbColor = Colors.OnPrimary,
                    checkedBorderColor = Colors.Primary,
                    uncheckedTrackColor = Colors.Surface2,
                    uncheckedThumbColor = Colors.TextSecondary,
                    uncheckedBorderColor = Colors.Border,
                ),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.xl,
        color = Colors.Surface1,
        border = BorderStroke(1.dp, Colors.Border),
        content = content,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = KineticType.bodyMd.copy(fontWeight = FontWeight.SemiBold),
        color = Colors.TextPrimary,
    )
}

@Composable
private fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
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

@Preview(
    name = "Setup",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFF0B0F14,
)
@Composable
private fun SetupScreenPreview() {
    WorkoutTheme {
        SetupScreen(
            email = "your.name@gmail.com",
            sheetUrl = "docs.google.com/spreadsheets/d/1dpBR…/edit",
            onSheetUrlChange = {},
            sheetsFound = 10,
            sheetsToLoad = listOf(
                "LUTY", "MARZEC", "KWIECIEŃ", "MAJ",
                "CZERWIEC", "LIPIEC", "SIERPIEŃ", "WRZESIEŃ",
            ),
            blacklist = listOf("Zawodnik", "Ogólne"),
            onRemoveFromBlacklist = {},
            blacklistInput = "",
            onBlacklistInputChange = {},
            onAddToBlacklist = {},
            writeResults = true,
            onWriteResultsChange = {},
            onSignOut = {},
            onSave = {},
        )
    }
}

@Preview(
    name = "Loading saved values",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFF0B0F14,
)
@Composable
private fun SetupScreenLoadingPreview() {
    WorkoutTheme {
        SetupScreen(
            email = "",
            sheetUrl = "",
            onSheetUrlChange = {},
            sheetsFound = null,
            sheetsToLoad = emptyList(),
            blacklist = emptyList(),
            onRemoveFromBlacklist = {},
            blacklistInput = "",
            onBlacklistInputChange = {},
            onAddToBlacklist = {},
            writeResults = true,
            onWriteResultsChange = {},
            onSignOut = {},
            onSave = {},
            enabled = false,
        )
    }
}
