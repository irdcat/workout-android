package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.components.ConfirmDialog
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.components.SecondaryButton
import com.github.irdcat.workout.application.ui.components.dashedBorder
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

// ---- UI state ------------------------------------------------------------

@Immutable
data class WarmUpUiState(
    /** e.g. "T2". */
    val workoutName: String,
    /** e.g. "1 – 7 Jun". */
    val weekRange: String,
    /** Session clock; null when "measure workout time" is off. */
    val elapsed: String?,
    val position: Int,
    val exerciseCount: Int,
    val exerciseName: String,
    /** Sets the sheet prescribes; null for open sets. */
    val plannedSets: Int?,
    /** Warm-ups logged so far as "kg×reps", e.g. "20×10". */
    val done: List<String>,
    /** The warm-up being logged, 1-based. */
    val number: Int,
    val entry: WarmUpEntry,
    /** What the count is, as for the exercise's sets: "Reps", "Seconds"… */
    val repsLabel: String = "Reps",
    /** The warm-ups logged last time, e.g. "20×10, 40×8, 60×5"; null when none were. */
    val lastTime: LastTime?,
)

/** The values being entered for the warm-up about to be logged. */
@Immutable
data class WarmUpEntry(
    val weight: String,
    val weightStep: String,
    val reps: String,
    /** Rest after it as "m:ss". */
    val rest: String,
)

sealed interface WarmUpEvent {
    data object Back : WarmUpEvent
    data object FinishWorkout : WarmUpEvent
    /** End the workout without saving: everything logged in it is deleted. */
    data object AbandonWorkout : WarmUpEvent
    data object StartWorkingSets : WarmUpEvent
    data class WeightChanged(val value: String) : WarmUpEvent
    data class AdjustWeight(val direction: Int) : WarmUpEvent
    data class WeightStepSelected(val step: String) : WarmUpEvent
    data class RepsChanged(val value: String) : WarmUpEvent
    data class AdjustReps(val direction: Int) : WarmUpEvent
    data class RestChanged(val value: String) : WarmUpEvent
    /** ±15 s; [direction] is -1 or +1. */
    data class AdjustRest(val direction: Int) : WarmUpEvent
    data object LogWarmUp : WarmUpEvent
}

// ---- Route ---------------------------------------------------------------

/** Warm-up sets backed by [WarmUpViewModel]; only navigation leaves this composable. */
@Composable
fun WarmUpRoute(
    onBack: () -> Unit,
    onFinishWorkout: () -> Unit,
    onRestStarted: () -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: WarmUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEvents(viewModel.navigation) { event ->
        when (event) {
            WarmUpNavigation.RestStarted -> onRestStarted()
            WarmUpNavigation.SessionEnded -> onSessionEnded()
        }
    }
    when (val warmUp = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> WarmUpScreen(
            state = warmUp.value,
            onEvent = { event ->
                when (event) {
                    // Working sets are the screen underneath.
                    WarmUpEvent.Back, WarmUpEvent.StartWorkingSets -> onBack()
                    WarmUpEvent.FinishWorkout -> onFinishWorkout()
                    else -> viewModel.onEvent(event)
                }
            },
        )
    }
}

// ---- Screen --------------------------------------------------------------

