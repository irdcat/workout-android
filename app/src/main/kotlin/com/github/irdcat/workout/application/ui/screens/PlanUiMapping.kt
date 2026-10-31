package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import java.math.BigDecimal
import java.time.LocalDate
import com.github.irdcat.workout.domain.workout.SetGroup as PlanSetGroup
import com.github.irdcat.workout.domain.workout.WorkoutItem as PlanItem

// Domain plan → screen state.

fun TrainingWeek.timing(today: LocalDate): WeekTiming = when {
    today in this -> WeekTiming.Current
    end.isBefore(today) -> WeekTiming.Past
    else -> WeekTiming.Upcoming
}

/** The sheet only says whether results are filled in, so there is no completion day. */
fun Workout.uiStatus(): WorkoutStatus = when {
    isCompleted -> WorkoutStatus.Completed(day = null)
    isStarted -> WorkoutStatus.InProgress
    else -> WorkoutStatus.NotStarted
}

fun Workout.toSummary(): WorkoutSummary = WorkoutSummary(
    id = id,
    name = code,
    title = title(),
    exerciseCount = exercises.size,
    supersetCount = items.count { it is PlanItem.Superset },
    status = uiStatus(),
)

fun Workout.toDetailItems(): List<WorkoutItem> = items.mapIndexed { position, item ->
    // Number items by position so a gap in the sheet's numbering doesn't show.
    val index = position + 1
    when (item) {
        is PlanItem.Single -> WorkoutItem.Single(index, item.exercise.toDetail())
        is PlanItem.Superset -> WorkoutItem.Superset(index, item.exercises.map { it.toDetail() })
    }
}

fun Exercise.toDetail(): ExerciseDetail = ExerciseDetail(
    id = id,
    name = name,
    setGroups = setGroups.map { it.toUi() },
    restSeconds = setGroups.firstNotNullOfOrNull { it.restSeconds },
    tempo = exerciseTempo(setGroups),
    result = setGroups.mapNotNull { it.result }.joinToString(" · ").ifEmpty { null },
    progress = if (isDone) ExerciseProgress.Done else ExerciseProgress.Pending,
)

private fun PlanSetGroup.toUi(): SetGroup = SetGroup(
    sets = (sets as? SetCount.Fixed)?.count,
    reps = reps.label(),
    load = load.label(),
    unlimited = sets == SetCount.Unlimited,
)

/**
 * One tempo for the exercise: shown as is when every group shares it, or with
 * "last group"/"last N groups" when only the trailing groups have it.
 */
fun exerciseTempo(groups: List<PlanSetGroup>): Tempo? {
    val tempos = groups.map { it.tempo?.phases }
    val first = tempos.firstNotNullOfOrNull { it } ?: return null
    if (tempos.all { it == first }) return Tempo(first)
    val trailing = tempos.takeLastWhile { it == first }.size
    val rest = tempos.dropLast(trailing)
    return when {
        trailing > 0 && rest.all { it == null } ->
            Tempo(first, appliesTo = if (trailing == 1) "last group" else "last $trailing groups")
        else -> Tempo(first, appliesTo = "some groups")
    }
}

fun Reps.label(): String = when (this) {
    is Reps.Count -> "$count"
    is Reps.Range -> "$min–$max"
    is Reps.Duration -> if (minSeconds == maxSeconds) "$minSeconds s" else "$minSeconds–$maxSeconds s"
    is Reps.PerSide -> "$count/side"
    is Reps.Steps -> "$count steps"
    is Reps.Lengths -> "$count lengths"
    is Reps.UpTo -> "≤$count"
    is Reps.Other -> raw
}

fun Load.label(): String = when (this) {
    is Load.Weight -> "${kg.kg()} kg"
    is Load.Implements -> "$count×${kgEach.kg()} kg"
    is Load.Range -> "${minKg.kg()}–${maxKg.kg()} kg"
    is Load.Choice -> kg.joinToString("/") { it.kg() } + " kg"
    is Load.Rpe -> "RPE ${value.kg()}"
    Load.Bodyweight -> "BW"
    is Load.Other -> raw
}

/** 60.0 → "60", 92.5 → "92.5". */
fun Double.kg(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
