package com.github.irdcat.workout.domain.workout

import java.time.LocalDate
import java.time.YearMonth

/**
 * A calendar month of training. Weeks belong to the month holding most of their
 * days, so a week can come from the next month's sheet (see [TrainingWeek.sheetName]).
 */
data class TrainingMonth(
    val month: YearMonth,
    val weeks: List<TrainingWeek>,
)

data class TrainingWeek(
    /** Stable for a given sheet layout; changes if rows above it are inserted. */
    val id: String,
    val start: LocalDate,
    val end: LocalDate,
    /** Tab the week was read from, e.g. "CZERWIEC". */
    val sheetName: String,
    /** Marked "WOLNE" in the sheet. */
    val isRestWeek: Boolean,
    val workouts: List<Workout>,
) {
    /** The month holding most of the week's days (the Thursday's month for Mon–Sun weeks). */
    val month: YearMonth
        get() = YearMonth.from(start.plusDays((end.toEpochDay() - start.toEpochDay()) / 2))

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val completedCount: Int get() = workouts.count { it.isCompleted }
}

data class Workout(
    /** e.g. "CZERWIEC2026/1/T1": tab, year of the tab's first week, week number in the tab, name. */
    val id: String,
    /** Name from the sheet, only a code such as "T1" or "L1". */
    val code: String,
    val items: List<WorkoutItem>,
) {
    val exercises: List<Exercise> get() = items.flatMap { it.exercises }

    /** The sheet has no status column: a workout is done once every exercise has a result. */
    val isCompleted: Boolean get() = exercises.isNotEmpty() && exercises.all { it.isDone }

    val isStarted: Boolean get() = exercises.any { it.isDone }

    /** First exercise names, used as a subtitle, e.g. "Przysiad · Bench Press". */
    fun title(maxExercises: Int = 2): String =
        exercises.take(maxExercises).joinToString(" · ") { it.name }
}

/** An exercise on its own, or a superset of exercises numbered 5a, 5b, … */
sealed interface WorkoutItem {
    /** The exercise number without its letter, e.g. 5 for 5a–5c. */
    val number: Int
    val exercises: List<Exercise>

    data class Single(val exercise: Exercise) : WorkoutItem {
        override val number: Int get() = exercise.number.index
        override val exercises: List<Exercise> get() = listOf(exercise)
    }

    data class Superset(override val number: Int, override val exercises: List<Exercise>) : WorkoutItem {
        /** Rounds = the largest set count; members with fewer sets sit out the last rounds. */
        val rounds: Int get() = exercises.maxOfOrNull { it.setCount ?: 0 } ?: 0
    }
}

data class Exercise(
    val id: String,
    val number: ExerciseNumber,
    val name: String,
    /** Rows of the exercise: ramp-up, working and back-off groups. */
    val setGroups: List<SetGroup>,
) {
    /** Done means a result is filled in on any of its rows. */
    val isDone: Boolean get() = setGroups.any { it.result != null }

    /** Total planned sets, or null when any group is open-ended. */
    val setCount: Int?
        get() = setGroups.fold(0 as Int?) { total, group ->
            val sets = (group.sets as? SetCount.Fixed)?.count
            if (total == null || sets == null) null else total + sets
        }

    /** Sets decided on while training, ended with "Finish exercise": "x" or an empty sets cell. */
    val isOpenSets: Boolean get() = setGroups.any { it.sets == SetCount.Open || it.sets == SetCount.Unlimited }

    /** Open sets from an empty sets cell: no time limit, and the user picks each rest. */
    val isUnlimited: Boolean get() = setGroups.any { it.sets == SetCount.Unlimited }

    /** The user picks each rest: unlimited sets, or no row of the sheet gives one (see [withSharedRests]). */
    val picksRest: Boolean get() = isUnlimited || setGroups.all { it.restSeconds == null }

    /** The unit its sets are counted in: its working (last) row's. */
    val repsUnit: RepsUnit get() = setGroups.lastOrNull()?.reps?.unit ?: RepsUnit.Reps

    /** The unit of the row with id [setGroupId], or the exercise's own when it isn't one of them. */
    fun repsUnitOf(setGroupId: String): RepsUnit = setGroups.firstOrNull { it.id == setGroupId }?.reps?.unit ?: repsUnit
}

