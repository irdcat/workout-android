package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

// ---- UI state ------------------------------------------------------------

@Immutable
data class ResultEditorUiState(
    /** e.g. "2" or "5b". */
    val exerciseLabel: String,
    val exerciseName: String,
    /** Sheet rows the result can be written to, e.g. "Last row · 2×5 92.5 kg". */
    val targets: List<String>,
    val selectedTarget: Int,
    /** The result as it will be saved, "RPE x" from [rpe] included. */
    val text: String,
    /** Snippets built from this session's logged sets, e.g. "75×5". */
    val suggestions: List<String>,
    val lastTime: LastResult? = null,
    /** Reviewing before the exercise is finished: saving finishes it. */
    val review: Boolean = false,
    /** How hard the exercise was; required unless the result is cleared. */
    val rpe: String = "",
    /** The selected row's RPE target, e.g. "8.5"; an equal [rpe] is left out of the result. */
    val rpeTarget: String? = null,
) {
    /** An RPE of 1–10 is given, or everything is empty, which clears the result. */
    val canSave: Boolean
        get() = rpe.toDoubleOrNull()?.let { it in 1.0..10.0 } ?: (rpe.isEmpty() && text.isBlank())
}

@Immutable
data class LastResult(
    /** e.g. "25 May"; null when last time wasn't trained in the app (the sheet keeps no dates). */
    val date: String?,
    val text: String,
)

sealed interface ResultEditorEvent {
    data class TextChanged(val value: String) : ResultEditorEvent
    data class TargetSelected(val index: Int) : ResultEditorEvent
    data class Insert(val snippet: String) : ResultEditorEvent
    /** Empty clears the RPE. */
    data class RpeChanged(val value: String) : ResultEditorEvent
    /** [direction] is -1 or +1, a step of 0.5. */
    data class AdjustRpe(val direction: Int) : ResultEditorEvent
    data object UseLastTime : ResultEditorEvent
    data object Clear : ResultEditorEvent
    data object Save : ResultEditorEvent
    data object Dismiss : ResultEditorEvent
}

// ---- Route ---------------------------------------------------------------

/**
 * Result editor for one exercise, backed by [ResultEditorViewModel]. Shows the
 * sheet once the exercise is loaded and calls [onClose] when it is dismissed or saved,
 * after [onSaved] when saved. With [review], saving also finishes the exercise.
 */
