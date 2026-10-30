package com.github.irdcat.workout.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiftTest {

    @Test
    fun eachLiftByItsEnglishAndPolishNames() {
        mapOf(
            Lift.Squat to listOf("Squat", "SQUAT", "Przysiad", "przysiad  ", "Back Squat"),
            Lift.Bench to listOf("Bench Press", "BENCH PRESS", "Wyciskanie leżąc", "Wyciskanie sztangi leżąc"),
            Lift.Deadlift to listOf("Deadlift", "DEADLIFT", "Martwy ciąg", "martwy ciag", "Sumo Deadlift"),
        ).forEach { (lift, names) -> names.forEach { assertEquals(it, lift, Lift.of(it)) } }
    }

    @Test
    fun notesInBracketsAndATrailingDotDoNotMatter() {
        assertEquals(Lift.Squat, Lift.of("PRZYSIAD(ZNAJDŹ CIĘŻKĄ PIĄTKĘ)"))
        assertEquals(Lift.Bench, Lift.of("Bench Press."))
    }

    @Test
    fun variationsAreOtherExercises() {
        listOf(
            "Front Squat", "Przysiad bułgarski", "Przysiad w Tempo", "PRZYSIAD ŁAŃCUCHY(duże)", "Close Grip Bench Press",
            "Bench Press Tech.", "Negative Bench Press", "Decifyt Deadlift", "Stiff Legs RDL", "Rack Pull spod kolan",
        ).forEach { assertNull(it, Lift.of(it)) }
    }
}
