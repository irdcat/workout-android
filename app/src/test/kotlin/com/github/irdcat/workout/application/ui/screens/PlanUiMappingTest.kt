package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.RepsUnit
import com.github.irdcat.workout.domain.workout.unit
import com.github.irdcat.workout.domain.workout.weekOf
import com.github.irdcat.workout.infrastructure.google.SheetFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Real weeks from test-data, mapped the way the screens receive them. */
class PlanUiMappingTest {

    private val months = SheetFixtures.months()
    private fun week(date: String) = months.weekOf(LocalDate.parse(date))!!

    @Test
    fun weekRanges() {
        assertEquals("1 – 7 Jun", week("2026-06-01").range)
        assertEquals("29 Jun – 5 Jul", week("2026-06-29").range)
    }

    @Test
    fun homeSummaries() {
        val summaries = week("2026-06-01").workouts.map { it.toSummary() }
        assertEquals(listOf("T1", "T2", "T3"), summaries.map { it.name })
        val t1 = summaries.first()
        assertEquals("Przysiad · Bench Press", t1.title)
        assertEquals(7, t1.exerciseCount)
        assertEquals(1, t1.supersetCount)
        assertEquals(WorkoutStatus.Completed(day = null), t1.status)
    }

    @Test
    fun detailItems() {
        val t2 = week("2026-06-01").workouts.first { it.code == "T2" }.toDetailItems()
        val rdl = (t2[1] as WorkoutItem.Single).exercise
        assertEquals("RDL", rdl.name)
        assertEquals(
            listOf("1×6 60 kg", "1×6 75 kg", "1×5 90 kg", "2×5 92.5 kg"),
            rdl.setGroups.map { "${it.sets}×${it.reps} ${it.load}" },
        )
        assertEquals(Tempo("3010", appliesTo = "last group"), rdl.tempo)
        assertEquals(150, rdl.restSeconds)

        val lever = (t2[0] as WorkoutItem.Single).exercise
        assertEquals("8–15 s", lever.setGroups.single().reps)
        assertEquals("BW", lever.setGroups.single().load)
        assertNull(lever.tempo)

        val superset = t2.last() as WorkoutItem.Superset
        assertEquals(5, superset.index)
        assertEquals(listOf(3, 2, 2), superset.exercises.map { it.setCount })
    }

    @Test
    fun openSetsHaveNoSetCountOrRest() {
        val pyramid = (week("2026-07-27").workouts.first { it.code == "T3" }.toDetailItems().first() as WorkoutItem.Single).exercise
        assertNull(pyramid.setGroups.single().sets)
        assertNull(pyramid.restSeconds)
    }

    @Test
    fun syncedAt() {
        val today = LocalDate.of(2026, 10, 25)
        val utc = ZoneOffset.UTC
        assertEquals("Never", formatSyncedAt(null, today, utc))
        assertEquals("Today 14:32", formatSyncedAt(Instant.parse("2026-10-25T14:32:00Z"), today, utc))
        assertEquals("Yesterday 9:05", formatSyncedAt(Instant.parse("2026-10-24T09:05:00Z"), today, utc))
        assertEquals("12 Oct 14:32", formatSyncedAt(Instant.parse("2026-10-12T14:32:00Z"), today, utc))
    }

    @Test
    fun aLoggedSetAtZeroKgReadsAsBodyweight() {
        fun set(kg: Double?, reps: Int?) = LoggedSet(1, "x", "g", kg, reps, null, false, Instant.EPOCH)

        assertEquals("60×6", set(60.0, 6).chipLabel())
        assertEquals("6 reps", set(0.0, 6).chipLabel())
        assertEquals("6 reps", set(null, 6).chipLabel())
        assertEquals("6 reps", set(0.0, 6).summaryText())
        assertEquals("90 kg × 5", set(90.0, 5).summaryText())
        assertEquals("no weight or reps", set(0.0, null).summaryText())
        assertEquals(null, set(0.0, 6).addedWeightKg)
    }

    @Test
    fun loggedSetsReadInTheirUnit() {
        fun set(kg: Double?, count: Int) = LoggedSet(1, "x", "g", kg, count, null, false, Instant.EPOCH)

        assertEquals("30 s", set(null, 30).chipLabel(RepsUnit.Seconds))
        assertEquals("10×30 s", set(10.0, 30).chipLabel(RepsUnit.Seconds))
        assertEquals("10 kg × 30 s", set(10.0, 30).summaryText(RepsUnit.Seconds))
        assertEquals("4 lengths", set(null, 4).summaryText(RepsUnit.Lengths))
        assertEquals("50 steps", set(0.0, 50).chipLabel(RepsUnit.Steps))
        assertEquals("16 kg × 10/side", set(16.0, 10).summaryText(RepsUnit.RepsPerSide))
        // Plain reps stay as they were.
        assertEquals("60×6", set(60.0, 6).chipLabel(RepsUnit.Reps))
        assertEquals("6 reps", set(null, 6).summaryText())
    }

    @Test
    fun aTargetsUnitNamesTheFieldAndSetsTheStep() {
        assertEquals(RepsUnit.Seconds, Reps.Duration(8, 15).unit)
        assertEquals("Seconds", RepsUnit.Seconds.fieldLabel)
        assertEquals(5, RepsUnit.Seconds.step)
        assertEquals("Reps per side", Reps.PerSide(10).unit.fieldLabel)
        assertEquals("Lengths", Reps.Lengths(2).unit.fieldLabel)
        assertEquals("Steps", Reps.Steps(50).unit.fieldLabel)
        assertEquals(RepsUnit.Reps, Reps.Range(10, 12).unit)
        assertEquals(1, RepsUnit.Reps.step)
    }
}
