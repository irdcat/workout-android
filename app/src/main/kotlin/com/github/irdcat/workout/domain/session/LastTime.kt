package com.github.irdcat.workout.domain.session

import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The previous time a workout was on the plan, with whatever the app logged then.
 * The sheet keeps only results, so anything else (sets, dates, durations) is known
 * only for sessions trained in the app: [session] is null otherwise.
 */
data class LastTime(
    val week: TrainingWeek,
    val workout: Workout,
    val session: PastSession?,
) {
    /** The same exercise then, matched by name: numbers shift as a plan changes. */
    fun exerciseLike(exercise: Exercise): Exercise? {
        val name = exercise.name.normalized()
        return workout.exercises.firstOrNull { it.name.normalized() == name }
    }

    /** Sets logged for the same exercise then; empty when the app has none. */
    fun setsLike(exercise: Exercise): List<LoggedSet> {
        val then = exerciseLike(exercise) ?: return emptyList()
        return session?.log?.setsOf(then.id).orEmpty().filterNot { it.warmUp }
    }

    /** Warm-up sets logged for the same exercise then; the sheet never has them. */
    fun warmUpsLike(exercise: Exercise): List<LoggedSet> {
        val then = exerciseLike(exercise) ?: return emptyList()
        return session?.log?.setsOf(then.id).orEmpty().filter { it.warmUp }
    }

    /** The sheet's result for the same exercise then, all rows together. */
    fun resultLike(exercise: Exercise): String? =
        exerciseLike(exercise)?.setGroups?.mapNotNull { it.result }?.joinToString(" · ")?.ifEmpty { null }

    private fun String.normalized() = trim().lowercase().replace(Regex("""\s+"""), " ")
}

/** Finds [LastTime] for a workout: the latest earlier week holding a workout with the same code. */
class FindLastTime @Inject constructor(
    private val planRepository: WorkoutPlanRepository,
    private val logRepository: SessionLogRepository,
) {
    suspend operator fun invoke(workoutId: String): LastTime? {
        val months = planRepository.observeMonths().first()
        val (week, workout) = months.previousOccurrenceOf(workoutId) ?: return null
        return LastTime(week, workout, logRepository.latestSessionOf(workout.id))
    }
}

/** The latest week before [workoutId]'s week with a workout of the same code, and that workout. */
fun List<TrainingMonth>.previousOccurrenceOf(workoutId: String): Pair<TrainingWeek, Workout>? {
    val weeks = flatMap { it.weeks }.sortedBy { it.start }
    val index = weeks.indexOfFirst { week -> week.workouts.any { it.id == workoutId } }
    if (index < 0) return null
    val code = weeks[index].workouts.first { it.id == workoutId }.code
    return weeks.subList(0, index).asReversed().firstNotNullOfOrNull { week ->
        week.workouts.firstOrNull { it.code.equals(code, ignoreCase = true) }?.let { week to it }
    }
}
