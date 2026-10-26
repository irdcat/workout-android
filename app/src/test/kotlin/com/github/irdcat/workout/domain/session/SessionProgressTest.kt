package com.github.irdcat.workout.domain.session

import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.WorkoutItem
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.superset
import com.github.irdcat.workout.testing.workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionProgressTest {

    private val squat = exercise(
        "squat", "1", "Przysiad",
        group(load = Load.Weight(60.0)),
        group(sets = 2, load = Load.Weight(90.0)),
    )
    private val bench = exercise("bench", "2", "Bench Press", group(sets = 2))
    private val planned = workout("W/1/T1", "T1", single(squat), single(bench))

    private val a = exercise("a", "3a", "Biceps", group(sets = 3))
    private val b = exercise("b", "3b", "Reverse Flies", group(sets = 2))
    private val c = exercise("c", "3c", "Wycieraczki", group(sets = 2))
    private val withSuperset = workout("W/1/T2", "T2", single(squat), superset(3, a, b, c))

    private var id = 0L
    private fun logged(exercise: Exercise, times: Int = 1) = List(times) {
        LoggedSet(++id, exercise.id, exercise.setGroups.first().id, 60.0, 5, null, false, Instant.EPOCH.plusSeconds(id))
    }

    @Test
    fun walksThroughPlannedSetsInOrder() {
        val start = SessionProgress(planned, SessionLog())
        assertEquals(squat, start.next?.exercise)
        assertEquals(1, start.next?.setNumber)
        assertEquals(3, start.next?.plannedCount)
        assertEquals(squat.setGroups[0], start.next?.group)

        val afterOne = SessionProgress(planned, SessionLog(logged(squat)))
        assertEquals(2, afterOne.next?.setNumber)
        // Set 2 belongs to the second group (2 × 90 kg).
        assertEquals(squat.setGroups[1], afterOne.next?.group)

        // Every set logged: nothing more to log, but the result is reviewed before moving on.
        val squatSets = logged(squat, 3)
        val squatLogged = SessionProgress(planned, SessionLog(squatSets))
        assertFalse(squatLogged.isComplete(squat))
        assertTrue(squatLogged.needsReview(squat))
        assertNull(squatLogged.next)
        assertEquals(squat, squatLogged.toReview)
        assertFalse(squatLogged.isWorkoutComplete)

        val squatReviewed = SessionProgress(planned, SessionLog(squatSets, finishedExerciseIds = setOf("squat")))
        assertTrue(squatReviewed.isComplete(squat))
        assertEquals(bench, squatReviewed.next?.exercise)
        assertNull(squatReviewed.toReview)

        val allDone = SessionProgress(planned, SessionLog(squatSets + logged(bench, 2), finishedExerciseIds = setOf("squat", "bench")))
        assertTrue(allDone.isWorkoutComplete)
        assertNull(allDone.next)
        assertNull(allDone.toReview)
    }

    @Test
    fun aResultInTheSheetCountsUntilTheExerciseIsLoggedInTheApp() {
        val doneInSheet = exercise("done", "1", "Przysiad", group(sets = 3, result = "RPE 9"))
        val workout = workout("W", "T1", single(doneInSheet), single(bench))

        assertEquals(bench, SessionProgress(workout, SessionLog()).next?.exercise)
        // Once sets are logged, the app's count decides.
        val loggedOnce = SessionProgress(workout, SessionLog(logged(doneInSheet)))
        assertFalse(loggedOnce.isComplete(doneInSheet))
        assertEquals(doneInSheet, loggedOnce.next?.exercise)
    }

    @Test
    fun unevenSupersetGoesRoundByRound() {
        val afterSquat = logged(squat, 3)
        fun progress(vararg sets: List<LoggedSet>) =
            SessionProgress(withSuperset, SessionLog(afterSquat + sets.toList().flatten(), finishedExerciseIds = setOf("squat")))
        fun next(vararg sets: List<LoggedSet>) = progress(*sets).next

        assertEquals("a" to 1, next().let { it?.exercise?.id to it?.round })
        assertEquals("c" to 1, next(logged(a), logged(b)).let { it?.exercise?.id to it?.round })
        assertEquals("a" to 2, next(logged(a), logged(b), logged(c)).let { it?.exercise?.id to it?.round })
        // b and c have 2 sets, so round 3 is a only.
        assertEquals("a" to 3, next(logged(a, 2), logged(b, 2), logged(c, 2)).let { it?.exercise?.id to it?.round })
        // b's sets end before a's: no review until the whole superset is logged.
        assertNull(progress(logged(a, 2), logged(b, 2), logged(c, 1)).toReview)
        val allLogged = progress(logged(a, 3), logged(b, 2), logged(c, 2))
        assertNull(allLogged.next)
        assertEquals(a, allLogged.toReview)

        val slots = SessionProgress(withSuperset, SessionLog(afterSquat + logged(a))).slots(withSuperset.items[1] as WorkoutItem.Superset)
        assertEquals(listOf("a1", "b1", "c1", "a2", "b2", "c2", "a3"), slots.map { "${it.exercise.id}${it.round}" })
        assertEquals(listOf(true, false, false, false, false, false, false), slots.map { it.logged })
    }

    @Test
    fun openSetsOnlyEndWhenFinished() {
        val pyramid = exercise("pyramid", "1", "Przysiad piramidka", group(sets = null, rest = null))
        val workout = workout("W", "T3", single(pyramid), single(bench))

        val afterFive = SessionProgress(workout, SessionLog(logged(pyramid, 5)))
        assertNull(afterFive.plannedCount(pyramid))
        assertEquals(pyramid, afterFive.next?.exercise)
        assertEquals(6, afterFive.next?.setNumber)

        val finished = SessionProgress(workout, SessionLog(logged(pyramid, 5), finishedExerciseIds = setOf("pyramid")))
        assertTrue(finished.isComplete(pyramid))
        assertEquals(bench, finished.next?.exercise)
    }

    @Test
    fun finishingEarlySkipsTheRemainingSets() {
        val progress = SessionProgress(planned, SessionLog(logged(squat), finishedExerciseIds = setOf("squat")))
        assertTrue(progress.isComplete(squat))
        assertEquals(bench, progress.next?.exercise)
    }

    @Test
    fun warmUpsDontCountTowardsThePlan() {
        val warmUps = logged(squat, 2).map { it.copy(warmUp = true) }
        val working = logged(squat)
        val progress = SessionProgress(planned, SessionLog(warmUps + working))

        assertEquals(working, progress.loggedSets(squat))
        assertEquals(warmUps, progress.warmUpSets(squat))
        assertEquals(2, progress.next?.setNumber)
        assertEquals(2, progress.setNumberOf(warmUps[1]))
        assertEquals(1, progress.setNumberOf(working.single()))
    }

    @Test
    fun aSupersetWithOnlyOpenSetsLeftCarriesOnWithThem() {
        val fixed = exercise("fixed", "1a", "Dips", group(sets = 2))
        val open = exercise("open", "1b", "Plank", group(sets = null))
        val workout = workout("W/1/T5", "T5", superset(1, fixed, open))

        val progress = SessionProgress(workout, SessionLog(logged(fixed, 2) + logged(open)))

        assertEquals(open, progress.next?.exercise)
        assertEquals(2, progress.next?.round)
        assertEquals(2, progress.next?.setNumber)
    }
}
