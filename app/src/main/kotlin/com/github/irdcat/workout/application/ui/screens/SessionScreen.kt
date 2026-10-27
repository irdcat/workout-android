package com.github.irdcat.workout.application.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.components.Chevron
import com.github.irdcat.workout.application.ui.components.Chip
import com.github.irdcat.workout.application.ui.components.CollectEvents
import com.github.irdcat.workout.application.ui.components.ConfirmDialog
import com.github.irdcat.workout.application.ui.components.Label
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.LoadingScreen
import com.github.irdcat.workout.application.ui.components.PrimaryButton
import com.github.irdcat.workout.application.ui.components.RestChip
import com.github.irdcat.workout.application.ui.components.SecondaryButton
import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.application.ui.components.TempoBadge
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.KineticType
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

// ---- UI state ------------------------------------------------------------

@Immutable
data class SessionUiState(
    /** e.g. "T2". */
    val workoutName: String,
    /** e.g. "1 – 7 Jun". */
    val weekRange: String,
    /** Session clock, e.g. "12:04"; null when "measure workout time" is off. */
    val elapsed: String?,
    /** One segment per exercise in the workout. */
    val progress: List<ProgressSegment>,
    val exercise: SessionExercise,
    /** The set being entered; null when there's nothing to log ([SessionExercise.Review], [SessionExercise.Complete]). */
    val entry: SetEntry?,
    /** Warm-up sets logged for the exercise being logged; they don't count towards the plan. */
    val warmUpSets: Int = 0,
)

enum class ProgressSegment { Done, Current, Pending }

/** What is being logged right now; each case has its own header and log label. */
sealed interface SessionExercise {

    /** Sets prescribed by the sheet, e.g. ramp-up sets then working sets. */
    data class Planned(
        val position: Int,
        val exerciseCount: Int,
        val name: String,
        val tempo: Tempo?,
        val restSeconds: Int?,
        /** Sets logged so far, e.g. Set 1 · 60 kg × 6. */
        val done: List<SetRow>,
        val set: Int,
        val setCount: Int,
        /** e.g. "5 reps × 92.5 kg". */
        val target: String,
        /** Planned sets after this one, e.g. Set 5 · 92.5 kg × 5. */
        val upcoming: List<SetRow>,
    ) : SessionExercise

    /** Exercises done alternately, round by round. */
    data class Superset(
        val index: Int,
        /** Set order, round by round, e.g. [[5a, 5b, 5c], [5a, 5b, 5c], [5a]]. */
        val rounds: List<List<SupersetSlot>>,
        val current: SupersetMember,
        /** Sets logged so far, e.g. 5a R1 · 15 kg × 12. */
        val done: List<SetRow>,
        /** The set after this one, e.g. 5c R2 · 8 reps. */
        val next: SetRow?,
    ) : SessionExercise

    /**
     * Sets the athlete decides on as they go, often against a time limit; [unlimited] ones
     * (an empty sets cell) have none, and the rest after each set is picked on the set card.
     */
    data class OpenSets(
        val position: Int,
        val exerciseCount: Int,
        /** Free text from the sheet; may be long. */
        val name: String,
        /** e.g. "5 reps". */
        val repsPerSet: String?,
        /** e.g. "40 kg". */
        val startWeight: String?,
        val tempo: Tempo?,
        /** null when there is no prescribed rest. */
        val restSeconds: Int?,
        val stopwatch: Stopwatch?,
        val done: List<SetRow>,
        val set: Int,
        val unlimited: Boolean = false,
    ) : SessionExercise

    /** Every set of the exercise is logged: its result is reviewed before going on. */
    data class Review(
        val position: Int,
        val exerciseCount: Int,
        val exerciseId: String,
        /** e.g. "RDL", or "5b · Reverse Flies" in a superset. */
        val name: String,
        val done: List<SetRow>,
    ) : SessionExercise

    /** Every exercise is finished: nothing more to log. */
    data object Complete : SessionExercise
}

/** One set in a list: done, or planned. */
@Immutable
data class SetRow(
    /** e.g. "Set 1", or "R1" after a superset [tag]. */
    val label: String,
    /** e.g. "60 kg × 6" or "8 reps". */
    val value: String,
    /** e.g. "RPE 8" or "after this". */
    val note: String? = null,
    /** Superset member, e.g. "5a". */
    val tag: String? = null,
)

@Immutable
data class SupersetSlot(val tag: String, val status: ProgressSegment)

@Immutable
data class SupersetMember(
    /** e.g. "5b". */
    val tag: String,
    val name: String,
    val round: Int,
    /** e.g. "10 reps × 6 kg". */
    val target: String,
    val restSeconds: Int?,
)

@Immutable
data class Stopwatch(
    /** e.g. "6:42". */
    val elapsed: String,
    /** e.g. "10:00"; null without a limit. */
    val limit: String?,
    val running: Boolean,
)

