package com.github.irdcat.workout.domain.workout

import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRestsTest {

    @Test
    fun rowsWithoutARestTakeTheLongestOfTheExercise() {
        val groups = listOf(group(rest = null), group(rest = 120), group(rest = null), group(rest = 180)).withSharedRests()

        assertEquals(listOf(180, 120, 180, 180), groups.map { it.restSeconds })
    }

    @Test
    fun withNoRestAnywhereTheUserPicksIt() {
        val groups = listOf(group(rest = null), group(rest = null))

        assertEquals(groups, groups.withSharedRests())
        assertTrue(exercise("e", "1", "Przysiad", *groups.toTypedArray()).picksRest)
        assertFalse(exercise("e", "1", "Przysiad", group(rest = null), group(rest = 90)).picksRest)
    }
}
