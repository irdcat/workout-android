package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AnalysisViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    /** Wednesday 3 June 2026: the week of 1 June is the current one. */
    private val clock = fixedClock("2026-06-03")

    private fun rdl(id: String, kg: Double) = exercise(id, "1", "RDL", group(sets = 2, reps = Reps.Count(5), load = Load.Weight(kg)))
    private val przysiad = exercise("jun-p", "2", "Przysiad", group(sets = 3, load = Load.Weight(100.0)))
    private val bulgarski = exercise("jun-b", "3", "Przysiad bułgarski", group(sets = 3, load = Load.Weight(20.0)))
    private val plank = exercise("jun-d", "4", "Deska", group(sets = 3, reps = Reps.Duration(30, 30), load = Load.Bodyweight))

    private val plan = months(
        week("w1", "2026-05-25", workout("M/T1", "T1", single(rdl("may-rdl", 90.0)))),
        week("w2", "2026-06-01", workout("J/T1", "T1", single(rdl("jun-rdl", 92.5)), single(przysiad), single(bulgarski), single(plank))),
        week("w3", "2026-06-08", workout("N/T1", "T1", single(rdl("next-rdl", 95.0)))),
    )
    private val logs = FakeSessionLogRepository()
    private val session = ActiveSession("M/T1", Instant.parse("2026-05-27T17:00:00Z"))

    private suspend fun log(exerciseId: String, kg: Double?, reps: Int, rpe: Double? = null) =
        logs.logSet(session, LoggedSet(exerciseId = exerciseId, setGroupId = "g", weightKg = kg, reps = reps, rpe = rpe, warmUp = false, loggedAt = session.startedAt))

    private fun TestScope.analysis(saved: SavedStateHandle = SavedStateHandle()): AnalysisViewModel {
        val viewModel = AnalysisViewModel(FakeWorkoutPlanRepository(plan), logs, clock, saved)
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private fun AnalysisViewModel.ready(): AnalysisUiState = (state.value as Loadable.Ready).value

    @Test
    fun opensOnTheExerciseLoggedLast() = runTest(main.dispatcher) {
        log("may-rdl", 100.0, 5)
        val state = analysis().ready()

        assertEquals("RDL", state.query)
        assertEquals("RDL", state.exercise!!.name)
        assertEquals("In your sheet for 3 weeks · logged on this device for 1 week", state.exercise!!.coverage)
    }

    @Test
    fun withNothingLoggedItOpensOnThisWeeksFirstExercise() = runTest(main.dispatcher) {
        assertEquals("RDL", analysis().ready().exercise!!.name)
    }

    @Test
    fun eightWeeksRunUpToNextWeekWithThisWeekSelected() = runTest(main.dispatcher) {
        log("may-rdl", 100.0, 5, rpe = 9.0)
        val exercise = analysis().ready().exercise!!

        assertEquals(8, exercise.weeks.size)
        assertEquals(LocalDate.parse("2026-04-20"), exercise.weeks.first().start)
        assertEquals(6, exercise.selectedWeek)
        assertTrue(exercise.weeks[6].current)
        assertTrue(exercise.weeks[7].future)
        assertEquals("1.6", exercise.weeks[6].label)

        val volume = exercise.volume as MetricPanel.Chart
        assertEquals(listOf(null, null, null, null, null, 900.0, 925.0, 950.0), volume.planned)
        assertEquals(listOf(null, null, null, null, null, 500.0, null, null), volume.actual)
        assertEquals("Sets × reps × kg", volume.scale)
        assertEquals("No logs on this device before 25 May.", volume.note)
        val intensity = exercise.intensity as MetricPanel.Chart
        assertEquals("25 May: you lifted 100 kg, 10 kg above plan.", intensity.note)
    }

    @Test
    fun allWeeksSpanEverythingThereIs() = runTest(main.dispatcher) {
        val viewModel = analysis()

        viewModel.onEvent(AnalysisEvent.RangeSelected(AnalysisRange.All))

        val weeks = viewModel.ready().exercise!!.weeks
        assertEquals(listOf("25.5", "1.6", "8.6"), weeks.map { it.label })
        assertEquals(AnalysisRange.All, viewModel.ready().range)
    }

    @Test
    fun searchingMatchesNamesStartingWithTheTextFirst() = runTest(main.dispatcher) {
        val viewModel = analysis()

        viewModel.onEvent(AnalysisEvent.QueryChanged("prz"))

        val state = viewModel.ready()
        assertEquals("prz", state.query)
        assertEquals(listOf("Przysiad", "Przysiad bułgarski"), state.options.map { it.name })
        assertEquals(2, state.matchCount)
        assertEquals(4, state.exerciseCount)
        assertEquals(0 to 3, state.options.first().let { it.matchStart to it.matchLength })

        viewModel.onEvent(AnalysisEvent.QueryChanged("garski"))
        assertEquals(listOf("Przysiad bułgarski"), viewModel.ready().options.map { it.name })
        assertEquals(12, viewModel.ready().options.single().matchStart)
    }

    @Test
    fun pickingAnExerciseShowsItAndIsRemembered() = runTest(main.dispatcher) {
        val saved = SavedStateHandle()
        val viewModel = analysis(saved)
        viewModel.onEvent(AnalysisEvent.QueryChanged("bułg"))

        viewModel.onEvent(AnalysisEvent.ExercisePicked("przysiad bułgarski"))

        assertEquals("Przysiad bułgarski", viewModel.ready().query)
        assertEquals("Przysiad bułgarski", viewModel.ready().exercise!!.name)
        // Reopened (e.g. after the process was killed): the same exercise.
        assertEquals("Przysiad bułgarski", analysis(saved).ready().exercise!!.name)
    }

    @Test
    fun closingTheSearchPutsTheChosenNameBack() = runTest(main.dispatcher) {
        val viewModel = analysis()
        viewModel.onEvent(AnalysisEvent.QueryChanged("xyz"))
        assertTrue(viewModel.ready().options.isEmpty())

        viewModel.onEvent(AnalysisEvent.SearchClosed)

        assertEquals("RDL", viewModel.ready().query)
    }

    @Test
    fun aHoldHasNoIntensityAndNoRpe() = runTest(main.dispatcher) {
        val viewModel = analysis()
        viewModel.onEvent(AnalysisEvent.ExercisePicked("deska"))

        val exercise = viewModel.ready().exercise!!
        assertEquals("Hold time, seconds", exercise.volume.scale)
        assertEquals("s", (exercise.volume as MetricPanel.Chart).suffix)
        assertEquals("Not available", (exercise.intensity as MetricPanel.Unavailable).heading)
        assertEquals("No RPE yet", (exercise.rpe as MetricPanel.Unavailable).heading)
    }

    @Test
    fun weeksTogglesAndSelection() = runTest(main.dispatcher) {
        val viewModel = analysis()

        viewModel.onEvent(AnalysisEvent.TogglePlanned)
        viewModel.onEvent(AnalysisEvent.WeekSelected(LocalDate.parse("2026-05-25")))

        val state = viewModel.ready()
        assertFalse(state.showPlanned)
        assertTrue(state.showActual)
        assertEquals(5, state.exercise!!.selectedWeek)

        // A new range starts from this week again.
        viewModel.onEvent(AnalysisEvent.RangeSelected(AnalysisRange.SixteenWeeks))
        assertEquals(14, viewModel.ready().exercise!!.selectedWeek)
    }

    @Test
    fun noPlanNothingToAnalyse() = runTest(main.dispatcher) {
        val viewModel = AnalysisViewModel(FakeWorkoutPlanRepository(months()), logs, clock, SavedStateHandle())
        backgroundScope.launch { viewModel.state.collect {} }

        assertNull(viewModel.ready().exercise)
        assertEquals(0, viewModel.ready().exerciseCount)
    }
}
