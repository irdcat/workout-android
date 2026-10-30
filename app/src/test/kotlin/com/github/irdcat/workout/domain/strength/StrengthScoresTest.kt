package com.github.irdcat.workout.domain.strength

import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthScoresTest {

    // Reference values from the published coefficient tables.

    @Test
    fun dots() {
        assertEquals(452.62, StrengthScores.dots(Sex.Male, 90.0, 700.0), 0.01)
        assertEquals(369.31, StrengthScores.dots(Sex.Male, 100.0, 600.0), 0.01)
        assertEquals(443.42, StrengthScores.dots(Sex.Female, 60.0, 400.0), 0.01)
    }

    @Test
    fun wilks() {
        assertEquals(446.88, StrengthScores.wilks(Sex.Male, 90.0, 700.0), 0.01)
        assertEquals(445.95, StrengthScores.wilks(Sex.Female, 60.0, 400.0), 0.01)
    }

    @Test
    fun wilks2020() {
        assertEquals(536.90, StrengthScores.wilks2020(Sex.Male, 90.0, 700.0), 0.01)
        assertEquals(583.49, StrengthScores.wilks2020(Sex.Male, 100.0, 800.0), 0.01)
        assertEquals(527.61, StrengthScores.wilks2020(Sex.Female, 60.0, 400.0), 0.01)
    }

    @Test
    fun bodyweightsOutsideAFormulasRangeUseItsEnds() {
        assertEquals(StrengthScores.dots(Sex.Female, 150.0, 400.0), StrengthScores.dots(Sex.Female, 170.0, 400.0), 0.0)
        assertEquals(StrengthScores.wilks(Sex.Male, 40.0, 300.0), StrengthScores.wilks(Sex.Male, 30.0, 300.0), 0.0)
        assertEquals(StrengthScores.wilks2020(Sex.Male, 200.95, 900.0), StrengthScores.wilks2020(Sex.Male, 230.0, 900.0), 0.0)
    }
}
