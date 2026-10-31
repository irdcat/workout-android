package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.results.ResultTarget
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.loggedSet
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.superset
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class WorkoutSummaryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = fixedClock("2026-06-03", "18:41")
    private val workout = workout(
        "W/1/T2", "T2",
        single(exercise("rdl", "1", "RDL", group(sets = 3, result = "RPE 9"))),
        superset(
            2,
            exercise("a", "2a", "Biceps", group(sets = 3, result = "11, 11, 10")),
            exercise("b", "2b", "Reverse Flies", group(sets = 2)),
        ),
    )

    private val settings = FakeSettingsRepository()
    private val logs = FakeSessionLogRepository()
    private val results = FakeResultRepository()
    private val session = ActiveSession(workout.id, fixedClock("2026-06-03", "17:42").instant())
    private lateinit var plan: FakeWorkoutPlanRepository

    /** T2 the week before, trained in the app from 17:00 to 18:03. */
    private val sessionBefore = ActiveSession("W/0/T2", Instant.parse("2026-05-27T17:00:00Z"))
    private val weekBefore = week("w0", "2026-05-25", workout("W/0/T2", "T2", single(exercise("rdl-before", "1", "RDL", group()))))

    private fun TestScope.summary(earlier: List<TrainingWeek> = emptyList()): Pair<WorkoutSummaryViewModel, FakeActiveSessionRepository> {
        plan = FakeWorkoutPlanRepository(months(*earlier.toTypedArray(), week("w1", "2026-06-01", workout, sheet = "CZERWIEC")))
        val sessions = FakeActiveSessionRepository(clock, session)
        val viewModel = WorkoutSummaryViewModel(
            sessions, plan, logs, clock, settings,
            FindLastTime(plan, logs),
            results,
            SyncWorkoutPlan(plan, sessions, results, settings),
            backgroundScope,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel to sessions
    }

    private suspend fun log(session: ActiveSession, exerciseId: String, at: String, weight: Double = 15.0, rpe: Double? = null): LoggedSet {
        logs.logSet(session, loggedSet(exerciseId, "$exerciseId-g", kg = weight, reps = 10, at = Instant.parse(at), rpe = rpe))
        return logs.logs.value[session]!!.sets.last()
    }

    private fun WorkoutSummaryViewModel.ready(): WorkoutSummaryUiState = (state.value as Loadable.Ready).value

    @Test
    fun summarisesTheSession() = runTest(main.dispatcher) {
        val state = (summary().first.state.value as Loadable.Ready).value

        assertEquals("T2", state.workoutName)
        assertEquals("Wed 3 Jun · 17:42 – 18:41", state.dateLine)
        assertEquals(59 * 60, state.totalSeconds)
        assertEquals(2, state.exercisesDone)
        assertEquals(3, state.exercisesTotal)
        assertEquals(6, state.setsDone)
        assertEquals(8, state.setsTotal)
        assertEquals("CZERWIEC", state.sheetName)
        assertEquals(listOf("RDL", "2a Biceps", "2b Reverse Flies"), state.results.map { it.label })
        assertNull(state.results.last().result)
    }

    @Test
    fun saveEndsTheSessionAndRemembersWhen() = runTest(main.dispatcher) {
        val (viewModel, sessions) = summary()

        viewModel.onSave()

        assertNull(sessions.session.value)
        assertEquals(WorkoutSummaryNavigation.Saved, viewModel.navigation.first())
        assertEquals(clock.instant(), logs.finished[session])
    }

    @Test
    fun loggedSetsBecomeTheResultOfAnExerciseWithoutOne() = runTest(main.dispatcher) {
        settings.update { it.copy(writeResultsToSheet = false) }
        log(session, "b", "2026-06-03T18:00:00Z")
        log(session, "b", "2026-06-03T18:02:00Z", weight = 17.5)
        val (viewModel, _) = summary()

        assertEquals("15×10, 17.5×10", viewModel.ready().results.last().result)
        // An exercise with a result in the sheet keeps it.
        assertEquals("RPE 9", viewModel.ready().results.first().result)

        viewModel.onSave()
        viewModel.navigation.first()

        val saved = results.saved.value.single()
        assertEquals(ResultTarget("W/1/T2", "2b", 0, "Reverse Flies"), saved.target)
        assertEquals("15×10, 17.5×10", saved.text)
    }

    @Test
    fun theLastRpeLoggedGoesInTheResultWhenThePlanHasNone() = runTest(main.dispatcher) {
        settings.update { it.copy(writeResultsToSheet = false) }
        log(session, "b", "2026-06-03T18:00:00Z", rpe = 8.0)
        log(session, "b", "2026-06-03T18:02:00Z", weight = 17.5, rpe = 9.0)
        val (viewModel, _) = summary()

        assertEquals("15×10, 17.5×10, RPE 9", viewModel.ready().results.last().result)
        viewModel.onSave()
        viewModel.navigation.first()

        assertEquals("15×10, 17.5×10, RPE 9", results.saved.value.single().text)
    }

    @Test
    fun anRpeAsPlannedIsLeftOutOfTheResult() = runTest(main.dispatcher) {
        settings.update { it.copy(writeResultsToSheet = false) }
        val rows = exercise("rows", "3", "Rows", group(sets = 2, load = Load.Rpe(8.0)))
        val withTarget = workout.copy(items = workout.items + single(rows))
        plan = FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", withTarget, sheet = "CZERWIEC")))
        log(session, "rows", "2026-06-03T18:00:00Z", rpe = 8.0)
        val sessions = FakeActiveSessionRepository(clock, session)
        val viewModel = WorkoutSummaryViewModel(
            sessions, plan, logs, clock, settings, FindLastTime(plan, logs), results,
            SyncWorkoutPlan(plan, sessions, results, settings), backgroundScope,
        )
        backgroundScope.launch { viewModel.state.collect {} }

        assertEquals("15×10", viewModel.ready().results.last().result)
    }

    @Test
    fun savingWritesToTheSheetWhenThatIsOn() = runTest(main.dispatcher) {
        log(session, "b", "2026-06-03T18:00:00Z")
        val (viewModel, _) = summary()

        viewModel.onSave()
        viewModel.navigation.first()
        testScheduler.runCurrent()

        assertEquals(1, plan.syncCalls)
        assertEquals(1, results.writeCalls)
    }

    @Test
    fun withWritingOffSavingKeepsResultsOnThePhone() = runTest(main.dispatcher) {
        settings.update { it.copy(writeResultsToSheet = false) }
        log(session, "b", "2026-06-03T18:00:00Z")
        val (viewModel, _) = summary()

        viewModel.onSave()
        viewModel.navigation.first()
        testScheduler.runCurrent()

        assertEquals(0, plan.syncCalls)
        assertEquals(1, results.saved.value.size)
    }

    // ---- Time ----------------------------------------------------------------

    @Test
    fun restsTimedInTheAppSplitTheTime() = runTest(main.dispatcher) {
        val first = log(session, "b", "2026-06-03T18:00:00Z")
        val second = log(session, "b", "2026-06-03T18:03:00Z")
        logs.recordRest(session, first.id, Instant.parse("2026-06-03T18:01:30Z"))
        logs.recordRest(session, second.id, Instant.parse("2026-06-03T18:05:30Z"))

        val state = summary().first.ready()

        assertEquals(4 * 60, state.restingSeconds)
        assertEquals(59 * 60 - 4 * 60, state.workingSeconds)
        assertEquals(120, state.avgRestSeconds)
    }

    @Test
    fun withoutTimedRestsTheSplitIsUnknown() = runTest(main.dispatcher) {
        log(session, "b", "2026-06-03T18:00:00Z")

        val state = summary().first.ready()

        assertEquals(0, state.restingSeconds)
        assertEquals(0, state.workingSeconds)
        assertNull(state.avgRestSeconds)
    }

    @Test
    fun lastWeeksDurationWhenItWasFinishedInTheApp() = runTest(main.dispatcher) {
        log(sessionBefore, "rdl-before", "2026-05-27T17:10:00Z")
        assertNull(summary(earlier = listOf(weekBefore)).first.ready().previousSeconds)

        logs.finishSession(sessionBefore, Instant.parse("2026-05-27T18:03:00Z"))
        assertEquals(63 * 60, summary(earlier = listOf(weekBefore)).first.ready().previousSeconds)
    }

    @Test
    fun followsTheSettings() = runTest(main.dispatcher) {
        settings.state.value = AppSettings(measureWorkoutTime = false, writeResultsToSheet = false)

        val state = (summary().first.state.value as Loadable.Ready).value

        assertNull(state.totalSeconds)
        assertEquals(false, state.writesToSheet)
    }
}
