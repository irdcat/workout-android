package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.results.ResultTarget
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ResultEditorViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = fixedClock("2026-06-03")

    private val squat = exercise(
        "squat", "1", "Przysiad",
        group(sets = 1, reps = Reps.Count(5), load = Load.Weight(60.0)),
        group(sets = 2, reps = Reps.Count(5), load = Load.Weight(90.0), result = "RPE 9"),
    )
    private val bench = exercise("bench", "2", "Bench Press", group(sets = 3, load = Load.Weight(70.0)))
    private val t1 = workout("W/1/T1", "T1", single(squat), single(bench))

    /** T1 the week before, with "RPE 8" for the squat in the sheet. */
    private val weekBefore = week(
        "w0", "2026-05-25",
        workout("W/0/T1", "T1", single(exercise("squat-before", "1", "Przysiad", group(result = "RPE 8")))),
    )
    private val plan = FakeWorkoutPlanRepository(months(weekBefore, week("w1", "2026-06-01", t1)))
    private val sessions = FakeActiveSessionRepository(clock)
    private val logs = FakeSessionLogRepository()
    private val results = FakeResultRepository()
    private val settings = FakeSettingsRepository()

    private fun TestScope.editor(exerciseId: String, review: Boolean = false) = ResultEditorViewModel(
        exerciseId, review, plan, sessions, logs, results,
        FindLastTime(plan, logs),
        settings,
        SyncWorkoutPlan(plan, sessions, results, settings),
        clock,
        backgroundScope,
    )

    private fun ResultEditorViewModel.ready(): ResultEditorUiState = (state.value as Loadable.Ready).value

    private suspend fun log(session: ActiveSession, exerciseId: String, weight: Double, reps: Int, rpe: Double? = null) =
        logs.logSet(session, LoggedSet(exerciseId = exerciseId, setGroupId = "$exerciseId-g", weightKg = weight, reps = reps, rpe = rpe, warmUp = false, loggedAt = clock.instant()))

    @Test
    fun opensOnTheRowWithTheResult() = runTest(main.dispatcher) {
        val editor = editor("squat").ready()

        assertEquals("1", editor.exerciseLabel)
        assertEquals("Przysiad", editor.exerciseName)
        assertEquals(listOf("First row · 1×5 60 kg", "Last row · 2×5 90 kg"), editor.targets)
        assertEquals(1, editor.selectedTarget)
        assertEquals("RPE 9", editor.text)
    }

    @Test
    fun aSingleRowNeedsNoChoiceAndStartsEmpty() = runTest(main.dispatcher) {
        val editor = editor("bench").ready()

        assertTrue(editor.targets.isEmpty())
        assertEquals("", editor.text)
        assertTrue(editor.suggestions.isEmpty())
        assertNull(editor.lastTime)
    }

    @Test
    fun lastTimeFromTheSheetHasNoDate() = runTest(main.dispatcher) {
        assertEquals(LastResult(date = null, text = "RPE 8"), editor("squat").ready().lastTime)
    }

    @Test
    fun lastTimeTrainedInTheAppHasItsDate() = runTest(main.dispatcher) {
        log(ActiveSession("W/0/T1", Instant.parse("2026-05-27T17:00:00Z")), "squat-before", 85.0, 5)

        assertEquals(LastResult(date = "27 May", text = "RPE 8"), editor("squat").ready().lastTime)
    }

    @Test
    fun suggestionsComeFromThisSessionsSets() = runTest(main.dispatcher) {
        val session = ActiveSession(t1.id, clock.instant())
        sessions.session.value = session
        log(session, "bench", 70.0, 5)
        log(session, "bench", 75.0, 5, rpe = 8.5)

        assertEquals(listOf("70×5, 75×5", "top 75×5", "RPE 8.5"), editor("bench").ready().suggestions)
    }

    @Test
    fun editingTheText() = runTest(main.dispatcher) {
        val viewModel = editor("squat")

        viewModel.onEvent(ResultEditorEvent.Insert("90×5"))
        assertEquals("RPE 9, 90×5", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.UseLastTime)
        assertEquals("RPE 8", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.Clear)
        assertEquals("", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.TextChanged("RPE 9.5"))
        assertEquals("RPE 9.5", viewModel.ready().text)
    }

    @Test
    fun savingStoresTheResultForTheChosenRowAndWritesItOutsideASession() = runTest(main.dispatcher) {
        val viewModel = editor("squat")
        viewModel.onEvent(ResultEditorEvent.TargetSelected(0))
        viewModel.onEvent(ResultEditorEvent.TextChanged(" ramp-up easy "))

        viewModel.onEvent(ResultEditorEvent.Save)

        assertEquals(true, viewModel.closed.first())
        testScheduler.runCurrent()
        // Not part of a session, so it's written now: a sync, then the write.
        assertEquals(1, plan.syncCalls)
        val written = results.written.single()
        assertEquals(ResultTarget("W/1/T1", "1", 0, "Przysiad"), written.target)
        assertEquals("ramp-up easy", written.text)
    }

    @Test
    fun duringTheSessionResultsWaitForTheSummary() = runTest(main.dispatcher) {
        sessions.session.value = ActiveSession(t1.id, clock.instant())
        val viewModel = editor("bench")

        viewModel.onEvent(ResultEditorEvent.TextChanged("RPE 8"))
        viewModel.onEvent(ResultEditorEvent.Save)
        viewModel.closed.first()
        testScheduler.runCurrent()

        assertEquals(1, results.saved.value.size)
        assertEquals(0, plan.syncCalls)
    }

    @Test
    fun anExerciseNoLongerInThePlanCloses() = runTest(main.dispatcher) {
        val viewModel = editor("gone")

        assertEquals(false, viewModel.closed.first())
        assertEquals(Loadable.Loading, viewModel.state.value)
    }

    @Test
    fun aReviewStartsFromTheLoggedSetsAndSavingFinishesTheExercise() = runTest(main.dispatcher) {
        val session = ActiveSession(t1.id, clock.instant())
        sessions.session.value = session
        log(session, "bench", 70.0, 5)
        log(session, "bench", 72.5, 5, rpe = 9.0)
        val viewModel = editor("bench", review = true)

        val editor = viewModel.ready()
        assertTrue(editor.review)
        assertEquals("70×5, 72.5×5", editor.text)

        viewModel.onEvent(ResultEditorEvent.TextChanged("70×5, 72.5×5, RPE 9"))
        viewModel.onEvent(ResultEditorEvent.Save)

        assertEquals(true, viewModel.closed.first())
        assertEquals("70×5, 72.5×5, RPE 9", results.saved.value.single().text)
        assertEquals(setOf("bench"), logs.logs.value.getValue(session).finishedExerciseIds)
    }

    @Test
    fun aReviewKeepsAResultAlreadyThere() = runTest(main.dispatcher) {
        val session = ActiveSession(t1.id, clock.instant())
        sessions.session.value = session
        log(session, "squat", 90.0, 5)

        assertEquals("RPE 9", editor("squat", review = true).ready().text)
    }

    @Test
    fun dismissingAReviewLeavesTheExerciseOpen() = runTest(main.dispatcher) {
        val session = ActiveSession(t1.id, clock.instant())
        sessions.session.value = session
        log(session, "bench", 70.0, 5)
        val viewModel = editor("bench", review = true)
        viewModel.ready()

        viewModel.onEvent(ResultEditorEvent.Dismiss)

        assertEquals(false, viewModel.closed.first())
        assertTrue(logs.logs.value.getValue(session).finishedExerciseIds.isEmpty())
    }
}
