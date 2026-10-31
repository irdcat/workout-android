package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.AddWithSuggestionsField
import com.github.irdcat.workout.application.ui.components.Card
import com.github.irdcat.workout.application.ui.components.ConfirmDialog
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.components.RemovableChip
import com.github.irdcat.workout.application.ui.components.SecondaryButton
import com.github.irdcat.workout.application.ui.components.fieldColors
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.TextStyles
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import com.github.irdcat.workout.domain.analysis.DefaultExerciseGroups
import com.github.irdcat.workout.domain.analysis.isFreeGroupName

// ---- UI state ------------------------------------------------------------

@Immutable
data class AnalysisGroupsUiState(
    /** In the order Analysis offers them. */
    val groups: List<GroupEditor>,
    /** The groups are the ones a new install has, so there's nothing to reset. */
    val isDefault: Boolean,
)

@Immutable
data class GroupEditor(
    val name: String,
    /** Names as they're written in the sheet. */
    val exercises: List<String>,
    /** What's typed in its "Add an exercise" field. */
    val input: String = "",
    /** Exercises from the sheet matching [input] that aren't in the group yet. */
    val suggestions: List<String> = emptyList(),
)

sealed interface AnalysisGroupsEvent {
    data object Back : AnalysisGroupsEvent
    data class AddGroup(val name: String) : AnalysisGroupsEvent
    data class RenameGroup(val name: String, val newName: String) : AnalysisGroupsEvent
    /** Sent only after the user confirms. */
    data class RemoveGroup(val name: String) : AnalysisGroupsEvent
    /** [direction] is -1 (up) or +1 (down). */
    data class MoveGroup(val name: String, val direction: Int) : AnalysisGroupsEvent
    data class InputChanged(val group: String, val value: String) : AnalysisGroupsEvent
    /** Adds what's typed in the group's field. */
    data class AddExercise(val group: String) : AnalysisGroupsEvent
    data class PickSuggestion(val group: String, val exercise: String) : AnalysisGroupsEvent
    data class RemoveExercise(val group: String, val exercise: String) : AnalysisGroupsEvent
    /** Sent only after the user confirms. */
    data object ResetToDefaults : AnalysisGroupsEvent
}

// ---- Route ---------------------------------------------------------------

/** Analysis groups backed by [AnalysisGroupsViewModel]; only going back leaves this composable. */
@Composable
fun AnalysisGroupsRoute(
    onBack: () -> Unit,
    viewModel: AnalysisGroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val groups = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> AnalysisGroupsScreen(
            state = groups.value,
            onEvent = { event -> if (event == AnalysisGroupsEvent.Back) onBack() else viewModel.onEvent(event) },
        )
    }
}

// ---- Screen --------------------------------------------------------------