/** Finishing or abandoning the workout is asked about first; [onEvent] gets it once confirmed. */
@Composable
fun WarmUpScreen(
    state: WarmUpUiState,
    onEvent: (WarmUpEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmAbandon by rememberSaveable { mutableStateOf(false) }
    WarmUpContent(
        state = state,
        onEvent = { event ->
            when (event) {
                WarmUpEvent.FinishWorkout -> confirmFinish = true
                WarmUpEvent.AbandonWorkout -> confirmAbandon = true
                else -> onEvent(event)
            }
        },
        modifier = modifier,
    )
    if (confirmAbandon) {
        AbandonWorkoutDialog(
            workoutName = state.workoutName,
            onConfirm = {
                confirmAbandon = false
                onEvent(WarmUpEvent.AbandonWorkout)
            },
            onDismiss = { confirmAbandon = false },
        )
    }
    if (confirmFinish) {
        ConfirmDialog(
            title = "Finish ${state.workoutName}?",
            text = "Exercises not finished yet stay as they are. Next is the summary, where the workout is saved.",
            confirmLabel = "Finish workout",
            onConfirm = {
                confirmFinish = false
                onEvent(WarmUpEvent.FinishWorkout)
            },
            onDismiss = { confirmFinish = false },
        )
    }
}

@Composable
private fun WarmUpContent(
    state: WarmUpUiState,
    onEvent: (WarmUpEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Colors.Canvas) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SessionTopBar(
                workoutName = state.workoutName,
                weekRange = state.weekRange,
                elapsed = state.elapsed,
                onBack = { onEvent(WarmUpEvent.Back) },
                onFinish = { onEvent(WarmUpEvent.FinishWorkout) },
                onAbandon = { onEvent(WarmUpEvent.AbandonWorkout) },
                backDescription = "Back to working sets",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Label("Exercise ${state.position} of ${state.exerciseCount} · warm-up")
                    Title(state.exerciseName)
                    Text(
                        text = buildString {
                            append("Warm-up sets are separate from the ")
                            append(
                                when (state.plannedSets) {
                                    null -> "prescribed sets"
                                    1 -> "1 prescribed set"
                                    else -> "${state.plannedSets} prescribed sets"
                                }
                            )
                            append(". They aren't counted and aren't written to the sheet.")
                        },
                        style = KineticType.bodyNote,
                        color = Colors.TextSecondary,
                    )
                }
                if (state.done.isNotEmpty()) {
                    SetsRow(label = "Warm-up done · kg × reps") {
                        state.done.forEach { LoggedChip(it) }
                    }
                }
                WarmUpCard(state = state, onEvent = onEvent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(WarmUpEvent.LogWarmUp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = Shapes.xl,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.Primary,
                        contentColor = Colors.OnPrimary,
                    ),
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Log warm-up ${state.number}", style = KineticType.buttonLabelLg)
                }
                SecondaryButton(
                    text = "Start working sets",
                    onClick = { onEvent(WarmUpEvent.StartWorkingSets) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Dashed, unlike the working set's card: this one isn't part of the plan. */
@Composable
private fun WarmUpCard(state: WarmUpUiState, onEvent: (WarmUpEvent) -> Unit) {
    val entry = state.entry
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Surface1, Shapes.xl)
            .dashedBorder(Colors.TextSecondary, radius = 16.dp, width = 1.5.dp, dash = 6.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "WARM-UP ${state.number}", style = SetHeadingStyle, color = Colors.TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1.25f)) {
                Label("Weight (kg)", modifier = Modifier.padding(bottom = 6.dp))
                Stepper(
                    value = entry.weight,
                    onValueChange = { onEvent(WarmUpEvent.WeightChanged(it)) },
                    onDecrease = { onEvent(WarmUpEvent.AdjustWeight(-1)) },
                    onIncrease = { onEvent(WarmUpEvent.AdjustWeight(+1)) },
                    keyboardType = KeyboardType.Decimal,
                    what = "weight",
                )
                WeightStepRow(
                    selected = entry.weightStep,
                    onSelect = { onEvent(WarmUpEvent.WeightStepSelected(it)) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Label(state.repsLabel, modifier = Modifier.padding(bottom = 6.dp))
                Stepper(
                    value = entry.reps,
                    onValueChange = { onEvent(WarmUpEvent.RepsChanged(it)) },
                    onDecrease = { onEvent(WarmUpEvent.AdjustReps(-1)) },
                    onIncrease = { onEvent(WarmUpEvent.AdjustReps(+1)) },
                    keyboardType = KeyboardType.Number,
                    what = state.repsLabel.lowercase(),
                )
            }
        }
        HorizontalDivider(color = Colors.Border)
        Column {
            Label("Rest after · mm:ss", modifier = Modifier.padding(bottom = 6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.width(180.dp)) {
                    Stepper(
                        value = entry.rest,
                        onValueChange = { onEvent(WarmUpEvent.RestChanged(it)) },
                        onDecrease = { onEvent(WarmUpEvent.AdjustRest(-1)) },
                        onIncrease = { onEvent(WarmUpEvent.AdjustRest(+1)) },
                        keyboardType = KeyboardType.Number,
                        what = "rest after",
                        decreaseDescription = "Subtract 15 seconds",
                        increaseDescription = "Add 15 seconds",
                    )
                }
                Text(
                    text = "Your time. Not in the sheet, so it starts from your last one.",
                    modifier = Modifier.weight(1f),
                    style = KineticType.bodySm,
                    color = Colors.TextSecondary,
                )
            }
        }
        state.lastTime?.let { last ->
            HorizontalDivider(color = Colors.Border)
            LastTimeText(last)
        }
    }
}

// ---- Previews ------------------------------------------------------------

@Preview(name = "Warm-up", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun WarmUpPreview() {
    WorkoutTheme {
        WarmUpScreen(
            state = WarmUpUiState(
                workoutName = "T2",
                weekRange = "1 – 7 Jun",
                elapsed = "11:36",
                position = 2,
                exerciseCount = 7,
                exerciseName = "RDL",
                plannedSets = 5,
                done = listOf("20×10", "40×8"),
                number = 3,
                entry = WarmUpEntry(weight = "60", weightStep = "5", reps = "5", rest = "1:00"),
                lastTime = LastTime(date = "25 May", summary = "20×10, 40×8, 60×5", note = null),
            ),
            onEvent = {},
        )
    }
}