/** The values being entered for the set about to be logged. */
@Immutable
data class SetEntry(
    /** e.g. "Weight (kg)" or "Each DB (kg)". */
    val weightLabel: String = "Weight (kg)",
    val weight: String,
    val weightStep: String,
    /** What the count is: "Reps", "Seconds", "Lengths", "Steps" or "Reps per side". */
    val repsLabel: String = "Reps",
    val reps: String,
    /** How much −/+ changes [reps]: 5 for seconds, else 1. */
    val repsStep: Int = 1,
    /** Empty for none. */
    val rpe: String = "",
    val rpeStep: String = "0.5",
    val lastTime: LastTime? = null,
    /** Rest after the set as "m:ss", picked by the user; null when the sheet's rest applies. */
    val rest: String? = null,
)

/**
 * The same exercise last time. The sheet keeps only results, so [date] and [summary]
 * are there only when that session was logged in the app; at least one of
 * [summary] and [note] is set.
 */
@Immutable
data class LastTime(
    /** e.g. "25 May". */
    val date: String?,
    /** e.g. "100 kg × 2". */
    val summary: String?,
    /** The result text written back then, e.g. "RPE 7". */
    val note: String?,
)

sealed interface SessionEvent {
    data object Back : SessionEvent
    data object FinishWorkout : SessionEvent
    /** End the workout without saving: everything logged in it is deleted. */
    data object AbandonWorkout : SessionEvent
    data class WeightChanged(val value: String) : SessionEvent
    /** [direction] is -1 or +1; the step is [SetEntry.weightStep]. */
    data class AdjustWeight(val direction: Int) : SessionEvent
    data class WeightStepSelected(val step: String) : SessionEvent
    data class RepsChanged(val value: String) : SessionEvent
    data class AdjustReps(val direction: Int) : SessionEvent
    /** Empty clears the RPE. */
    data class RpeChanged(val value: String) : SessionEvent
    /** [direction] is -1 or +1; the step is [SetEntry.rpeStep]. */
    data class AdjustRpe(val direction: Int) : SessionEvent
    data class RpeStepSelected(val step: String) : SessionEvent
    /** Only for [SetEntry.rest]. */
    data class RestChanged(val value: String) : SessionEvent
    /** ±15 s; [direction] is -1 or +1. */
    data class AdjustRest(val direction: Int) : SessionEvent
    /** Open the warm-up sets of the exercise being logged. */
    data object OpenWarmUp : SessionEvent
    data object ToggleStopwatch : SessionEvent
    data object LogSet : SessionEvent
    data object OpenResult : SessionEvent
    /** Open the result waiting for review. */
    data object ReviewResult : SessionEvent
    data object FinishExercise : SessionEvent
}

internal val WeightSteps = listOf("0.5", "1", "2.5", "5")
private val RpeSteps = listOf("0.5", "1")

// ---- Route ---------------------------------------------------------------

/** Logging backed by [SessionViewModel]; only navigation leaves this composable. */
@Composable
fun SessionRoute(
    onBack: () -> Unit,
    onFinishWorkout: () -> Unit,
    onRestStarted: () -> Unit,
    onOpenWarmUp: () -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    // Reviewing finishes the exercise when the result is saved.
    var reviewing by rememberSaveable { mutableStateOf(false) }
    CollectEvents(viewModel.navigation) { event ->
        when (event) {
            SessionNavigation.RestStarted -> onRestStarted()
            is SessionNavigation.EditResult -> {
                editingExerciseId = event.exerciseId
                reviewing = false
            }
            is SessionNavigation.ReviewResult -> {
                editingExerciseId = event.exerciseId
                reviewing = true
            }
            SessionNavigation.SessionEnded -> onSessionEnded()
        }
    }
    when (val session = state) {
        Loadable.Loading -> LoadingScreen()
        is Loadable.Ready -> SessionScreen(
            state = session.value,
            onEvent = { event ->
                when (event) {
                    SessionEvent.Back -> onBack()
                    SessionEvent.FinishWorkout -> onFinishWorkout()
                    SessionEvent.OpenWarmUp -> onOpenWarmUp()
                    else -> viewModel.onEvent(event)
                }
            },
        )
    }
    editingExerciseId?.let { id ->
        ResultEditorRoute(
            exerciseId = id,
            review = reviewing,
            onClose = { editingExerciseId = null },
            onSaved = { if (reviewing) viewModel.onResultReviewed(id) },
        )
    }
}

// ---- Screen --------------------------------------------------------------

/** What a confirmation is being asked for. */
private enum class Confirming { FinishWorkout, AbandonWorkout, FinishExercise }

/**
 * Finishing or abandoning the workout and finishing an exercise are asked about first;
 * [onEvent] gets them once confirmed.
 */
