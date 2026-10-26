package com.github.irdcat.workout.domain.results

import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SavedResultTest {

    private val squat = exercise("squat", "1", "Przysiad", group(), group(result = "RPE 8"))
    private val t1 = workout("W/1/T1", "T1", single(squat))

    private fun saved(text: String, groupIndex: Int = 0, name: String = "Przysiad", at: Long = 0) =
        SavedResult(ResultTarget("W/1/T1", "1", groupIndex, name), text, Instant.ofEpochSecond(at))

    private fun Workout.results() = exercises.single().setGroups.map { it.result }

    @Test
    fun aSavedResultReplacesTheSheetsText() {
        assertEquals(listOf("RPE 9", "RPE 8"), t1.withSavedResults(listOf(saved("RPE 9"))).results())
        assertEquals(listOf(null, "RPE 9.5"), t1.withSavedResults(listOf(saved("RPE 9.5", groupIndex = 1))).results())
    }

    @Test
    fun anEmptyResultClearsTheCell() {
        assertEquals(listOf(null, null), t1.withSavedResults(listOf(saved(" ", groupIndex = 1))).results())
    }

    @Test
    fun theTargetMustStillBeTheSameExercise() {
        // Renamed (or another exercise took its number): leave the sheet's text alone.
        assertEquals(t1, t1.withSavedResults(listOf(saved("RPE 9", name = "Bench Press"))))
        assertEquals(t1, t1.withSavedResults(listOf(saved("RPE 9", groupIndex = 5))))
        assertEquals(t1, t1.withSavedResults(listOf(saved("RPE 9").let { it.copy(target = it.target.copy(workoutId = "W/2/T1")) })))
        // Names match regardless of case and spacing at the ends.
        assertEquals(listOf("RPE 9", "RPE 8"), t1.withSavedResults(listOf(saved("RPE 9", name = " przysiad"))).results())
    }

    @Test
    fun theLatestSaveWins() {
        val results = t1.withSavedResults(listOf(saved("RPE 8", at = 1), saved("RPE 9", at = 2))).results()
        assertEquals("RPE 9", results.first())
    }

    @Test
    fun targetsNameTheExerciseAndRow() {
        val target = t1.resultTarget(squat, squat.setGroups[1])
        assertEquals(ResultTarget("W/1/T1", "1", 1, "Przysiad"), target)
        assertNull(t1.withSavedResults(emptyList()).exercises.single().setGroups.first().result)
    }
}
