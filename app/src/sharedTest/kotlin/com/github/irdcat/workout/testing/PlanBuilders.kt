package com.github.irdcat.workout.testing

import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.ExerciseNumber
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.SetGroup
import com.github.irdcat.workout.domain.workout.TempoPattern
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutItem
import java.time.LocalDate
import java.time.YearMonth

// Small hand-built plans for tests that need exact control over what is done.

fun group(
    sets: Int? = 1,
    reps: Reps = Reps.Count(5),
    load: Load = Load.Weight(60.0),
    rest: Int? = 150,
    tempo: String? = null,
    result: String? = null,
) = SetGroup(
    id = "g${counter++}",
    sheetRow = counter,
    sets = sets?.let { SetCount.Fixed(it) } ?: SetCount.Open,
    reps = reps,
    load = load,
    tempo = tempo?.let(::TempoPattern),
    restSeconds = rest,
    result = result,
)

fun exercise(id: String, number: String, name: String, vararg groups: SetGroup) = Exercise(
    id = id,
    number = ExerciseNumber(number.filter(Char::isDigit).toInt(), number.lastOrNull()?.takeIf(Char::isLetter)),
    name = name,
    setGroups = groups.toList(),
)

fun workout(id: String, code: String, vararg items: WorkoutItem) = Workout(id, code, items.toList())

fun single(exercise: Exercise) = WorkoutItem.Single(exercise)

fun superset(number: Int, vararg exercises: Exercise) = WorkoutItem.Superset(number, exercises.toList())

fun week(id: String, start: String, vararg workouts: Workout, sheet: String = "CZERWIEC") =
    LocalDate.parse(start).let { TrainingWeek(id, it, it.plusDays(6), sheet, isRestWeek = false, workouts = workouts.toList()) }

fun months(vararg weeks: TrainingWeek): List<TrainingMonth> =
    weeks.groupBy { it.month }.toSortedMap().map { (month: YearMonth, list) -> TrainingMonth(month, list) }

private var counter = 0