@Composable
fun SessionScreen(
    state: SessionUiState,
    onEvent: (SessionEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf<Confirming?>(null) }
    val askFirst: (SessionEvent) -> Unit = { event ->
        when (event) {
            SessionEvent.FinishWorkout -> confirming = Confirming.FinishWorkout
            SessionEvent.AbandonWorkout -> confirming = Confirming.AbandonWorkout
            SessionEvent.FinishExercise -> confirming = Confirming.FinishExercise
            else -> onEvent(event)
        }
    }
    SessionContent(state = state, onEvent = askFirst, modifier = modifier)
    when (confirming) {
        Confirming.FinishWorkout -> FinishWorkoutDialog(
            state = state,
            onConfirm = {
                confirming = null
                onEvent(SessionEvent.FinishWorkout)
            },
            onDismiss = { confirming = null },
        )
        Confirming.AbandonWorkout -> AbandonWorkoutDialog(
            workoutName = state.workoutName,
            onConfirm = {
                confirming = null
                onEvent(SessionEvent.AbandonWorkout)
            },
            onDismiss = { confirming = null },
        )
        Confirming.FinishExercise -> FinishExerciseDialog(
            exercise = state.exercise,
            onConfirm = {
                confirming = null
                onEvent(SessionEvent.FinishExercise)
            },
            onDismiss = { confirming = null },
        )
        null -> Unit
    }
}

@Composable
private fun FinishWorkoutDialog(state: SessionUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val open = state.progress.count { it != ProgressSegment.Done }
    ConfirmDialog(
        title = "Finish ${state.workoutName}?",
        text = when (open) {
            0 -> "Every exercise is done. Next is the summary, where the workout is saved."
            1 -> "1 of ${state.progress.size} exercises isn't finished yet; it stays as it is. " +
                "Next is the summary, where the workout is saved."
            else -> "$open of ${state.progress.size} exercises aren't finished yet; they stay as they are. " +
                "Next is the summary, where the workout is saved."
        },
        confirmLabel = "Finish workout",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Shared with the warm-up screen, which abandons the same workout. */
@Composable
internal fun AbandonWorkoutDialog(workoutName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Abandon $workoutName?",
        text = "Every set logged and every result saved in this workout is deleted, and nothing is written " +
            "to the sheet. This can't be undone.",
        confirmLabel = "Abandon workout",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

@Composable
private fun FinishExerciseDialog(exercise: SessionExercise, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (name, logged) = when (exercise) {
        is SessionExercise.Planned -> exercise.name to "${exercise.done.size} of ${exercise.setCount} sets logged; the rest are skipped."
        is SessionExercise.Superset -> "Superset ${exercise.index}" to
            "${exercise.done.size} of ${exercise.rounds.sumOf { it.size }} sets logged; the rest are skipped."
        is SessionExercise.OpenSets -> exercise.name to
            "${plural(exercise.done.size, "set")} logged."
        is SessionExercise.Review, SessionExercise.Complete -> return
    }
    ConfirmDialog(
        title = "Finish $name?",
        text = "$logged You'll review the result next.",
        confirmLabel = "Finish exercise",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun SessionContent(
    state: SessionUiState,
    onEvent: (SessionEvent) -> Unit,
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
                onBack = { onEvent(SessionEvent.Back) },
                onFinish = { onEvent(SessionEvent.FinishWorkout) },
                onAbandon = { onEvent(SessionEvent.AbandonWorkout) },
            )
            ExerciseProgressBar(segments = state.progress)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val entry = state.entry
                when (val exercise = state.exercise) {
                    is SessionExercise.Planned -> if (entry != null) PlannedContent(exercise, entry, state.warmUpSets, onEvent)
                    is SessionExercise.Superset -> if (entry != null) SupersetContent(exercise, entry, state.warmUpSets, onEvent)
                    is SessionExercise.OpenSets -> if (entry != null) OpenSetsContent(exercise, entry, state.warmUpSets, onEvent)
                    is SessionExercise.Review -> ReviewContent(exercise)
                    SessionExercise.Complete -> CompleteContent()
                }
            }
            when (val exercise = state.exercise) {
                is SessionExercise.Planned -> SessionActions(logLabel = "Log set ${exercise.set}", onEvent = onEvent)
                is SessionExercise.Superset ->
                    SessionActions(logLabel = "Log ${exercise.current.tag} · round ${exercise.current.round}", onEvent = onEvent)
                is SessionExercise.OpenSets -> SessionActions(logLabel = "Log set ${exercise.set}", onEvent = onEvent)
                is SessionExercise.Review -> PrimaryButton(text = "Review result", icon = Icons.Outlined.Edit) {
                    onEvent(SessionEvent.ReviewResult)
                }
                SessionExercise.Complete -> PrimaryButton(text = "Finish workout", icon = Icons.Outlined.Flag) {
                    onEvent(SessionEvent.FinishWorkout)
                }
            }
        }
    }
}

// ---- Per-exercise layouts ------------------------------------------------

@Composable
private fun PlannedContent(
    exercise: SessionExercise.Planned,
    entry: SetEntry,
    warmUpSets: Int,
    onEvent: (SessionEvent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label("Exercise ${exercise.position} of ${exercise.exerciseCount}")
        Title(exercise.name)
        MetaRow {
            exercise.tempo?.let { TempoBadge(tempo = it) }
            exercise.restSeconds?.let { RestChip(seconds = it) }
        }
    }
    DoneSets(exercise.done)
    SetCard(entry = entry, warmUpSets = warmUpSets, onEvent = onEvent) { warmUp ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Colors.Primary)) { append("SET ${exercise.set}") }
                        withStyle(SpanStyle(color = Colors.TextSecondary, fontWeight = FontWeight.SemiBold)) {
                            append(" of ${exercise.setCount}")
                        }
                    },
                    style = SetHeadingStyle,
                )
                TargetText(exercise.target)
            }
            warmUp()
        }
    }
    NextSets(exercise.upcoming)
}