/** "5" or "5a": [index] 5 and [member] null or 'a'. */
data class ExerciseNumber(val index: Int, val member: Char?) {
    override fun toString(): String = member?.let { "$index$it" } ?: "$index"
}

/** One sheet row: a group of identical sets. */
data class SetGroup(
    val id: String,
    /** 1-based row in the sheet; the row whose WYNIK cell holds [result]. */
    val sheetRow: Int,
    val sets: SetCount,
    val reps: Reps,
    val load: Load,
    val tempo: TempoPattern?,
    /** null when no row of the exercise gives a rest (see [withSharedRests]): the user picks it. */
    val restSeconds: Int?,
    /** Free text from the WYNIK column. */
    val result: String?,
    /** Text in the name column of a continuation row, e.g. "PRZYSIAD(ZNAJDŹ CIĘŻKĄ PIĄTKĘ)". */
    val note: String? = null,
)

sealed interface SetCount {
    data class Fixed(val count: Int) : SetCount

    /** "x": as many sets as fit, often against a time limit. */
    data object Open : SetCount

    /** Empty: as many sets as the user wants, with no time limit and rests they pick. */
    data object Unlimited : SetCount

    data class Unknown(val raw: String) : SetCount
}

sealed interface Reps {
    data class Count(val count: Int) : Reps
    data class Range(val min: Int, val max: Int) : Reps
    /** Held for a time, e.g. "25sek" or "8-15sek". */
    data class Duration(val minSeconds: Int, val maxSeconds: Int) : Reps
    /** "10/str". */
    data class PerSide(val count: Int) : Reps
    /** "50 kroków". */
    data class Steps(val count: Int) : Reps
    /** "2długości". */
    data class Lengths(val count: Int) : Reps
    /** "<12". */
    data class UpTo(val count: Int) : Reps
    data class Other(val raw: String) : Reps
}

/** What a set's count measures: the number logged for it is in this unit. */
enum class RepsUnit { Reps, RepsPerSide, Seconds, Steps, Lengths }

val Reps.unit: RepsUnit
    get() = when (this) {
        is Reps.Duration -> RepsUnit.Seconds
        is Reps.PerSide -> RepsUnit.RepsPerSide
        is Reps.Steps -> RepsUnit.Steps
        is Reps.Lengths -> RepsUnit.Lengths
        is Reps.Count, is Reps.Range, is Reps.UpTo, is Reps.Other -> RepsUnit.Reps
    }

/** Weights are in kg; a bare number in the sheet means kg. */
sealed interface Load {
    data class Weight(val kg: Double) : Load
    /** "2x 20kg": two implements, e.g. dumbbells. */
    data class Implements(val count: Int, val kgEach: Double) : Load
    /** "12.5-15kg". */
    data class Range(val minKg: Double, val maxKg: Double) : Load
    /** "85/87.5/90": pick one per set. */
    data class Choice(val kg: List<Double>) : Load
    /** Target effort instead of a weight, e.g. "RPE 8.5". */
    data class Rpe(val value: Double) : Load
    /** "-" or empty. */
    data object Bodyweight : Load
    data class Other(val raw: String) : Load
}

/** Four phases (lower, pause, lift, pause); 'X' is explosive and '-' is unspecified. */
data class TempoPattern(val phases: String)

/**
 * Rows of one exercise with no rest of their own (e.g. ramp-up singles) still have one:
 * they take the longest rest given on the exercise's other rows.
 */
fun List<SetGroup>.withSharedRests(): List<SetGroup> {
    val shared = mapNotNull { it.restSeconds }.maxOrNull() ?: return this
    return map { if (it.restSeconds == null) it.copy(restSeconds = shared) else it }
}

/** The week containing [date], if the plan has one. */
fun List<TrainingMonth>.weekOf(date: LocalDate): TrainingWeek? =
    asSequence().flatMap { it.weeks }.firstOrNull { date in it }
