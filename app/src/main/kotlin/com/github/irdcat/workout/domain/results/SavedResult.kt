package com.github.irdcat.workout.domain.results

import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.SetGroup
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutItem
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The WYNIK cell a result belongs to, named by what it is rather than by sheet row:
 * rows move when lines are inserted above, but a workout keeps its ID and an exercise
 * its number and rows. The row is looked up again from the latest sync when writing.
 */
data class ResultTarget(
    val workoutId: String,
    /** e.g. "5a". */
    val exerciseNumber: String,
    /** Index into [Exercise.setGroups]: which of the exercise's rows. */
    val groupIndex: Int,
    /** Checked when applying the result, so a renumbered exercise never gets another's result. */
    val exerciseName: String,
)

/** A result entered in the app that the sheet doesn't have yet (or never gets, with writing off). */
data class SavedResult(
    val target: ResultTarget,
    /** Empty clears the cell. */
    val text: String,
    val savedAt: Instant,
)

sealed interface WriteResultsOutcome {
    data class Written(val count: Int) : WriteResultsOutcome

    /** Nothing waiting, or writing to the sheet is turned off. */
    data object NothingToWrite : WriteResultsOutcome

    /** The sheet couldn't be written; everything stays queued for the next try. */
    data class Failed(val cause: Throwable) : WriteResultsOutcome
}

/**
 * Results kept on the phone until they're in the sheet. The plan shows them in place
 * of the sheet's own text straight away; once written, the sheet's text is the result.
 */
interface ResultRepository {

    /** Results not in the sheet yet, oldest first. */
    fun observeSaved(): Flow<List<SavedResult>>

    /** Saves or replaces the result for [SavedResult.target]. */
    suspend fun save(result: SavedResult)

    /** Drops the results for [workoutId] saved at or after [since] that aren't in the sheet yet. */
    suspend fun discardSavedSince(workoutId: String, since: Instant)

    /**
     * Writes every saved result to its WYNIK cell, using rows from the cached plan, so
     * call it right after a sync. Results whose exercise can't be found stay saved.
     */
    suspend fun writeSaved(): WriteResultsOutcome
}

fun Workout.resultTarget(exercise: Exercise, group: SetGroup): ResultTarget = ResultTarget(
    workoutId = id,
    exerciseNumber = exercise.number.toString(),
    groupIndex = exercise.setGroups.indexOf(group).coerceAtLeast(0),
    exerciseName = exercise.name,
)

/** Whether [target] names this exercise of this workout. */
fun ResultTarget.matches(workout: Workout, exercise: Exercise): Boolean =
    workoutId == workout.id &&
        exerciseNumber == exercise.number.toString() &&
        exerciseName.trim().equals(exercise.name.trim(), ignoreCase = true) &&
        groupIndex in exercise.setGroups.indices

/** The workout with saved results in place of the sheet's text; an empty one clears it. */
fun Workout.withSavedResults(saved: List<SavedResult>): Workout {
    val mine = saved.filter { it.target.workoutId == id }
    if (mine.isEmpty()) return this
    return copy(
        items = items.map { item ->
            item.mapExercises { exercise ->
                val results = mine.filter { it.target.matches(this, exercise) }
                if (results.isEmpty()) {
                    exercise
                } else {
                    exercise.copy(
                        setGroups = exercise.setGroups.mapIndexed { index, group ->
                            // The latest save wins if a cell was saved more than once.
                            val result = results.lastOrNull { it.target.groupIndex == index }
                            if (result == null) group else group.copy(result = result.text.trim().ifEmpty { null })
                        },
                    )
                }
            }
        },
    )
}

private fun WorkoutItem.mapExercises(transform: (Exercise) -> Exercise): WorkoutItem = when (this) {
    is WorkoutItem.Single -> copy(exercise = transform(exercise))
    is WorkoutItem.Superset -> copy(exercises = exercises.map(transform))
}