@Composable
private fun SupersetContent(
    exercise: SessionExercise.Superset,
    entry: SetEntry,
    warmUpSets: Int,
    onEvent: (SessionEvent) -> Unit,
) {
    val tags = exercise.rounds.flatten().map { it.tag }.distinct()
    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label("Superset · exercises ${tags.first()} – ${tags.last()}")
        Title("Superset ${exercise.index}", color = Colors.Primary)
    }
    SupersetSequence(rounds = exercise.rounds)
    DoneSets(exercise.done)
    val member = exercise.current
    SetCard(entry = entry, warmUpSets = warmUpSets, onEvent = onEvent) { warmUp ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "ROUND ${member.round} · ${member.tag}",
                modifier = Modifier.weight(1f),
                style = SetHeadingStyle,
                color = Colors.Primary,
            )
            warmUp()
        }
        Column {
            Text(
                text = member.name,
                style = KineticType.bodyLg.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                color = Colors.TextPrimary,
            )
            TargetText(member.target + (member.restSeconds?.let { " · rest $it s after" } ?: ""))
        }
    }
    NextSets(listOfNotNull(exercise.next))
}

@Composable
private fun OpenSetsContent(
    exercise: SessionExercise.OpenSets,
    entry: SetEntry,
    warmUpSets: Int,
    onEvent: (SessionEvent) -> Unit,
) {
    var showFullName by rememberSaveable(exercise.name) { mutableStateOf(false) }
    var nameOverflows by rememberSaveable(exercise.name) { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label("Exercise ${exercise.position} of ${exercise.exerciseCount}")
        Text(
            text = exercise.name.uppercase(),
            style = KineticType.headlineLg.copy(fontSize = 30.sp, lineHeight = 32.sp),
            color = Colors.TextPrimary,
            maxLines = if (showFullName) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!showFullName) nameOverflows = it.hasVisualOverflow },
        )
        if (nameOverflows || showFullName) {
            TextButton(
                onClick = { showFullName = !showFullName },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = if (showFullName) "Show less" else "Show full text",
                    style = KineticType.bodyNoteStrong,
                    color = Colors.Primary,
                )
            }
        }
        MetaRow {
            Chip(text = if (exercise.unlimited) "Unlimited sets" else "Open sets", contentColor = Colors.Primary)
            exercise.repsPerSet?.let { Chip(text = it) }
            exercise.startWeight?.let { Chip(text = "Start $it") }
            exercise.tempo?.let { TempoBadge(tempo = it) }
            // An unlimited set's rest is picked on the set card instead.
            if (!exercise.unlimited) {
                exercise.restSeconds?.let { RestChip(seconds = it) }
                    ?: Chip(text = "No rest set", contentColor = Colors.TextSecondary)
            }
        }
    }
    exercise.stopwatch?.let { stopwatch ->
        StopwatchCard(stopwatch = stopwatch, onToggle = { onEvent(SessionEvent.ToggleStopwatch) })
    }
    DoneSets(exercise.done)
    SetCard(entry = entry, warmUpSets = warmUpSets, onEvent = onEvent) { warmUp ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SET ${exercise.set}",
                modifier = Modifier.weight(1f),
                style = SetHeadingStyle,
                color = Colors.Primary,
            )
            warmUp()
        }
    }
}

/** The sets just finished, and why the session waits: the result needs a look first. */
@Composable
private fun ReviewContent(review: SessionExercise.Review) {
    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label("Exercise ${review.position} of ${review.exerciseCount} · review")
        Title(review.name)
        Text(
            text = "Every set is logged. The result is filled in from your sets: check it before moving on. " +
                "Saving it finishes the exercise.",
            style = KineticType.bodyNote,
            color = Colors.TextSecondary,
        )
    }
    DoneSets(review.done)
}

