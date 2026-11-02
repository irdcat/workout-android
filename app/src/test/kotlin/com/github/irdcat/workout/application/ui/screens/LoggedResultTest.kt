package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.SetGroup
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.loggedSet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LoggedResultTest {

    private val at = Instant.parse("2026-06-01T10:00:00Z")

    private fun Exercise.result(vararg sets: Triple<SetGroup, Double?, Int?>, rpe: String = "", target: String? = null) =
        loggedResult(this, sets.map { (group, kg, reps) -> loggedSet(id, group.id, kg, reps, at) }, rpe, target)

    private fun SetGroup.did(kg: Double?, reps: Int?) = Triple(this, kg, reps)

    @Test
    fun aWeightedExerciseAsPlannedIsJustItsRpe() {
        val row = group(sets = 3, reps = Reps.Count(5), load = Load.Weight(100.0))
        val squat = exercise("squat", "1", "Przysiad", row)
        assertEquals("RPE 8", squat.result(row.did(100.0, 5), row.did(100.0, 5), row.did(100.0, 5), rpe = "8"))
    }

    @Test
    fun onlyWhatDifferedIsTold() {
        val row = group(sets = 3, reps = Reps.Count(5), load = Load.Weight(100.0))
        val squat = exercise("squat", "1", "Przysiad", row)
        // Fewer reps on one set: the reps of every set, so it's clear which.
        assertEquals("5, 5, 3 reps, RPE 9", squat.result(row.did(100.0, 5), row.did(100.0, 5), row.did(100.0, 3), rpe = "9"))
        // Variance in weight.
        assertEquals("100, 100, 105 kg, RPE 9", squat.result(row.did(100.0, 5), row.did(100.0, 5), row.did(105.0, 5), rpe = "9"))
        // The same other weight on every set: told once.
        assertEquals("105 kg, RPE 9", squat.result(row.did(105.0, 5), row.did(105.0, 5), row.did(105.0, 5), rpe = "9"))
        // Both.
        assertEquals("100×5, 105×4, 105×3, RPE 9", squat.result(row.did(100.0, 5), row.did(105.0, 4), row.did(105.0, 3), rpe = "9"))
    }

    @Test
    fun anRpeExerciseAlwaysTellsItsWeight() {
        val row = group(sets = 3, reps = Reps.Count(5), load = Load.Rpe(8.0))
        val bench = exercise("bench", "2", "Bench Press", row)
        // The RPE as planned is left out.
        assertEquals("80 kg", bench.result(row.did(80.0, 5), row.did(80.0, 5), row.did(80.0, 5), rpe = "8", target = "8"))
        assertEquals("80, 82.5, 85 kg, RPE 9", bench.result(row.did(80.0, 5), row.did(82.5, 5), row.did(85.0, 5), rpe = "9", target = "8"))
        assertEquals("80×5, 80×5, 80×4, RPE 9", bench.result(row.did(80.0, 5), row.did(80.0, 5), row.did(80.0, 4), rpe = "9", target = "8"))
    }

    @Test
    fun aDifferentNumberOfSetsListsThem() {
        val row = group(sets = 3, reps = Reps.Count(5), load = Load.Weight(100.0))
        val squat = exercise("squat", "1", "Przysiad", row)
        assertEquals("5, 5 reps, RPE 8", squat.result(row.did(100.0, 5), row.did(100.0, 5), rpe = "8"))
        val asPlanned = row.did(100.0, 5)
        assertEquals("5, 5, 5, 5 reps, RPE 8", squat.result(asPlanned, asPlanned, asPlanned, asPlanned, rpe = "8"))
    }

    @Test
    fun aRepRangeTellsTheReps() {
        val row = group(sets = 3, reps = Reps.Range(8, 12), load = Load.Weight(20.0))
        val curl = exercise("curl", "3", "Biceps", row)
        assertEquals("10 reps, RPE 8", curl.result(row.did(20.0, 10), row.did(20.0, 10), row.did(20.0, 10), rpe = "8"))
        assertEquals("12, 10, 9 reps, RPE 8", curl.result(row.did(20.0, 12), row.did(20.0, 10), row.did(20.0, 9), rpe = "8"))
    }

    @Test
    fun rowsAreToldApartAndUntouchedOnesLeftOut() {
        val single = group(sets = 1, reps = Reps.Count(1), load = Load.Rpe(8.0))
        val backOff = group(sets = 3, reps = Reps.Count(5), load = Load.Weight(120.0))
        val squat = exercise("squat", "1", "Przysiad", single, backOff)
        val top = single.did(150.0, 1)
        val five = backOff.did(120.0, 5)
        assertEquals("150 kg, RPE 8", squat.result(top, five, five, five, rpe = "8"))
        assertEquals("150 kg; 5, 5, 4 reps, RPE 8", squat.result(top, five, five, backOff.did(120.0, 4), rpe = "8"))
        // Ended before the back-off sets.
        assertEquals("150 kg; skipped, RPE 8", squat.result(single.did(150.0, 1), rpe = "8"))
    }

    @Test
    fun bodyweightAndOpenSets() {
        val pullUps = group(sets = 3, reps = Reps.Count(8), load = Load.Bodyweight)
        val pull = exercise("pull", "4", "Podciąganie", pullUps)
        assertEquals("RPE 8", pull.result(pullUps.did(null, 8), pullUps.did(null, 8), pullUps.did(null, 8), rpe = "8"))
        assertEquals("BW, BW, 5 kg, RPE 8", pull.result(pullUps.did(null, 8), pullUps.did(null, 8), pullUps.did(5.0, 8), rpe = "8"))

        val open = group(sets = null, reps = Reps.Count(5), load = Load.Weight(100.0))
        val pyramid = exercise("pyramid", "5", "Przysiad piramidka", open)
        val five = open.did(100.0, 5)
        assertEquals("5, 5, 5, 5 reps, RPE 9", pyramid.result(five, five, five, five, rpe = "9"))
    }

    @Test
    fun withNothingToTellAndNoRpeEverySetIsWritten() {
        val row = group(sets = 2, reps = Reps.Count(5), load = Load.Weight(100.0))
        val squat = exercise("squat", "1", "Przysiad", row)
        assertEquals("100×5, 100×5", squat.result(row.did(100.0, 5), row.did(100.0, 5)))
    }
}
