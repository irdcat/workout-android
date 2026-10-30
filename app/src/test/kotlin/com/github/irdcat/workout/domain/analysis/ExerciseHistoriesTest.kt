package com.github.irdcat.workout.domain.analysis

import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.RepsUnit
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExerciseHistoriesTest {

    // The squat two weeks running, named in Polish then English; bench by RPE; two exercises that aren't lifts.
    private val squatMay = exercise(
        "may-squat", "1", "Przysiad",
        group(sets = 1, reps = Reps.Count(6), load = Load.Weight(60.0)),
        group(sets = 2, reps = Reps.Count(5), load = Load.Weight(90.0)),
    )
    private val squatJune = exercise("june-squat", "1", "SQUAT ", group(sets = 3, reps = Reps.Count(5), load = Load.Weight(92.5)))
    private val bench = exercise("june-bench", "2", "Bench Press", group(sets = 3, reps = Reps.Count(8), load = Load.Rpe(8.5)))
    private val frontSquat = exercise("june-front", "3", "Front Squat", group(sets = 3, reps = Reps.Count(5), load = Load.Weight(60.0)))
    private val plank = exercise("june-plank", "4", "Deska", group(sets = 3, reps = Reps.Duration(8, 15), load = Load.Bodyweight))

    private val plan = months(
        week("w1", "2026-05-25", workout("M/T2", "T2", single(squatMay))),
        week("w2", "2026-06-01", workout("J/T2", "T2", single(squatJune), single(bench), single(frontSquat), single(plank))),
    )

    private var id = 0L
    private fun set(exerciseId: String, kg: Double?, reps: Int, rpe: Double? = null, warmUp: Boolean = false, at: Long = id) =
        LoggedSet(++id, exerciseId, "g", kg, reps, rpe, warmUp, Instant.EPOCH.plusSeconds(at))

    @Test
    fun plannedFiguresComeFromTheSheet() {
        val history = ExerciseHistories(plan, emptyList()).history(Lift.Squat)!!

        val may = history.weeks.first()
        assertEquals(LocalDate.parse("2026-05-25"), may.weekStart)
        // 1 × 6 × 60 + 2 × 5 × 90.
        assertEquals(1260.0, may.plannedVolume)
        assertEquals(90.0, may.plannedTop)
        assertNull(may.actualVolume)
        assertEquals(1387.5, history.weeks[1].plannedVolume)
        assertEquals(VolumeUnit.Kilograms, history.volumeUnit)
    }

    @Test
    fun aLiftByAnyOfItsNamesAcrossWeeks() {
        val histories = ExerciseHistories(plan, emptyList())
        val history = histories.history(Lift.Squat)!!

        assertEquals(2, history.weeksPlanned)
        assertEquals(Lift.Squat, history.lift)
        // Only the lifts, in meet order; the front squat and the plank aren't among them.
        assertEquals(listOf(Lift.Squat, Lift.Bench), histories.catalog.map { it.lift })
        assertNull(histories.history(Lift.Deadlift))
    }

    @Test
    fun actualFiguresComeFromTheSetsLoggedThatWeek() {
        val sets = listOf(
            set("june-squat", 20.0, 10, warmUp = true),
            set("june-squat", 92.5, 5, rpe = 8.0),
            set("june-squat", 92.5, 5, rpe = 9.0),
            set("june-squat", 95.0, 3, rpe = 9.5),
        )
        val history = ExerciseHistories(plan, sets).history(Lift.Squat)!!

        val june = history.weeks[1]
        assertEquals(92.5 * 10 + 95 * 3, june.actualVolume!!, 0.001)
        assertEquals(95.0, june.actualTop)
        // (8 + 9 + 9.5) / 3 = 8.83, to the nearest half.
        assertEquals(9.0, june.actualRpe)
        assertEquals(1, history.weeksLogged)
        assertEquals(LocalDate.parse("2026-06-01"), history.firstLogged)
        assertNull(history.weeks[0].actualVolume)
    }

    @Test
    fun anRpeTargetIsThePlannedRpe() {
        val history = ExerciseHistories(plan, emptyList()).history(Lift.Bench)!!

        assertEquals(8.5, history.weeks.single().plannedRpe)
        assertTrue(history.weightless)
        assertFalse(history.noRpe)
        assertEquals(VolumeUnit.Count(RepsUnit.Reps), history.volumeUnit)
        assertEquals(24.0, history.weeks.single().plannedVolume)
    }

    @Test
    fun theLastLoggedLiftAndTheCatalog() {
        // The plank comes later, but isn't a lift.
        val sets = listOf(set("june-bench", 60.0, 8, at = 1), set("may-squat", 60.0, 6, at = 2), set("june-plank", null, 40, at = 3))
        val histories = ExerciseHistories(plan, sets)

        assertEquals(Lift.Squat, histories.lastLogged)
        assertEquals(listOf(LiftSummary(Lift.Squat, 2, 1), LiftSummary(Lift.Bench, 1, 1)), histories.catalog)
    }

    @Test
    fun setsOfAnExerciseNoLongerInThePlanAreLeftOut() {
        val histories = ExerciseHistories(plan, listOf(set("gone", 50.0, 5)))
        assertNull(histories.lastLogged)
        assertTrue(histories.catalog.all { it.weeksLogged == 0 })
    }
}