@Composable
private fun CompleteContent() {
    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label("Workout")
        Title("All exercises done")
        Text(
            text = "Every exercise is finished. Finish the workout to see the summary and save it.",
            style = KineticType.bodyNote,
            color = Colors.TextSecondary,
        )
    }
}

// ---- Header pieces -------------------------------------------------------

@Composable
internal fun SessionTopBar(
    workoutName: String,
    weekRange: String,
    elapsed: String?,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onAbandon: () -> Unit,
    backDescription: String = "Back to workout",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledIconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = Shapes.xl,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Colors.Surface2,
                contentColor = Colors.TextPrimary,
            ),
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    append(workoutName)
                    withStyle(SpanStyle(color = Colors.TextSecondary, fontWeight = FontWeight.Medium)) {
                        append(" · $weekRange")
                    }
                },
                style = KineticType.rowTitle,
                color = Colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            elapsed?.let {
                Text(
                    text = it,
                    style = KineticType.dataValue.copy(fontWeight = FontWeight.SemiBold),
                    color = Colors.TextSecondary,
                )
            }
        }
        SecondaryButton(text = "Finish", onClick = onFinish, minHeight = 44)
        WorkoutMenu(onAbandon = onAbandon)
    }
}

/** Rarer workout actions, kept out of the way of "Finish". */
@Composable
private fun WorkoutMenu(onAbandon: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More workout actions",
                tint = Colors.TextSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Colors.Surface2,
        ) {
            DropdownMenuItem(
                text = { Text(text = "Abandon workout", style = KineticType.bodyMd, color = Colors.Critical) },
                onClick = {
                    expanded = false
                    onAbandon()
                },
            )
        }
    }
}

@Composable
private fun ExerciseProgressBar(segments: List<ProgressSegment>) {
    val current = segments.indexOfFirst { it == ProgressSegment.Current } + 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Exercise $current of ${segments.size}" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(segment.color(), Shapes.pill),
            )
        }
    }
}

private fun ProgressSegment.color(): Color = when (this) {
    ProgressSegment.Done -> Colors.Primary
    ProgressSegment.Current -> Colors.Primary.copy(alpha = 0.5f)
    ProgressSegment.Pending -> Colors.Border
}

@Composable
private fun SupersetSequence(rounds: List<List<SupersetSlot>>) {
    val description = rounds.joinToString(", ") { round -> round.joinToString(" ") { it.tag } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Order of sets: $description" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rounds.forEachIndexed { index, round ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(Colors.Border, Shapes.pill),
                )
            }
            round.forEach { slot ->
                val isCurrent = slot.status == ProgressSegment.Current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(if (isCurrent) Colors.Primary else Colors.Surface1, Shapes.lg)
                        .border(1.dp, if (isCurrent) Colors.Primary else Colors.Border, Shapes.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = slot.tag,
                        style = KineticType.dataValue,
                        color = when (slot.status) {
                            ProgressSegment.Done -> Colors.Primary
                            ProgressSegment.Current -> Colors.OnPrimary
                            ProgressSegment.Pending -> Colors.TextSecondary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StopwatchCard(stopwatch: Stopwatch, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Surface1, Shapes.xl)
            .border(1.dp, Colors.Border, Shapes.xl)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stopwatch.elapsed,
                style = KineticType.dataDisplayXl.copy(fontSize = 34.sp, lineHeight = 34.sp),
                color = Colors.TextPrimary,
            )
            Text(
                text = buildString {
                    append("since first set")
                    stopwatch.limit?.let { append(" · limit $it") }
                },
                style = KineticType.bodySm,
                color = Colors.TextSecondary,
            )
        }
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(48.dp),
            shape = Shapes.xl,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Colors.Surface2,
                contentColor = Colors.TextPrimary,
            ),
        ) {
            Icon(
                imageVector = if (stopwatch.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (stopwatch.running) "Pause stopwatch" else "Resume stopwatch",
            )
        }
    }
}

// ---- Set entry card ------------------------------------------------------

internal val SetHeadingStyle = KineticType.headlineMd.copy(fontWeight = FontWeight.Bold)

/**
 * The highlighted card for the set being logged. [heading] receives the
 * warm-up link so each layout can place it next to its own title.
 */
