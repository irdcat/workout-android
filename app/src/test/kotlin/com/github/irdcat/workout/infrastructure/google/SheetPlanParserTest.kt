package com.github.irdcat.workout.infrastructure.google

import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.TempoPattern
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.WorkoutItem
import com.github.irdcat.workout.domain.workout.weekOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Parses the real exports in test-data and checks them against the design's notes. */
class SheetPlanParserTest {

    private val months = SheetFixtures.months()
    private val weeks = months.flatMap { it.weeks }

    private fun week(date: String): TrainingWeek {
        val week = months.weekOf(LocalDate.parse(date))
        assertNotNull("no week contains $date", week)
        return week!!
    }

    @Test
    fun readsAllWeeksAndSetRows() {
        assertEquals(30, weeks.size)
        assertEquals(LocalDate.of(2026, 2, 9), weeks.first().start)
        assertEquals(LocalDate.of(2026, 9, 20), weeks.last().end)
        assertEquals(780, SheetFixtures.parsed().sumOf { it.second.setGroups.size })
    }

    @Test
    fun weeksBelongToTheMonthHoldingMostOfTheirDays() {
        val spillover = week("2026-06-29")
        assertEquals("LIPIEC", spillover.sheetName)
        assertEquals(YearMonth.of(2026, 7), spillover.month)

        val april = week("2026-03-30")
        assertEquals("KWIECIEŃ", april.sheetName)
        assertEquals(YearMonth.of(2026, 4), april.month)

        // 23 Feb – 1 Mar: six of seven days in February.
        assertEquals(YearMonth.of(2026, 2), week("2026-02-23").month)
    }

    @Test
    fun restAndMissingWeeks() {
        val rest = week("2026-05-04")
        assertTrue(rest.isRestWeek)
        assertTrue(rest.workouts.isEmpty())

        assertNull(months.weekOf(LocalDate.of(2026, 7, 13)))
        assertNull(months.weekOf(LocalDate.of(2026, 8, 3)))
        assertEquals(1, weeks.count { it.isRestWeek })
    }

    @Test
    fun workoutCodesChangeInMay() {
        assertEquals(listOf("L1", "U1", "L2", "U2"), week("2026-02-09").workouts.map { it.code })
        assertEquals(listOf("T1", "T2", "T3"), week("2026-06-01").workouts.map { it.code })
        assertEquals(listOf("T2", "T3"), week("2026-09-07").workouts.map { it.code })
    }

    @Test
    fun workoutIdsUseTabYearWeekNumberAndName() {
        assertEquals(
            listOf("CZERWIEC2026/1/T1", "CZERWIEC2026/1/T2", "CZERWIEC2026/1/T3"),
            week("2026-06-01").workouts.map { it.id },
        )
        assertEquals("CZERWIEC2026/2/T1", week("2026-06-08").workouts.first().id)
        // The spillover week is the first one on the LIPIEC tab.
        assertEquals("LIPIEC2026/1/T1", week("2026-06-29").workouts.first().id)
        // 13 – 19 Jul is missing, so 20 Jul is the tab's third week.
        assertEquals("LIPIEC2026/3/T1", week("2026-07-20").workouts.first().id)

        val ids = weeks.flatMap { it.workouts }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun rampSetsAndSupersets() {
        val t1 = week("2026-06-01").workouts.first { it.code == "T1" }
        assertEquals("Przysiad · Bench Press", t1.title())
        assertEquals(5, t1.items.size)

        val squat = t1.exercises.first()
        assertEquals(5, squat.setGroups.size)
        assertEquals(7, squat.setCount)
        assertEquals(Load.Weight(60.0), squat.setGroups.first().load)
        assertEquals("RPE 9.5", squat.setGroups.last().result)
        assertTrue(squat.isDone)

        val superset = t1.items.last() as WorkoutItem.Superset
        assertEquals(5, superset.number)
        assertEquals(listOf("5a", "5b", "5c"), superset.exercises.map { it.number.toString() })
        assertEquals(3, superset.rounds)
        assertTrue(t1.isCompleted)
    }

    @Test
    fun tempoChangesInsideAnExerciseAndResultsSitOnTheirRow() {
        val rdl = week("2026-06-01").workouts.first { it.code == "T2" }.exercises.first { it.name == "RDL" }
        assertEquals(4, rdl.setGroups.size)
        assertNull(rdl.setGroups.first().tempo)
        val last = rdl.setGroups.last()
        assertEquals(TempoPattern("3010"), last.tempo)
        assertEquals(Load.Weight(92.5), last.load)
        assertEquals("RPE 9", last.result)
        // CZERWIEC row 23 in the sheet (1-based): where its WYNIK cell lives.
        assertEquals(23, last.sheetRow)
    }

    @Test
    fun openSetsAndContinuationNotes() {
        val pyramid = week("2026-07-27").workouts.first { it.code == "T3" }.exercises.first()
        assertEquals(SetCount.Open, pyramid.setGroups.single().sets)
        assertTrue(pyramid.isOpenSets)
        assertNull(pyramid.setCount)
        assertNull(pyramid.setGroups.single().restSeconds)

        val squat = week("2026-02-09").workouts.first().exercises.first()
        assertEquals("PRZYSIAD(ZNAJDŹ CIĘŻKĄ PIĄTKĘ)", squat.setGroups.last().note)

        // "-" in the name column of a continuation row is not a note.
        val sumoRdl = weeks.flatMap { it.workouts }.flatMap { it.exercises }.first { it.name == "Bench Supported Sumo RDL" }
        assertNull(sumoRdl.setGroups.last().note)
        assertFalse(sumoRdl.setGroups.isEmpty())
    }

    private fun rows(vararg rows: List<String>) = rows.mapIndexed { index, values -> SheetRow(index, "TEST", values) }

    private val header = listOf("ĆWICZENIE", "SERIE", "POWTÓRZENIA", "CIĘŻAR / RPE", "TEMPO", "PRZERWA", "WYNIK")

    @Test
    fun aWorkoutNameRepeatedInAWeekGetsItsOwnId() {
        val content = SheetPlanParser.parse(
            "TEST",
            rows(
                listOf("", "01.06.2026 - 07.06.2026"),
                listOf("T1") + header,
                listOf("1", "Przysiad", "1", "5", "40", "", "90sek", ""),
                listOf("T1") + header,
                listOf("1", "Bench Press", "1", "5", "40", "", "90sek", ""),
                listOf("T1") + header,
                listOf("1", "RDL", "1", "5", "40", "", "90sek", ""),
            ),
        )

        assertEquals(listOf("TEST2026/1/T1", "TEST2026/1/T1#2", "TEST2026/1/T1#3"), content.workouts.map { it.id })
        assertEquals(listOf(0, 1, 2), content.workouts.map { it.position })
    }

    @Test
    fun aDateThatCantBeReadIsNotAWeekSoItsRowsAreSkipped() {
        val content = SheetPlanParser.parse(
            "TEST",
            rows(
                listOf("", "01.13.2026 - 07.13.2026"),
                listOf("T1") + header,
                listOf("1", "Przysiad", "1", "5", "40", "", "90sek", ""),
            ),
        )

        assertTrue(content.weeks.isEmpty())
        assertTrue(content.workouts.isEmpty())
        assertTrue(content.exercises.isEmpty())
    }
}
