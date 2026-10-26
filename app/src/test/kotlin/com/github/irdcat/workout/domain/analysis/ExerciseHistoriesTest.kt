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

    // RDL two weeks running, spelt a little differently; a plank hold once.
    private val rdlMay = exercise(
        "may-rdl", "1", "RDL",
        group(sets = 1, reps = Reps.Count(6), load = Load.Weight(60.0)),
        group(sets = 2, reps = Reps.Count(5), load = Load.Weight(90.0)),
    )
    private val rdlJune = exercise("june-rdl", "1", "rdl ", group(sets = 3, reps = Reps.Count(5), load = Load.Weight(92.5)))
    private val zurawie = exercise("june-z", "2", "Żurawie", group(sets = 3, reps = Reps.Count(8), load = Load.Rpe(8.5)))
    private val plank = exercise("june-plank", "3", "Deska", group(sets = 3, reps = Reps.Duration(8, 15), load = Load.Bodyweight))

    private val plan = months(
        week("w1", "2026-05-25", workout("M/T2", "T2", single(rdlMay))),
        week("w2", "2026-06-01", workout("J/T2", "T2", single(rdlJune), single(zurawie), single(plank))),
    )

    private var id = 0L
    private fun set(exerciseId: String, kg: Double?, reps: Int, rpe: Double? = null, warmUp: Boolean = false, at: Long = id) =
        LoggedSet(++id, exerciseId, "g", kg, reps, rpe, warmUp, Instant.EPOCH.plusSeconds(at))

    @Test
    fun plannedFiguresComeFromTheSheet() {
        val history = ExerciseHistories(plan, emptyList()).history("rdl")!!

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
    fun theSameExerciseByNameAcrossWeeks() {
        val histories = ExerciseHistories(plan, emptyList())
        val history = histories.history("rdl")!!

        assertEquals(2, history.weeksPlanned)
        // The latest spelling.
        assertEquals("rdl ", history.name)
        // Two weeks planned first, then by name.
        assertEquals(listOf("rdl", "deska", "żurawie"), histories.catalog.map { it.key })
    }

    @Test
    fun actualFiguresComeFromTheSetsLoggedThatWeek() {
        val sets = listOf(
            set("june-rdl", 20.0, 10, warmUp = true),
            set("june-rdl", 92.5, 5, rpe = 8.0),
            set("june-rdl", 92.5, 5, rpe = 9.0),
            set("june-rdl", 95.0, 3, rpe = 9.5),
        )
        val history = ExerciseHistories(plan, sets).history("rdl")!!

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
        val history = ExerciseHistories(plan, emptyList()).history("żurawie")!!

        assertEquals(8.5, history.weeks.single().plannedRpe)
        assertTrue(history.weightless)
        assertFalse(history.noRpe)
        assertEquals(VolumeUnit.Count(RepsUnit.Reps), history.volumeUnit)
        assertEquals(24.0, history.weeks.single().plannedVolume)
    }

    @Test
    fun aHoldCountsSecondsAtTheTopOfItsRange() {
        val history = ExerciseHistories(plan, listOf(set("june-plank", null, 40))).history("deska")!!

        assertEquals(VolumeUnit.Count(RepsUnit.Seconds), history.volumeUnit)
        assertEquals(45.0, history.weeks.single().plannedVolume)
        assertEquals(40.0, history.weeks.single().actualVolume)
        assertEquals("8–15 s", history.estimatedRange)
        assertTrue(history.weightless)
        assertTrue(history.noRpe)
    }

    @Test
    fun theLastLoggedExerciseAndTheCatalogOrder() {
        val sets = listOf(set("june-plank", null, 40, at = 1), set("may-rdl", 60.0, 6, at = 2))
        val histories = ExerciseHistories(plan, sets)

        assertEquals("rdl", histories.lastLogged)
        // Most trained first: logged weeks, then planned weeks.
        assertEquals(listOf("rdl", "deska", "żurawie"), histories.catalog.map { it.key })
        assertEquals(ExerciseSummary("rdl", "rdl ", 2, 1), histories.catalog.first())
    }

    @Test
    fun setsOfAnExerciseNoLongerInThePlanAreLeftOut() {
        val histories = ExerciseHistories(plan, listOf(set("gone", 50.0, 5)))
        assertNull(histories.lastLogged)
        assertNull(histories.history("gone"))
    }
}