@Composable
private fun SetCard(
    entry: SetEntry,
    warmUpSets: Int,
    onEvent: (SessionEvent) -> Unit,
    heading: @Composable (warmUp: @Composable () -> Unit) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Surface1, Shapes.xl)
            .border(1.5.dp, Colors.Primary, Shapes.xl)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        heading {
            WarmUpLink(sets = warmUpSets, onClick = { onEvent(SessionEvent.OpenWarmUp) })
        }
        WideStepper(
            label = entry.weightLabel,
            what = "weight",
            value = entry.weight,
            onValueChange = { onEvent(SessionEvent.WeightChanged(it)) },
            onAdjust = { onEvent(SessionEvent.AdjustWeight(it)) },
            keyboardType = KeyboardType.Decimal,
            steps = WeightSteps,
            step = entry.weightStep,
            onStep = { onEvent(SessionEvent.WeightStepSelected(it)) },
        )
        WideStepper(
            label = entry.repsLabel,
            what = entry.repsLabel.lowercase(),
            value = entry.reps,
            onValueChange = { onEvent(SessionEvent.RepsChanged(it)) },
            onAdjust = { onEvent(SessionEvent.AdjustReps(it)) },
            keyboardType = KeyboardType.Number,
        )
        WideStepper(
            label = "RPE (optional)",
            what = "RPE",
            value = entry.rpe,
            onValueChange = { onEvent(SessionEvent.RpeChanged(it)) },
            onAdjust = { onEvent(SessionEvent.AdjustRpe(it)) },
            keyboardType = KeyboardType.Decimal,
            steps = RpeSteps,
            step = entry.rpeStep,
            onStep = { onEvent(SessionEvent.RpeStepSelected(it)) },
        )
        entry.rest?.let { rest ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WideStepper(
                    label = "Rest after · mm:ss",
                    what = "rest after",
                    value = rest,
                    onValueChange = { onEvent(SessionEvent.RestChanged(it)) },
                    onAdjust = { onEvent(SessionEvent.AdjustRest(it)) },
                    keyboardType = KeyboardType.Number,
                )
                Text(
                    text = "Your time: it starts from the one you picked last.",
                    style = KineticType.bodySm,
                    color = Colors.TextSecondary,
                )
            }
        }
        entry.lastTime?.let { last ->
            HorizontalDivider(color = Colors.Border)
            LastTimeText(last)
        }
    }
}

/**
 * A full-width −/+ stepper with its label, and below it the size of a step when
 * [steps] are given ("STEP 0.5 1 2.5 5").
 */
@Composable
internal fun WideStepper(
    label: String,
    what: String,
    value: String,
    onValueChange: (String) -> Unit,
    onAdjust: (direction: Int) -> Unit,
    keyboardType: KeyboardType,
    steps: List<String> = emptyList(),
    step: String? = null,
    onStep: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WideStepperButton(icon = Icons.Filled.Remove, description = "Decrease $what") { onAdjust(-1) }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(Colors.Canvas, Shapes.xl)
                    .border(1.dp, Colors.Border, Shapes.xl)
                    .semantics { contentDescription = what.replaceFirstChar { it.uppercase() } },
                textStyle = KineticType.dataDisplayLg.copy(
                    fontSize = 36.sp,
                    color = Colors.TextPrimary,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
                cursorBrush = SolidColor(Colors.Primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } },
            )
            WideStepperButton(icon = Icons.Filled.Add, description = "Increase $what") { onAdjust(+1) }
        }
        if (steps.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "STEP",
                    modifier = Modifier.width(44.dp),
                    style = KineticType.labelCaps,
                    color = Colors.TextSecondary,
                )
                steps.forEach { option ->
                    val selected = option == step
                    Surface(
                        selected = selected,
                        onClick = { onStep(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .semantics { contentDescription = "${what.replaceFirstChar { it.uppercase() }} step $option" },
                        shape = Shapes.lg,
                        color = if (selected) Colors.Surface3 else Colors.Surface2,
                        contentColor = if (selected) Colors.TextPrimary else Colors.TextSecondary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = option, style = KineticType.dataValue.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WideStepperButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 64.dp, height = 52.dp),
        shape = Shapes.xl,
        color = Colors.Surface2,
        contentColor = Colors.TextPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(24.dp))
        }
    }
}

// ---- Set lists -----------------------------------------------------------

/** "DONE · 3 SETS" and a row per set; nothing before the first set. */
@Composable
private fun DoneSets(rows: List<SetRow>) {
    if (rows.isEmpty()) return
    SetList(title = if (rows.size == 1) "Done · 1 set" else "Done · ${rows.size} sets", rows = rows, done = true)
}

@Composable
private fun NextSets(rows: List<SetRow>) {
    if (rows.isEmpty()) return
    SetList(title = "Next", rows = rows, done = false)
}