@Composable
fun AnalysisGroupsScreen(
    state: AnalysisGroupsUiState,
    onEvent: (AnalysisGroupsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The group being named: "" for a new one (a name is never blank), null when no one is.
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = Colors.Canvas) {
        GroupList(
            state = state,
            onEvent = onEvent,
            onAdd = { naming = "" },
            onRename = { naming = it },
            onRemove = { removing = it },
            onReset = { confirmReset = true },
        )
    }

    naming?.let { old ->
        val isNew = old.isEmpty()
        NameDialog(
            initial = old,
            title = if (isNew) "New group" else "Rename group",
            canConfirm = { isFreeGroupName(state.groups.map { it.name }, it, renaming = old.ifEmpty { null }) },
            onConfirm = { name ->
                naming = null
                onEvent(if (isNew) AnalysisGroupsEvent.AddGroup(name) else AnalysisGroupsEvent.RenameGroup(old, name))
            },
            onDismiss = { naming = null },
        )
    }
    removing?.let { name ->
        ConfirmDialog(
            title = "Remove $name?",
            text = "Analysis stops showing it. Nothing logged is lost.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                removing = null
                onEvent(AnalysisGroupsEvent.RemoveGroup(name))
            },
            onDismiss = { removing = null },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset to defaults?",
            text = "Your groups are replaced by Squat, Bench and Deadlift with their usual names.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = {
                confirmReset = false
                onEvent(AnalysisGroupsEvent.ResetToDefaults)
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun GroupList(
    state: AnalysisGroupsUiState,
    onEvent: (AnalysisGroupsEvent) -> Unit,
    onAdd: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(onBack = { onEvent(AnalysisGroupsEvent.Back) })
        if (state.groups.isEmpty()) {
            Text(text = "No groups, so Analysis has nothing to show.", style = TextStyles.bodyNoteStrong, color = Colors.TextPrimary)
        }
        state.groups.forEachIndexed { index, group ->
            GroupCard(
                group = group,
                first = index == 0,
                last = index == state.groups.lastIndex,
                onEvent = onEvent,
                onRename = { onRename(group.name) },
                onRemove = { onRemove(group.name) },
            )
        }
        SecondaryButton(
            text = "Add group",
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp)) },
        )
        if (!state.isDefault) {
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Reset to defaults", color = Colors.Critical, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    FilledIconButton(
        onClick = onBack,
        modifier = Modifier.size(44.dp),
        shape = Shapes.xl,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Colors.Surface2, contentColor = Colors.TextPrimary),
    ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
    Column {
        Label("Settings")
        Text(text = "ANALYSIS GROUPS", style = TextStyles.screenTitle, color = Colors.TextPrimary)
    }
    Text(
        text = "Analysis compares each group as one exercise. An exercise in the sheet counts when its name " +
            "matches one listed here, ignoring case, accents and notes in brackets. Each exercise counts " +
            "toward one group: adding it to a group takes it out of the others.",
        style = TextStyles.bodyNote,
        color = Colors.TextSecondary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(
    group: GroupEditor,
    first: Boolean,
    last: Boolean,
    onEvent: (AnalysisGroupsEvent) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    modifier = Modifier.weight(1f),
                    style = TextStyles.bodyLg.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = Colors.TextPrimary,
                )
                GroupAction(Icons.Outlined.KeyboardArrowUp, "Move ${group.name} up", enabled = !first) {
                    onEvent(AnalysisGroupsEvent.MoveGroup(group.name, -1))
                }
                GroupAction(Icons.Outlined.KeyboardArrowDown, "Move ${group.name} down", enabled = !last) {
                    onEvent(AnalysisGroupsEvent.MoveGroup(group.name, +1))
                }
                GroupAction(Icons.Outlined.Edit, "Rename ${group.name}", onClick = onRename)
                GroupAction(Icons.Outlined.Delete, "Remove ${group.name}", onClick = onRemove)
            }
            if (group.exercises.isEmpty()) {
                Text(text = "No exercises yet.", style = TextStyles.bodySm, color = Colors.TextSecondary)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.exercises.forEach { exercise ->
                        RemovableChip(
                            name = exercise,
                            removeDescription = "Remove $exercise from ${group.name}",
                            onRemove = { onEvent(AnalysisGroupsEvent.RemoveExercise(group.name, exercise)) },
                        )
                    }
                }
            }
            AddWithSuggestionsField(
                input = group.input,
                onInputChange = { onEvent(AnalysisGroupsEvent.InputChanged(group.name, it)) },
                onAdd = { onEvent(AnalysisGroupsEvent.AddExercise(group.name)) },
                suggestions = group.suggestions,
                onPickSuggestion = { onEvent(AnalysisGroupsEvent.PickSuggestion(group.name, it)) },
                placeholder = "Add an exercise",
                addDescription = "Add to ${group.name}",
            )
        }
    }
}

@Composable
private fun GroupAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Colors.TextSecondary, disabledContentColor = Colors.Surface3),
    ) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** Asks for a group name; [canConfirm] says whether it's free (and not blank). */
@Composable
private fun NameDialog(
    initial: String,
    title: String,
    canConfirm: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val valid = canConfirm(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Colors.Surface1,
        titleContentColor = Colors.TextPrimary,
        textContentColor = Colors.TextSecondary,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Overhead press") },
                    singleLine = true,
                    textStyle = TextStyles.bodyMd.copy(fontSize = 16.sp),
                    shape = Shapes.xl,
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                if (name.isNotBlank() && !valid) {
                    Text(text = "There's already a group with this name.", style = TextStyles.bodySm, color = Colors.Critical)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = valid) {
                Text("Save", color = if (valid) Colors.Primary else Colors.TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Colors.TextPrimary)
            }
        },
    )
}

// ---- Previews ------------------------------------------------------------

@Preview(name = "Analysis groups", widthDp = 390, heightDp = 1100, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun AnalysisGroupsPreview() {
    WorkoutTheme {
        AnalysisGroupsScreen(
            state = AnalysisGroupsUiState(
                groups = DefaultExerciseGroups.map { GroupEditor(it.name, it.exercises) } + GroupEditor("OHP", listOf("OHP")),
                isDefault = false,
            ),
            onEvent = {},
        )
    }
}
