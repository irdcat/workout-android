package com.github.irdcat.workout.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGroupTest {

    private fun groupOf(name: String) = DefaultExerciseGroups.groupOf(name)?.name

    @Test
    fun theDefaultsKnowEachLiftByItsEnglishAndPolishNames() {
        mapOf(
            "Squat" to listOf("Squat", "SQUAT", "Przysiad", "przysiad  ", "Back Squat"),
            "Bench" to listOf("Bench Press", "BENCH PRESS", "Wyciskanie leżąc", "Wyciskanie sztangi leżąc"),
            "Deadlift" to listOf("Deadlift", "DEADLIFT", "Martwy ciąg", "martwy ciag", "Sumo Deadlift"),
        ).forEach { (group, names) -> names.forEach { assertEquals(it, group, groupOf(it)) } }
    }

    @Test
    fun accentsNotesInBracketsAndATrailingDotDoNotMatter() {
        assertEquals("Squat", groupOf("PRZYSIAD(ZNAJDŹ CIĘŻKĄ PIĄTKĘ)"))
        assertEquals("Bench", groupOf("Bench Press."))
        assertEquals("Bench", groupOf("Wyciskanie na lawce"))
        assertEquals(exerciseKey("Łańcuchy"), exerciseKey("lancuchy"))
    }

    @Test
    fun variationsAreOtherExercises() {
        listOf(
            "Front Squat", "Przysiad bułgarski", "Przysiad w Tempo", "PRZYSIAD ŁAŃCUCHY(duże)", "Close Grip Bench Press",
            "Bench Press Tech.", "Negative Bench Press", "Decifyt Deadlift", "Stiff Legs RDL", "Rack Pull spod kolan",
        ).forEach { assertNull(it, groupOf(it)) }
    }

    @Test
    fun theFirstGroupListingAnExerciseHasIt() {
        val groups = listOf(ExerciseGroup("A", listOf("OHP")), ExerciseGroup("B", listOf("ohp")))
        assertEquals("A", groups.groupOf("OHP")?.name)
    }

    @Test
    fun aGroupNameMustBeNewAndNotBlank() {
        val taken = listOf("Squat", "Bench")
        assertTrue(isFreeGroupName(taken, "OHP"))
        assertFalse(isFreeGroupName(taken, "  "))
        assertFalse(isFreeGroupName(taken, " squat "))
        // Renaming a group to itself, re-cased.
        assertTrue(isFreeGroupName(taken, "SQUAT", renaming = "Squat"))
    }
}