@Composable
private fun SetList(title: String, rows: List<SetRow>, done: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Colors.Surface1, Shapes.xl)
                .border(1.dp, Colors.Border, Shapes.xl)
                .padding(horizontal = 14.dp),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = Colors.Border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Colors.Primary,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .border(2.dp, Colors.TextSecondary, CircleShape),
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            row.tag?.let {
                                withStyle(SpanStyle(color = Colors.Primary)) { append(it) }
                                append(" ")
                            }
                            append(row.label)
                        },
                        modifier = Modifier.width(52.dp),
                        style = KineticType.dataValue.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        color = Colors.TextSecondary,
                        maxLines = 1,
                    )
                    Text(
                        text = row.value,
                        modifier = Modifier.weight(1f),
                        style = KineticType.dataValue.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                        color = if (done) Colors.TextPrimary else Colors.TextSecondary,
                    )
                    row.note?.let {
                        Text(text = it, style = KineticType.bodySm, color = Colors.TextSecondary)
                    }
                }
            }
        }
    }
}

/** Opens the warm-up sets; they're logged on their own screen, apart from the plan. */
@Composable
private fun WarmUpLink(sets: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .semantics { contentDescription = "Warm-up sets, separate from the prescribed sets" },
        shape = Shapes.xl,
        color = Colors.Surface2,
        contentColor = Colors.TextPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                tint = Colors.Primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = when (sets) {
                    0 -> "Warm-up"
                    1 -> "Warm-up · 1 set"
                    else -> "Warm-up · $sets sets"
                },
                style = KineticType.bodyNoteStrong,
            )
            Chevron(16.dp)
        }
    }
}

/** The weight −/+ step, e.g. 2.5 kg a tap. */
@Composable
internal fun WeightStepRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WeightSteps.forEach { step ->
            val isSelected = step == selected
            Surface(
                selected = isSelected,
                onClick = { onSelect(step) },
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .semantics { contentDescription = "Weight step $step" },
                shape = Shapes.lg,
                color = if (isSelected) Colors.Surface3 else Colors.Surface2,
                contentColor = if (isSelected) Colors.TextPrimary else Colors.TextSecondary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = step,
                        style = KineticType.bodySm.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/** "LAST TIME · 25 MAY  100 kg × 2 · “RPE 7”". */
@Composable
internal fun LastTimeText(last: LastTime) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = Colors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = KineticType.labelCaps.letterSpacing,
                )
            ) {
                append(listOfNotNull("LAST TIME", last.date?.uppercase()).joinToString(" · ") + "  ")
            }
            last.summary?.let(::append)
            last.note?.let {
                if (last.summary != null) append(" · ")
                withStyle(SpanStyle(color = Colors.TextSecondary)) { append("“$it”") }
            }
        },
        style = KineticType.bodySm,
        color = Colors.TextPrimary,
    )
}

@Composable
internal fun Stepper(
    value: String,
    onValueChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    keyboardType: KeyboardType,
    what: String,
    decreaseDescription: String = "Decrease $what",
    increaseDescription: String = "Increase $what",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepperButton(icon = Icons.Filled.Remove, description = decreaseDescription, onClick = onDecrease)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .background(Colors.Canvas, Shapes.xl)
                .border(1.dp, Colors.Border, Shapes.xl)
                .semantics { contentDescription = what.replaceFirstChar { it.uppercase() } },
            textStyle = KineticType.dataDisplayLg.copy(
                fontSize = 30.sp,
                color = Colors.TextPrimary,
                textAlign = TextAlign.Center,
            ),
            singleLine = true,
            cursorBrush = SolidColor(Colors.Primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) { inner() }
            },
        )
        StepperButton(icon = Icons.Filled.Add, description = increaseDescription, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 38.dp, height = 52.dp),
        shape = Shapes.xl,
        color = Colors.Surface2,
        contentColor = Colors.TextPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(18.dp))
        }
    }
}

// ---- Bottom actions ------------------------------------------------------

