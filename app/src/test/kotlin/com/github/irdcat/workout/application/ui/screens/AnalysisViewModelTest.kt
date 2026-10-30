package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.analysis.Lift
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

    // A plank first this week, then the deadlift, named in Polish once.
    private fun deadlift(id: String, kg: Double, name: String = "Deadlift") =
        exercise(id, "1", name, group(sets = 2, reps = Reps.Count(5), load = Load.Weight(kg)))
    private val przysiad = exercise("jun-p", "2", "Przysiad", group(sets = 3, load = Load.Weight(100.0)))
    private val bulgarski = exercise("jun-b", "3", "Przysiad bułgarski", group(sets = 3, load = Load.Weight(20.0)))
    private val plank = exercise("jun-d", "0", "Deska", group(sets = 3, reps = Reps.Duration(30, 30), load = Load.Bodyweight))

    private val plan = months(
        week("w1", "2026-05-25", workout("M/T1", "T1", single(deadlift("may-dl", 90.0, "Martwy ciąg")))),
        week(
            "w2", "2026-06-01",
            workout("J/T1", "T1", single(plank), single(deadlift("jun-dl", 92.5)), single(przysiad), single(bulgarski)),
        ),
        week("w3", "2026-06-08", workout("N/T1", "T1", single(deadlift("next-dl", 95.0)))),
    )
    private val logs = FakeSessionLogRepository()
    private val session = ActiveSession("M/T1", Instant.parse("2026-05-27T17:00:00Z"))

    private suspend fun log(exerciseId: String, kg: Double?, reps: Int, rpe: Double? = null, minute: Long = 0) = logs.logSet(
        session,
        LoggedSet(
            exerciseId = exerciseId, setGroupId = "g", weightKg = kg, reps = reps, rpe = rpe, warmUp = false,
            loggedAt = session.startedAt.plusSeconds(minute * 60),
        ),
    )

    private fun TestScope.analysis(saved: SavedStateHandle = SavedStateHandle()): AnalysisViewModel {
        val viewModel = AnalysisViewModel(FakeWorkoutPlanRepository(plan), logs, clock, saved)
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private fun AnalysisViewModel.ready(): AnalysisUiState = (state.value as Loadable.Ready).value

    @Test
    fun opensOnTheLiftLoggedLast() = runTest(main.dispatcher) {
        log("jun-p", 100.0, 5, minute = 1)
        log("may-dl", 100.0, 5, minute = 2)
        // Later, but not a lift.
        log("jun-d", null, 30, minute = 3)
        val state = analysis().ready()

        assertEquals(Lift.Deadlift, state.lift)
        assertEquals("Deadlift", state.exercise!!.name)
        assertEquals("In your sheet for 3 weeks · logged on this device for 1 week", state.exercise!!.coverage)
    }

    @Test
    fun withNothingLoggedItOpensOnThisWeeksFirstLift() = runTest(main.dispatcher) {
        assertEquals(Lift.Deadlift, analysis().ready().lift)
    }

    @Test
    fun eightWeeksRunUpToNextWeekWithThisWeekSelected() = runTest(main.dispatcher) {
        log("may-dl", 100.0, 5, rpe = 9.0)
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
    fun onlyTheLiftsCanBePicked() = runTest(main.dispatcher) {
        assertEquals(listOf(Lift.Squat, Lift.Deadlift), analysis().ready().lifts)
    }

    @Test
    fun pickingALiftShowsItAndIsRemembered() = runTest(main.dispatcher) {
        val saved = SavedStateHandle()
        val viewModel = analysis(saved)

        viewModel.onEvent(AnalysisEvent.LiftPicked(Lift.Squat))

        assertEquals(Lift.Squat, viewModel.ready().lift)
        assertEquals("Squat", viewModel.ready().exercise!!.name)
        // Reopened (e.g. after the process was killed): the same lift.
        assertEquals(Lift.Squat, analysis(saved).ready().lift)
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
        assertNull(viewModel.ready().lift)
    }
}
