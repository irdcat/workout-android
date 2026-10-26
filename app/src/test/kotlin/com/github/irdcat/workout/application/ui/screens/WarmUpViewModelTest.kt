package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.AbandonSession
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWarmUpRestRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.RecordingRestAlarm
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class WarmUpViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = fixedClock("2026-06-03")
    private val startedAt = clock.instant().minus(Duration.ofMinutes(11).plusSeconds(36))

    private val rdl = exercise(
        "rdl", "1", "RDL",
        group(reps = Reps.Count(6), load = Load.Weight(60.0), rest = 150),
        group(sets = 4, reps = Reps.Count(5), load = Load.Weight(92.5), rest = 150),
    )
    private val planned = workout("W/1/T2", "T2", single(rdl))

    private val settings = FakeSettingsRepository()
    private val logs = FakeSessionLogRepository()
    private val warmUpRests = FakeWarmUpRestRepository()
    private val session = ActiveSession(planned.id, startedAt)

    private fun TestScope.warmUp(earlier: List<TrainingWeek> = emptyList()): WarmUpViewModel {
        val plan = FakeWorkoutPlanRepository(months(*earlier.toTypedArray(), week("w1", "2026-06-01", planned)))
        val sessions = FakeActiveSessionRepository(clock, session)
        val viewModel = WarmUpViewModel(
            sessions,
            plan,
            logs,
            warmUpRests,
            clock,
            settings,
            FindLastTime(plan, logs),
            AbandonSession(sessions, logs, FakeResultRepository(), RecordingRestAlarm()),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private fun WarmUpViewModel.ready(): WarmUpUiState = (state.value as Loadable.Ready).value

    private fun warmUpSet(kg: Double, reps: Int) =
        LoggedSet(exerciseId = "rdl", setGroupId = rdl.setGroups[0].id, weightKg = kg, reps = reps, rpe = null, warmUp = true, loggedAt = startedAt)

    @Test
    fun forTheExerciseUpNext() = runTest(main.dispatcher) {
        val state = warmUp().ready()

        assertEquals("T2", state.workoutName)
        assertEquals("11:36", state.elapsed)
        assertEquals(1, state.position)
        assertEquals("RDL", state.exerciseName)
        assertEquals(5, state.plannedSets)
        assertEquals(1, state.number)
        assertEquals(WarmUpEntry(weight = "0", weightStep = "2.5", reps = "", rest = "1:00"), state.entry)
        assertNull(state.lastTime)
    }

    @Test
    fun loggingAWarmUpLeavesThePlanAsItWasAndStartsItsRest() = runTest(main.dispatcher) {
        val viewModel = warmUp()
        viewModel.onEvent(WarmUpEvent.WeightChanged("20"))
        viewModel.onEvent(WarmUpEvent.RepsChanged("10"))
        viewModel.onEvent(WarmUpEvent.AdjustRest(+1))

        viewModel.onEvent(WarmUpEvent.LogWarmUp)

        assertEquals(WarmUpNavigation.RestStarted, viewModel.navigation.first())
        val set = logs.logs.value[session]!!.sets.single()
        assertTrue(set.warmUp)
        assertEquals(20.0, set.weightKg)
        assertEquals(10, set.reps)
        assertEquals(75, warmUpRests.rests.value["rdl"])

        val state = viewModel.ready()
        assertEquals(listOf("20×10"), state.done)
        assertEquals(2, state.number)
        // The next warm-up repeats the last one and its rest.
        assertEquals(WarmUpEntry(weight = "20", weightStep = "2.5", reps = "10", rest = "1:15"), state.entry)
    }

    @Test
    fun noRestNoRestTimer() = runTest(main.dispatcher) {
        val viewModel = warmUp()
        viewModel.onEvent(WarmUpEvent.RepsChanged("10"))
        viewModel.onEvent(WarmUpEvent.RestChanged("0:00"))

        viewModel.onEvent(WarmUpEvent.LogWarmUp)
        testScheduler.runCurrent()

        assertEquals(1, logs.logs.value[session]!!.sets.size)
        assertEquals(2, viewModel.ready().number)
    }

    @Test
    fun startsFromTheSavedRestAndTheSameWarmUpLastTime() = runTest(main.dispatcher) {
        warmUpRests.save("RDL", 45)
        val then = workout("M/1/T2", "T2", single(exercise("rdl-then", "1", "RDL", group(load = Load.Weight(60.0)))))
        val sessionThen = ActiveSession(then.id, startedAt.minus(Duration.ofDays(7)))
        logs.logSet(sessionThen, warmUpSet(20.0, 10).copy(exerciseId = "rdl-then", loggedAt = sessionThen.startedAt))
        logs.logSet(sessionThen, warmUpSet(40.0, 8).copy(exerciseId = "rdl-then", loggedAt = sessionThen.startedAt))
        logs.logSet(session, warmUpSet(20.0, 10))

        val state = warmUp(earlier = listOf(week("w0", "2026-05-25", then))).ready()

        assertEquals(WarmUpEntry(weight = "40", weightStep = "2.5", reps = "8", rest = "0:45"), state.entry)
        assertEquals(LastTime(date = "27 May", summary = "20×10, 40×8", note = null), state.lastTime)
    }

    @Test
    fun aDefaultStepFromSettings() = runTest(main.dispatcher) {
        settings.update { AppSettings(defaultWeightStepKg = 5.0) }
        assertEquals("5", warmUp().ready().entry.weightStep)
    }

    @Test
    fun restTimes() {
        assertEquals(90, parseRestTime("1:30"))
        assertEquals(90, parseRestTime("90"))
        assertEquals(5, parseRestTime(":05"))
        assertNull(parseRestTime("1:75"))
        assertNull(parseRestTime(""))
        assertEquals("2:05", formatRestTime(125))
        assertEquals("1:3", "1a:3:".filterRestTime())
    }

    @Test
    fun repsStepButNeverBelowZero() = runTest(main.dispatcher) {
        val viewModel = warmUp()

        viewModel.onEvent(WarmUpEvent.AdjustReps(-1))
        assertEquals("0", viewModel.ready().entry.reps)
        viewModel.onEvent(WarmUpEvent.AdjustReps(+1))
        viewModel.onEvent(WarmUpEvent.AdjustReps(+1))
        assertEquals("2", viewModel.ready().entry.reps)
        viewModel.onEvent(WarmUpEvent.WeightStepSelected("5"))
        viewModel.onEvent(WarmUpEvent.AdjustWeight(+1))
        assertEquals("5", viewModel.ready().entry.weight)
    }

    @Test
    fun onceThePlanIsDoneWarmUpsAreForTheLastExercise() = runTest(main.dispatcher) {
        rdl.setGroups.forEach { group ->
            repeat((group.sets as SetCount.Fixed).count) {
                logs.logSet(session, warmUpSet(60.0, 5).copy(setGroupId = group.id, warmUp = false))
            }
        }
        val viewModel = warmUp()
        assertEquals("RDL", viewModel.ready().exerciseName)
        viewModel.onEvent(WarmUpEvent.RepsChanged("5"))
        viewModel.onEvent(WarmUpEvent.LogWarmUp)
        viewModel.navigation.first()

        assertEquals(rdl.setGroups.last().id, logs.logs.value[session]!!.sets.last().setGroupId)
    }

    @Test
    fun aWarmUpNeedsReps() = runTest(main.dispatcher) {
        val viewModel = warmUp()
        viewModel.onEvent(WarmUpEvent.WeightChanged("20"))

        viewModel.onEvent(WarmUpEvent.LogWarmUp)
        testScheduler.runCurrent()

        assertNull(logs.logs.value[session])
    }

    @Test
    fun aWarmUpAtZeroIsBodyweight() = runTest(main.dispatcher) {
        val viewModel = warmUp()
        viewModel.onEvent(WarmUpEvent.RepsChanged("10"))

        viewModel.onEvent(WarmUpEvent.LogWarmUp)
        viewModel.navigation.first()

        assertNull(logs.logs.value[session]!!.sets.single().weightKg)
        assertEquals(listOf("10 reps"), viewModel.ready().done)
    }
}