@Composable
private fun SessionActions(logLabel: String, onEvent: (SessionEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(text = logLabel, icon = Icons.Filled.Check) { onEvent(SessionEvent.LogSet) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Result",
                onClick = { onEvent(SessionEvent.OpenResult) },
                modifier = Modifier.weight(1f),
                icon = {
                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            SecondaryButton(
                text = "Finish exercise",
                onClick = { onEvent(SessionEvent.FinishExercise) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---- Small pieces --------------------------------------------------------

@Composable
internal fun Title(text: String, color: Color = Colors.TextPrimary) {
    Text(
        text = text.uppercase(),
        style = KineticType.headlineXl.copy(fontSize = 44.sp, lineHeight = 44.sp),
        color = color,
    )
}

@Composable
private fun TargetText(target: String) {
    Text(
        text = "Target $target",
        style = KineticType.bodyNote,
        color = Colors.TextSecondary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetsRow(label: String, content: @Composable RowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label, modifier = Modifier.padding(end = 4.dp))
        content()
    }
}

@Composable
internal fun LoggedChip(text: String) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(Colors.Surface1, Shapes.lg)
            .border(BorderStroke(1.dp, Colors.Border), Shapes.lg)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Colors.Primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = KineticType.dataValue.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = Colors.TextPrimary,
            maxLines = 1,
        )
    }
}

// ---- Previews ------------------------------------------------------------

private fun segments(done: Int, current: Int, total: Int) = List(total) {
    when {
        it < done -> ProgressSegment.Done
        it < done + current -> ProgressSegment.Current
        else -> ProgressSegment.Pending
    }
}

@Composable
private fun SessionPreviewContent(state: SessionUiState) {
    WorkoutTheme {
        SessionScreen(state = state, onEvent = {})
    }
}

@Preview(name = "Planned sets", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun SessionPlannedPreview() {
    SessionPreviewContent(
        SessionUiState(
            workoutName = "T2",
            weekRange = "1 – 7 Jun",
            elapsed = "12:04",
            progress = segments(done = 1, current = 1, total = 7),
            exercise = SessionExercise.Planned(
                position = 2,
                exerciseCount = 7,
                name = "RDL",
                tempo = Tempo("3010", appliesTo = "last 2 sets"),
                restSeconds = 150,
                done = listOf(
                    SetRow("Set 1", "60 kg × 6"),
                    SetRow("Set 2", "75 kg × 6"),
                    SetRow("Set 3", "90 kg × 5", note = "RPE 8"),
                ),
                set = 4,
                setCount = 5,
                target = "5 reps × 92.5 kg",
                upcoming = listOf(SetRow("Set 5", "92.5 kg × 5", note = "after this")),
            ),
            entry = SetEntry(
                weight = "92.5",
                weightStep = "2.5",
                reps = "5",
                rpe = "8.5",
                lastTime = LastTime(date = "25 May", summary = "100 kg × 2", note = "RPE 7"),
            ),
            warmUpSets = 2,
        )
    )
}

@Preview(name = "Uneven superset", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun SessionSupersetPreview() {
    val d = ProgressSegment.Done
    val c = ProgressSegment.Current
    val p = ProgressSegment.Pending
    SessionPreviewContent(
        SessionUiState(
            workoutName = "T2",
            weekRange = "1 – 7 Jun",
            elapsed = "51:12",
            progress = segments(done = 4, current = 3, total = 7),
            exercise = SessionExercise.Superset(
                index = 5,
                rounds = listOf(
                    listOf(SupersetSlot("5a", d), SupersetSlot("5b", d), SupersetSlot("5c", d)),
                    listOf(SupersetSlot("5a", d), SupersetSlot("5b", c), SupersetSlot("5c", p)),
                    listOf(SupersetSlot("5a", p)),
                ),
                current = SupersetMember(
                    tag = "5b",
                    name = "Reverse Flies Hantlami",
                    round = 2,
                    target = "10 reps × 6 kg",
                    restSeconds = 45,
                ),
                done = listOf(
                    SetRow("R1", "15 kg × 12", tag = "5a"),
                    SetRow("R1", "6 kg × 10", tag = "5b"),
                    SetRow("R1", "8 reps", note = "RPE 8.5", tag = "5c"),
                    SetRow("R2", "15 kg × 11", tag = "5a"),
                ),
                next = SetRow("R2", "8 reps", note = "RPE 8.5", tag = "5c"),
            ),
            entry = SetEntry(
                weightLabel = "Each DB (kg)",
                weight = "6",
                weightStep = "1",
                reps = "10",
                rpe = "8",
                lastTime = LastTime(date = "25 May", summary = "6 kg × 10", note = "RPE 7.5"),
            ),
        )
    )
}

@Preview(name = "Open sets", widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun SessionOpenSetsPreview() {
    SessionPreviewContent(
        SessionUiState(
            workoutName = "T3",
            weekRange = "31 Aug – 6 Sep",
            elapsed = "08:12",
            progress = segments(done = 0, current = 1, total = 6),
            exercise = SessionExercise.OpenSets(
                position = 1,
                exerciseCount = 6,
                name = "Przysiad Piramidka ciężaru 15/15/10/10 Dalej 5-2,5 - Czas: 10:00 od pierwszej serii",
                repsPerSet = "5 reps",
                startWeight = "40 kg",
                tempo = Tempo("2010"),
                restSeconds = null,
                stopwatch = Stopwatch(elapsed = "6:42", limit = "10:00", running = true),
                done = listOf(
                    SetRow("Set 1", "40 kg × 5"),
                    SetRow("Set 2", "55 kg × 5"),
                    SetRow("Set 3", "70 kg × 5"),
                    SetRow("Set 4", "80 kg × 5", note = "RPE 8"),
                ),
                set = 5,
            ),
            entry = SetEntry(
                weight = "90",
                weightStep = "5",
                reps = "5",
                rpe = "9",
                lastTime = LastTime(date = "17 Aug", summary = "95 kg", note = "9:23"),
            ),
        )
    )
}
