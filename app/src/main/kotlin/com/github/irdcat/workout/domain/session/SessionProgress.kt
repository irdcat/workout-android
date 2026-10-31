package com.github.irdcat.workout.domain.session

import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.SetGroup
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutItem

/**
 * Where a session stands, worked out from what's been logged: which exercises
 * are complete, which set comes next, and whose result is waiting to be reviewed.
 *
 * An exercise is finished once its result was reviewed ("Finish exercise" or after
 * its last planned set), or, with nothing logged in this session, when the sheet
 * already has a result for it (done before the app was used). Logging every planned
 * set isn't enough on its own: the result is reviewed first. Warm-up sets are extra:
 * they never count towards the plan.
 */
class SessionProgress(val workout: Workout, val log: SessionLog) {

    /** Working sets logged for [exercise], warm-ups left out. */
    fun loggedSets(exercise: Exercise): List<LoggedSet> =
        log.setsOf(exercise.id).filterNot { it.warmUp }

    fun warmUpSets(exercise: Exercise): List<LoggedSet> =
        log.setsOf(exercise.id).filter { it.warmUp }

    /** One planned set group per set, in order; open-ended groups appear once. */
    fun plannedSets(exercise: Exercise): List<SetGroup> = exercise.setGroups.flatMap { group ->
        List((group.sets as? SetCount.Fixed)?.count ?: 1) { group }
    }

    /** null for open sets, which only end with "Finish exercise". */
    fun plannedCount(exercise: Exercise): Int? = if (exercise.isOpenSets) null else plannedSets(exercise).size

    fun isComplete(exercise: Exercise): Boolean =
        exercise.id in log.finishedExerciseIds || (loggedSets(exercise).isEmpty() && exercise.isDone)

    /** Every planned set is logged, but the result hasn't been reviewed, which finishes it. */
    fun needsReview(exercise: Exercise): Boolean =
        !isComplete(exercise) && plannedCount(exercise)?.let { loggedSets(exercise).size >= it } == true

    /** Nothing left to log: finished, or only its result to review. */
    private fun isSettled(exercise: Exercise): Boolean = isComplete(exercise) || needsReview(exercise)

    /** The first item with an exercise not finished yet, and its position; null once all are. */
    private val current: IndexedValue<WorkoutItem>? =
        workout.items.withIndex().firstOrNull { (_, item) -> item.exercises.any { !isComplete(it) } }

    /** The set to log next; null while a result waits to be reviewed and once every exercise is finished. */
    val next: NextSet? = current
        ?.takeIf { (_, item) -> item.exercises.any { !isSettled(it) } }
        ?.let { (index, item) -> nextIn(index, item) }

    /**
     * An exercise whose result to review before the session goes on: in the current item
     * (each member of a superset, once all of it is logged), with nothing left to log there.
     */
    val toReview: Exercise? = current
        ?.takeIf { next == null }
        ?.value?.exercises?.firstOrNull { needsReview(it) }

    val isWorkoutComplete: Boolean get() = current == null

    /** Superset slots in the order they're done: round by round, member by member. */
    fun slots(superset: WorkoutItem.Superset): List<Slot> = (1..superset.rounds).flatMap { round ->
        superset.exercises
            .filter { (plannedCount(it) ?: 1) >= round }
            .map { Slot(round, it, logged = loggedSets(it).size >= round) }
    }

    /**
     * Which set of its exercise [set] was (1-based), e.g. 3 for the third set logged;
     * warm-ups and working sets are counted apart.
     */
    fun setNumberOf(set: LoggedSet): Int =
        log.setsOf(set.exerciseId).filter { it.warmUp == set.warmUp }.indexOfFirst { it.id == set.id } + 1

    private fun nextIn(itemIndex: Int, item: WorkoutItem): NextSet {
        val (exercise, round) = when (item) {
            is WorkoutItem.Single -> item.exercise to null
            is WorkoutItem.Superset -> {
                val slot = slots(item).firstOrNull { !it.logged && !isSettled(it.exercise) }
                if (slot != null) {
                    slot.exercise to slot.round
                } else {
                    // Only open-ended members are left: carry on with the first of them.
                    val member = item.exercises.first { !isSettled(it) }
                    member to loggedSets(member).size + 1
                }
            }
        }
        val setNumber = loggedSets(exercise).size + 1
        val planned = plannedSets(exercise)
        return NextSet(
            itemIndex = itemIndex,
            exercise = exercise,
            setNumber = setNumber,
            plannedCount = plannedCount(exercise),
            // Past the plan (extra or open sets) repeats the last group.
            group = planned.getOrElse(setNumber - 1) { planned.last() },
            round = round,
        )
    }

    data class Slot(val round: Int, val exercise: Exercise, val logged: Boolean)
}

/** The exercise [set] was logged against; null when it's no longer in the workout. */
fun Workout.exerciseOf(set: LoggedSet): Exercise? = exercises.firstOrNull { it.id == set.exerciseId }

/** The item holding [exercise], and its position. */
fun Workout.itemOf(exercise: Exercise): IndexedValue<WorkoutItem>? =
    items.withIndex().firstOrNull { (_, item) -> exercise in item.exercises }

fun Workout.setGroup(id: String): SetGroup? = exercises.flatMap { it.setGroups }.firstOrNull { it.id == id }

data class NextSet(
    /** Position of the item in [Workout.items]. */
    val itemIndex: Int,
    val exercise: Exercise,
    /** 1-based within [exercise]. */
    val setNumber: Int,
    /** null for open sets. */
    val plannedCount: Int?,
    /** The planned group this set belongs to: its target, tempo and rest. */
    val group: SetGroup,
    /** Superset round (1-based); null outside a superset. */
    val round: Int?,
)
