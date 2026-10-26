package com.github.irdcat.workout.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/** Every format listed on the design's "What the sheet contains" board. */
class SheetNotationTest {

    @Test
    fun sets() {
        assertEquals(SetCount.Fixed(5), SheetNotation.sets("5"))
        assertEquals(SetCount.Open, SheetNotation.sets("x"))
        assertEquals(SetCount.Unlimited, SheetNotation.sets(""))
        assertEquals(SetCount.Unlimited, SheetNotation.sets("  "))
        assertEquals(SetCount.Unknown("?"), SheetNotation.sets("?"))
    }

    @Test
    fun reps() {
        assertEquals(Reps.Count(5), SheetNotation.reps("5"))
        assertEquals(Reps.Range(8, 10), SheetNotation.reps("8-10"))
        assertEquals(Reps.Duration(25, 25), SheetNotation.reps("25sek"))
        assertEquals(Reps.Duration(8, 15), SheetNotation.reps("8-15sek"))
        assertEquals(Reps.PerSide(10), SheetNotation.reps("10/str"))
        assertEquals(Reps.Steps(50), SheetNotation.reps("50 kroków"))
        assertEquals(Reps.Lengths(2), SheetNotation.reps("2długości"))
        assertEquals(Reps.UpTo(12), SheetNotation.reps("<12"))
        assertEquals(Reps.Other("max"), SheetNotation.reps("max"))
    }

    @Test
    fun load() {
        assertEquals(Load.Weight(85.0), SheetNotation.load("85"))
        assertEquals(Load.Weight(87.5), SheetNotation.load("87.5kg"))
        assertEquals(Load.Weight(105.0), SheetNotation.load("105KG"))
        assertEquals(Load.Weight(12.5), SheetNotation.load("12,5kg"))
        assertEquals(Load.Rpe(8.5), SheetNotation.load("RPE 8.5"))
        assertEquals(Load.Implements(2, 20.0), SheetNotation.load("2x 20kg"))
        assertEquals(Load.Bodyweight, SheetNotation.load("-"))
        assertEquals(Load.Bodyweight, SheetNotation.load(""))
        assertEquals(Load.Range(12.5, 15.0), SheetNotation.load("12.5-15kg"))
        assertEquals(Load.Range(20.0, 22.0), SheetNotation.load("20kg-22kg"))
        assertEquals(Load.Choice(listOf(85.0, 87.5, 90.0)), SheetNotation.load("85/87.5/90"))
        assertEquals(Load.Choice(listOf(14.0, 15.0)), SheetNotation.load("14kg/15kg"))
    }

    @Test
    fun tempo() {
        assertEquals(TempoPattern("3010"), SheetNotation.tempo("3010"))
        assertEquals(TempoPattern("20X1"), SheetNotation.tempo("20x1"))
        assertEquals(TempoPattern("52--"), SheetNotation.tempo("52--"))
        assertNull(SheetNotation.tempo("-"))
        assertNull(SheetNotation.tempo(""))
    }

    @Test
    fun rest() {
        assertEquals(150, SheetNotation.restSeconds("150sek"))
        assertEquals(45, SheetNotation.restSeconds("45"))
        assertEquals(90, SheetNotation.restSeconds("1.5min"))
        assertNull(SheetNotation.restSeconds("-"))
        assertNull(SheetNotation.restSeconds(""))
    }

    @Test
    fun exerciseNumber() {
        assertEquals(ExerciseNumber(5, 'a'), SheetNotation.exerciseNumber("5a"))
        assertEquals(ExerciseNumber(3, null), SheetNotation.exerciseNumber("3"))
        assertNull(SheetNotation.exerciseNumber("T1"))
    }

    @Test
    fun timeLimit() {
        // As in the test data's open-sets exercise.
        assertEquals(
            Duration.ofMinutes(10),
            SheetNotation.timeLimit("Przysiad Piramidka ciężaru 15/15/10/10 Dalej 5-2,5 - Czas: 10:00 od pierwszej serii"),
        )
        assertEquals(Duration.ofSeconds(9 * 60 + 20), SheetNotation.timeLimit("czas 9:20"))
        assertEquals(Duration.ofMinutes(12), SheetNotation.timeLimit("Czas 12 min"))
        assertNull(SheetNotation.timeLimit("Przysiad"))
        // Only after the word "czas": a weight or a tempo isn't a time.
        assertNull(SheetNotation.timeLimit("Bench 92.5 kg 3:1"))
    }
}
