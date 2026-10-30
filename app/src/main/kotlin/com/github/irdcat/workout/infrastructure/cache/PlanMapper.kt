package com.github.irdcat.workout.infrastructure.cache

import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.ExerciseNumber
import com.github.irdcat.workout.domain.workout.SetGroup
import com.github.irdcat.workout.domain.workout.SheetNotation
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutItem
import com.github.irdcat.workout.domain.workout.withSharedRests
import java.time.LocalDate

// Room doesn't keep @Relation lists in order, so everything is sorted by position here.

fun List<WeekWithWorkouts>.toMonths(): List<TrainingMonth> =
    map { it.toDomain() }
        .groupBy { it.month }
        .toSortedMap()
        .map { (month, weeks) -> TrainingMonth(month, weeks.sortedBy { it.start }) }

fun WeekWithWorkouts.toDomain(): TrainingWeek = TrainingWeek(
    id = week.id,
    start = LocalDate.ofEpochDay(week.startEpochDay),
    end = LocalDate.ofEpochDay(week.endEpochDay),
    sheetName = week.sheetName,
    isRestWeek = week.isRestWeek,
    workouts = workouts.sortedBy { it.workout.position }.map { it.toDomain() },
)

fun WorkoutWithExercises.toDomain(): Workout {
    val exercises = exercises.sortedBy { it.exercise.position }.map { it.toDomain() }
    return Workout(id = workout.id, code = workout.code, items = groupSupersets(exercises))
}

private fun ExerciseWithGroups.toDomain(): Exercise = Exercise(
    id = exercise.id,
    // A number the notation doesn't recognise still keeps the exercise in order.
    number = SheetNotation.exerciseNumber(exercise.number) ?: ExerciseNumber(exercise.position + 1, null),
    name = exercise.name,
    setGroups = groups.sortedBy { it.position }.map { it.toDomain() }.withSharedRests(),
)

private fun SetGroupEntity.toDomain(): SetGroup = SetGroup(
    id = id,
    sheetRow = sheetRow,
    sets = SheetNotation.sets(sets),
    reps = SheetNotation.reps(reps),
    load = SheetNotation.load(load),
    tempo = SheetNotation.tempo(tempo),
    restSeconds = SheetNotation.restSeconds(rest),
    result = SheetNotation.result(result),
    note = note,
)

/** Consecutive lettered exercises with the same number (5a, 5b, 5c) form a superset. */
private fun groupSupersets(exercises: List<Exercise>): List<WorkoutItem> {
    val items = mutableListOf<WorkoutItem>()
    var members = mutableListOf<Exercise>()

    fun flush() {
        when (members.size) {
            0 -> Unit
            1 -> items += WorkoutItem.Single(members.single())
            else -> items += WorkoutItem.Superset(members.first().number.index, members)
        }
        members = mutableListOf()
    }

    for (exercise in exercises) {
        val lettered = exercise.number.member != null
        val sameGroup = members.isNotEmpty() && members.first().number.index == exercise.number.index
        if (!lettered || !sameGroup) flush()
        if (lettered) members += exercise else items += WorkoutItem.Single(exercise)
    }
    flush()
    return items
}
