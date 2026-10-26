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
import org.junit.Assert.assertFalse
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
    private val rows = exercise("rows", "3", "Wiosłowanie", group(sets = 3, reps = Reps.Count(10), load = Load.Rpe(8.0)))
    private val t1 = workout("W/1/T1", "T1", single(squat), single(bench), single(rows))

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
        // The result as it is; its RPE also fills the field.
        assertEquals("RPE 9", editor.text)
        assertEquals("9", editor.rpe)
        assertNull(editor.rpeTarget)
    }

    @Test
    fun aSingleRowNeedsNoChoiceAndStartsEmpty() = runTest(main.dispatcher) {
        val editor = editor("bench").ready()

        assertTrue(editor.targets.isEmpty())
        assertEquals("", editor.text)
        assertEquals("", editor.rpe)
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

        val editor = editor("bench").ready()

        assertEquals(listOf("70×5, 75×5", "top 75×5"), editor.suggestions)
        // The RPE logged last starts the field.
        assertEquals("8.5", editor.rpe)
    }

    @Test
    fun editingTheText() = runTest(main.dispatcher) {
        val viewModel = editor("squat")

        // Snippets go before the RPE, which stays last.
        viewModel.onEvent(ResultEditorEvent.Insert("90×5"))
        assertEquals("90×5, RPE 9", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.Insert("easy"))
        assertEquals("90×5, easy, RPE 9", viewModel.ready().text)
        // Last time's RPE isn't taken over.
        viewModel.onEvent(ResultEditorEvent.UseLastTime)
        assertEquals("RPE 9", viewModel.ready().text)
        assertEquals("9", viewModel.ready().rpe)
        viewModel.onEvent(ResultEditorEvent.TextChanged("grip gave out"))
        assertEquals("grip gave out", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.Clear)
        assertEquals("", viewModel.ready().text)
        assertEquals("", viewModel.ready().rpe)
    }

    @Test
    fun editingTheRpe() = runTest(main.dispatcher) {
        val viewModel = editor("bench")

        viewModel.onEvent(ResultEditorEvent.TextChanged("70×5"))
        viewModel.onEvent(ResultEditorEvent.AdjustRpe(+1))
        assertEquals("8", viewModel.ready().rpe)
        assertEquals("70×5, RPE 8", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.AdjustRpe(+1))
        assertEquals("70×5, RPE 8.5", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.RpeChanged("9,5x"))
        assertEquals("9.5", viewModel.ready().rpe)
        assertEquals("70×5, RPE 9.5", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.RpeChanged(""))
        assertEquals("70×5", viewModel.ready().text)
    }

    @Test
    fun anRpeTypedInTheTextFillsTheField() = runTest(main.dispatcher) {
        val viewModel = editor("bench")

        viewModel.onEvent(ResultEditorEvent.TextChanged("70×5, RPE 7.5"))

        assertEquals("7.5", viewModel.ready().rpe)
    }

    @Test
    fun theRpeComesAndGoesWithTheRowsTarget() = runTest(main.dispatcher) {
        val plan = FakeWorkoutPlanRepository(
            months(week("w1", "2026-06-01", workout("W/1/T1", "T1", single(exercise(
                "rows", "3", "Wiosłowanie",
                group(load = Load.Weight(20.0)),
                group(load = Load.Rpe(8.0), result = "20×10"),
            ))))),
        )
        val viewModel = ResultEditorViewModel(
            "rows", false, plan, sessions, logs, results, FindLastTime(plan, logs), settings,
            SyncWorkoutPlan(plan, sessions, results, settings), clock, backgroundScope,
        )
        assertEquals("20×10", viewModel.ready().text)

        // The first row has no target: the same RPE has to be written.
        viewModel.onEvent(ResultEditorEvent.TargetSelected(0))
        assertEquals("20×10, RPE 8", viewModel.ready().text)
        viewModel.onEvent(ResultEditorEvent.TargetSelected(1))
        assertEquals("20×10", viewModel.ready().text)
    }

    @Test
    fun aResultNeedsAnRpe() = runTest(main.dispatcher) {
        sessions.session.value = ActiveSession(t1.id, clock.instant())
        val viewModel = editor("bench")
        viewModel.onEvent(ResultEditorEvent.TextChanged("70×5"))
        assertFalse(viewModel.ready().canSave)

        viewModel.onEvent(ResultEditorEvent.Save)
        testScheduler.runCurrent()
        assertTrue(results.saved.value.isEmpty())

        viewModel.onEvent(ResultEditorEvent.RpeChanged("11"))
        assertFalse(viewModel.ready().canSave)
        viewModel.onEvent(ResultEditorEvent.RpeChanged("8"))
        assertTrue(viewModel.ready().canSave)
    }

    @Test
    fun anRpeAsPlannedIsLeftOut() = runTest(main.dispatcher) {
        sessions.session.value = ActiveSession(t1.id, clock.instant())
        val viewModel = editor("rows")
        assertEquals("8", viewModel.ready().rpeTarget)
        viewModel.onEvent(ResultEditorEvent.TextChanged("20×10, 20×10, 20×10"))
        viewModel.onEvent(ResultEditorEvent.RpeChanged("8"))

        viewModel.onEvent(ResultEditorEvent.Save)
        viewModel.closed.first()

        assertEquals("20×10, 20×10, 20×10", results.saved.value.single().text)
    }

    @Test
    fun anRpeOffThePlanIsWritten() = runTest(main.dispatcher) {
        sessions.session.value = ActiveSession(t1.id, clock.instant())
        val viewModel = editor("rows")
        viewModel.onEvent(ResultEditorEvent.TextChanged("20×10, 20×10, 20×8"))
        viewModel.onEvent(ResultEditorEvent.RpeChanged("9.5"))
        assertEquals("20×10, 20×10, 20×8, RPE 9.5", viewModel.ready().text)

        viewModel.onEvent(ResultEditorEvent.Save)
        viewModel.closed.first()

        assertEquals("20×10, 20×10, 20×8, RPE 9.5", results.saved.value.single().text)
    }

    @Test
    fun aSavedResultWithoutAnRpeWasAsPlanned() = runTest(main.dispatcher) {
        val plan = FakeWorkoutPlanRepository(
            months(week("w1", "2026-06-01", workout("W/1/T1", "T1", single(exercise("rows", "3", "Wiosłowanie", group(load = Load.Rpe(8.5), result = "20×10")))))),
        )
        val viewModel = ResultEditorViewModel(
            "rows", false, plan, sessions, logs, results, FindLastTime(plan, logs), settings,
            SyncWorkoutPlan(plan, sessions, results, settings), clock, backgroundScope,
        )

        assertEquals("20×10", viewModel.ready().text)
        assertEquals("8.5", viewModel.ready().rpe)
    }

    @Test
    fun savingStoresTheResultForTheChosenRowAndWritesItOutsideASession() = runTest(main.dispatcher) {
        val viewModel = editor("squat")
        viewModel.onEvent(ResultEditorEvent.TargetSelected(0))
        viewModel.onEvent(ResultEditorEvent.TextChanged(" ramp-up easy "))
        viewModel.onEvent(ResultEditorEvent.RpeChanged("6"))

        viewModel.onEvent(ResultEditorEvent.Save)

        assertEquals(true, viewModel.closed.first())
        testScheduler.runCurrent()
        // Not part of a session, so it's written now: a sync, then the write.
        assertEquals(1, plan.syncCalls)
        val written = results.written.single()
        assertEquals(ResultTarget("W/1/T1", "1", 0, "Przysiad"), written.target)
        assertEquals("ramp-up easy, RPE 6", written.text)
    }

    @Test
    fun duringTheSessionResultsWaitForTheSummary() = runTest(main.dispatcher) {
        sessions.session.value = ActiveSession(t1.id, clock.instant())
        val viewModel = editor("bench")

        viewModel.onEvent(ResultEditorEvent.RpeChanged("8"))
        viewModel.onEvent(ResultEditorEvent.Save)
        viewModel.closed.first()
        testScheduler.runCurrent()

        assertEquals("RPE 8", results.saved.value.single().text)
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
        assertEquals("70×5, 72.5×5, RPE 9", editor.text)
        assertEquals("9", editor.rpe)

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

        val editor = editor("squat", review = true).ready()

        assertEquals("RPE 9", editor.text)
        assertEquals("9", editor.rpe)
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

    @Test
    fun splittingTheRpeOffAResult() {
        assertEquals("70×5" to "9", splitRpe("70×5, RPE 9"))
        assertEquals("" to "8.5", splitRpe("rpe 8,5"))
        assertEquals("easy" to null, splitRpe(" easy "))
        // A range isn't one RPE: it stays text.
        assertEquals("RPE 7.5 - 8" to null, splitRpe("RPE 7.5 - 8"))
    }

    @Test
    fun theResultText() {
        assertEquals("70×5, RPE 9", resultText("70×5", "9", target = null))
        assertEquals("70×5", resultText("70×5", "8.0", target = "8"))
        assertEquals("70×5, RPE 9", resultText("70×5, RPE 7", "9", target = "8"))
        assertEquals("RPE 8", resultText("", "8", target = "8"))
        assertEquals("", resultText("", "", target = "8"))
    }
}