@Composable
fun ResultEditorRoute(
    exerciseId: String,
    onClose: () -> Unit,
    review: Boolean = false,
    onSaved: () -> Unit = {},
) {
    val viewModel = hiltViewModel<ResultEditorViewModel, ResultEditorViewModel.Factory>(
        key = "result-editor-$exerciseId-$review",
        creationCallback = { factory -> factory.create(exerciseId, review) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.closed) { saved ->
        if (saved) onSaved()
        onClose()
    }
    (state as? Loadable.Ready)?.let { editor ->
        ResultEditorSheet(state = editor.value, onEvent = viewModel::onEvent)
    }
}

// ---- Sheet ---------------------------------------------------------------

/** Shows the result editor over the current screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultEditorSheet(
    state: ResultEditorUiState,
    onEvent: (ResultEditorEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(ResultEditorEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = Colors.Surface1,
        scrimColor = Colors.Canvas.copy(alpha = 0.68f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Colors.Surface3) },
    ) {
        ResultEditorContent(state = state, onEvent = onEvent)
    }
}

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultEditorContent(
    state: ResultEditorUiState,
    onEvent: (ResultEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Label(
                    if (state.review) "Review result · exercise ${state.exerciseLabel}" else "Result · exercise ${state.exerciseLabel}",
                )
                Text(
                    text = state.exerciseName.uppercase(),
                    style = TextStyles.headlineLg.copy(fontSize = 36.sp, lineHeight = 36.sp),
                    color = Colors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(
                onClick = { onEvent(ResultEditorEvent.Dismiss) },
                modifier = Modifier.size(44.dp),
                shape = Shapes.xl,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Colors.Surface2,
                    contentColor = Colors.TextPrimary,
                ),
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close without saving")
            }
        }

        if (state.targets.isNotEmpty()) {
            TargetPicker(
                targets = state.targets,
                selected = state.selectedTarget,
                onSelect = { onEvent(ResultEditorEvent.TargetSelected(it)) },
            )
        }

        Column {
            Label("Your result · any text", modifier = Modifier.padding(bottom = 6.dp))
            OutlinedTextField(
                value = state.text,
                onValueChange = { onEvent(ResultEditorEvent.TextChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                textStyle = TextStyles.bodyLg.copy(fontSize = 17.sp, lineHeight = 25.sp),
                shape = Shapes.xl,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Colors.Canvas,
                    unfocusedContainerColor = Colors.Canvas,
                    focusedBorderColor = Colors.Primary,
                    unfocusedBorderColor = Colors.Primary,
                    cursorColor = Colors.Primary,
                    focusedTextColor = Colors.TextPrimary,
                    unfocusedTextColor = Colors.TextPrimary,
                ),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WideStepper(
                label = "RPE · required",
                what = "RPE",
                value = state.rpe,
                onValueChange = { onEvent(ResultEditorEvent.RpeChanged(it)) },
                onAdjust = { onEvent(ResultEditorEvent.AdjustRpe(it)) },
                keyboardType = KeyboardType.Decimal,
            )
            state.rpeTarget?.let { target ->
                Text(
                    text = if (state.rpe.toBigDecimalOrNull() == target.toBigDecimalOrNull()) {
                        "As planned (RPE $target): not added to the result"
                    } else {
                        "Plan: RPE $target"
                    },
                    style = TextStyles.bodyNote,
                    color = Colors.TextSecondary,
                )
            }
        }

        if (state.suggestions.isNotEmpty()) {
            Column {
                Label("Insert from this session", modifier = Modifier.padding(bottom = 6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.suggestions.forEach { snippet ->
                        InsertChip(text = snippet, onClick = { onEvent(ResultEditorEvent.Insert(snippet)) })
                    }
                }
            }
        }

        state.lastTime?.let { last ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Colors.Canvas, Shapes.xl)
                    .border(1.dp, Colors.Border, Shapes.xl)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Label(listOfNotNull("Last time", last.date).joinToString(" · "))
                    Text(
                        text = "“${last.text}”",
                        style = TextStyles.bodyMd,
                        color = Colors.TextPrimary,
                    )
                }
                SheetButton(text = "Use", onClick = { onEvent(ResultEditorEvent.UseLastTime) }, minHeight = 44)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetButton(text = "Clear", onClick = { onEvent(ResultEditorEvent.Clear) }, minHeight = 56)
            Button(
                onClick = { onEvent(ResultEditorEvent.Save) },
                enabled = state.canSave,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                shape = Shapes.xl,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.Primary,
                    contentColor = Colors.OnPrimary,
                ),
            ) {
                Text(
                    text = if (state.review) "Save and finish exercise" else "Save result",
                    style = TextStyles.buttonLabelLg,
                )
            }
        }
    }
}

@Composable
private fun TargetPicker(targets: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val canChoose = targets.size > 1
    Box {
        Surface(
            onClick = { expanded = true },
            enabled = canChoose,
            modifier = Modifier.fillMaxWidth(),
            shape = Shapes.xl,
            color = Colors.Surface2,
            contentColor = Colors.TextPrimary,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Write to", style = TextStyles.bodyNote, color = Colors.TextSecondary)
                Text(
                    text = targets.getOrElse(selected) { targets.first() },
                    modifier = Modifier.weight(1f),
                    style = TextStyles.bodyNoteStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (canChoose) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Colors.TextSecondary,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Colors.Surface2,
        ) {
            targets.forEachIndexed { index, target ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = target,
                            style = TextStyles.bodyMd,
                            color = if (index == selected) Colors.Primary else Colors.TextPrimary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun InsertChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = Shapes.pill,
        color = Colors.Surface2,
        contentColor = Colors.TextPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Colors.Primary,
                modifier = Modifier.size(14.dp),
            )
            Text(text = text, style = TextStyles.dataValue.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit, minHeight: Int) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = minHeight.dp),
        shape = Shapes.xl,
        colors = ButtonDefaults.buttonColors(
            containerColor = Colors.Surface2,
            contentColor = Colors.TextPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Text(text = text, style = TextStyles.rowTitle)
    }
}

// ---- Previews ------------------------------------------------------------

/**
 * ModalBottomSheet opens in its own window, which previews don't render,
 * so these draw the sheet body docked at the bottom over a dimmed canvas.
 */
@Composable
private fun ResultEditorPreviewFrame(state: ResultEditorUiState) {
    WorkoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Colors.Canvas),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SheetShape,
                color = Colors.Surface1,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .background(Colors.Surface3, Shapes.pill),
                    )
                    ResultEditorContent(state = state, onEvent = {})
                }
            }
        }
    }
}

@Preview(name = "Result editor", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun ResultEditorPreview() {
    ResultEditorPreviewFrame(
        ResultEditorUiState(
            exerciseLabel = "2",
            exerciseName = "RDL",
            targets = listOf("Last row · 2×5 92.5 kg", "Row 3 · 1×5 90 kg", "Row 2 · 1×6 75 kg"),
            selectedTarget = 0,
            text = "chwyt puszczał, RPE 9",
            suggestions = listOf("92.5×5, 92.5×5", "Tempo off"),
            lastTime = LastResult(date = "25 May", text = "RPE 7"),
            rpe = "9",
        )
    )
}

@Preview(name = "Result editor, first time", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun ResultEditorFirstTimePreview() {
    ResultEditorPreviewFrame(
        ResultEditorUiState(
            exerciseLabel = "5b",
            exerciseName = "Reverse Flies Hantlami",
            targets = listOf("2×10 6 kg"),
            selectedTarget = 0,
            text = "",
            suggestions = listOf("6×10, 6×10"),
            rpeTarget = "8",
        )
    )
}
