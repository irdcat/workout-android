package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.RestClocks
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeSetRestRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWarmUpRestRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.MutableClock
import com.github.irdcat.workout.testing.RecordingRestAlarm
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.loggedSet
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.superset
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = MutableClock(fixedClock("2026-06-03", "17:42").instant())
    private val rdl = exercise(
        "rdl", "1", "RDL",
        group(reps = Reps.Count(6), load = Load.Weight(60.0), rest = 150),
        group(reps = Reps.Count(6), load = Load.Weight(75.0), rest = 150, tempo = "3010"),
    )
    private val planned = workout("W/1/T2", "T2", single(rdl))

    private val settings = FakeSettingsRepository(AppSettings(restEndAlert = RestEndAlert.Both))
    private val logs = FakeSessionLogRepository()
    private val alarm = RecordingRestAlarm()
    private val warmUpRests = FakeWarmUpRestRepository()
    private val setRests = FakeSetRestRepository()
    private val restClocks = RestClocks()
    private lateinit var session: ActiveSession
    private val created = mutableListOf<RestTimerViewModel>()

    /**
     * The timer ticks for as long as the view model lives, which in the app ends when the
     * screen closes. Tests end it themselves, or the test scheduler would tick forever.
     */
    private fun restTest(block: suspend TestScope.() -> Unit) = runTest(main.dispatcher) {
        try {
            block()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
        }
    }

    private suspend fun TestScope.rest(workout: Workout, vararg sets: LoggedSet): RestTimerViewModel {
        session = ActiveSession(workout.id, clock.instant())
        sets.forEach { logs.logSet(session, it) }
        val viewModel = RestTimerViewModel(
            FakeActiveSessionRepository(clock, session),
            FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", workout))),
            logs,
            settings,
            warmUpRests,
            setRests,
            restClocks,
            alarm,
            clock,
        )
        created += viewModel
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private fun set(exerciseId: String, groupId: String, weight: Double?, reps: Int, warmUp: Boolean = false) =
        loggedSet(exerciseId, groupId, kg = weight, reps = reps, at = clock.instant(), warmUp = warmUp)

    private fun RestTimerViewModel.ready(): RestTimerUiState = (state.value as Loadable.Ready).value

    /** Moves the clock and lets the once-a-second tick pick it up. */
    private fun TestScope.passSeconds(seconds: Long) {
        clock.advance(seconds)
        testScheduler.advanceTimeBy(seconds * 1_000)
        testScheduler.runCurrent()
    }

    @Test
    fun timesTheRestAfterTheLastSet() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))
        passSeconds(30)

        val rest = viewModel.ready()
        assertEquals(150, rest.totalSeconds)
        assertEquals(120, rest.remainingSeconds)
        assertEquals(LoggedSetInfo("Set 1", "60 kg × 6"), rest.lastSet)
        assertEquals(UpNext("RDL", "Set 2", listOf("6 reps", "75 kg"), Tempo("3010"), setCount = 2, target = "6 reps × 75 kg"), rest.upNext)
        assertTrue(rest.fromSheet)
        assertEquals(listOf(60, 90, 120, 150, 180), rest.presets.map { it.seconds })
        assertEquals(RestPreset(150, selected = true, fromSheet = true), rest.presets.single { it.selected })
        assertEquals("T2 · 0:30", "${rest.workoutName} · ${rest.elapsed}")
    }

    @Test
    fun adjustingChangesThisRestOnly() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))

        viewModel.onEvent(RestTimerEvent.Adjust(+15))
        viewModel.onEvent(RestTimerEvent.Adjust(+15))
        viewModel.onEvent(RestTimerEvent.Adjust(-15))
        passSeconds(1)

        assertEquals(165, viewModel.ready().totalSeconds)
        assertEquals(164, viewModel.ready().remainingSeconds)
    }

    @Test
    fun theAlarmIsSetForTheEndOfTheRest() = restTest {
        val loggedAt = clock.instant()
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))

        assertEquals(loggedAt.plusSeconds(150) to RestEndAlert.Both, alarm.scheduled)
        // Ticking doesn't set it again.
        passSeconds(5)
        assertEquals(1, alarm.history.size)

        viewModel.onEvent(RestTimerEvent.Adjust(+15))
        passSeconds(1)
        assertEquals(loggedAt.plusSeconds(165) to RestEndAlert.Both, alarm.scheduled)
    }

    @Test
    fun aRestThatIsAlreadyOverSetsNoAlarm() = restTest {
        val set = set("rdl", rdl.setGroups[0].id, 60.0, 6)
        clock.advance(200)

        rest(planned, set)

        assertEquals(null, alarm.scheduled)
    }

    @Test
    fun theAlertFollowsTheSetting() = restTest {
        rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))

        settings.update { it.copy(restEndAlert = RestEndAlert.Sound) }
        passSeconds(1)

        assertEquals(RestEndAlert.Sound, alarm.scheduled?.second)
    }

    @Test
    fun skippingRecordsHowLongTheRestReallyWasAndCancelsTheAlarm() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))
        passSeconds(40)

        viewModel.onEvent(RestTimerEvent.Skip)

        assertEquals(RestTimerNavigation.Done, viewModel.navigation.first())
        assertEquals(Duration.ofSeconds(40), logs.logs.value[session]!!.rests.values.single())
        assertNull(alarm.scheduled)
    }

    @Test
    fun undoRemovesTheSetAndReturns() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))

        viewModel.onEvent(RestTimerEvent.Undo)

        assertEquals(RestTimerNavigation.SetUndone, viewModel.navigation.first())
        assertTrue(logs.logs.value[session]!!.sets.isEmpty())
        assertNull(alarm.scheduled)
    }

    @Test
    fun nothingToTimeWithoutASet() = restTest {
        val viewModel = rest(planned)
        assertEquals(RestTimerNavigation.Done, viewModel.navigation.first())
    }

    @Test
    fun supersetLabels() = restTest {
        val a = exercise("a", "1a", "Biceps", group(sets = 2, rest = 45))
        val b = exercise("b", "1b", "Reverse Flies", group(sets = 2, reps = Reps.Count(10), load = Load.Bodyweight, rest = 45))
        val viewModel = rest(workout("W/1/T3", "T3", superset(1, a, b)), set("a", a.setGroups[0].id, 15.0, 11))

        val rest = viewModel.ready()
        assertEquals(LoggedSetInfo("1a · round 1", "15 kg × 11"), rest.lastSet)
        assertEquals(UpNext("Reverse Flies", "1b · round 1", listOf("10 reps"), null, target = "10 reps"), rest.upNext)
    }

    @Test
    fun pausingStopsTheRestAndItsAlarm() = restTest {
        val loggedAt = clock.instant()
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))
        passSeconds(30)

        viewModel.onEvent(RestTimerEvent.TogglePause)
        passSeconds(20)

        assertTrue(viewModel.ready().paused)
        assertEquals(120, viewModel.ready().remainingSeconds)
        assertNull(alarm.scheduled)

        viewModel.onEvent(RestTimerEvent.TogglePause)
        passSeconds(1)

        assertEquals(119, viewModel.ready().remainingSeconds)
        // The 20 s paused move the end.
        assertEquals(loggedAt.plusSeconds(170) to RestEndAlert.Both, alarm.scheduled)
    }

    @Test
    fun restartingTimesAFreshRestFromNow() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))
        passSeconds(100)
        viewModel.onEvent(RestTimerEvent.Adjust(+15))

        viewModel.onEvent(RestTimerEvent.Restart(90))
        passSeconds(1)

        val rest = viewModel.ready()
        assertEquals(90, rest.totalSeconds)
        assertEquals(89, rest.remainingSeconds)
        assertEquals(false, rest.fromSheet)
        assertEquals(90, rest.presets.single { it.selected }.seconds)
    }

    @Test
    fun aTypedLengthKeepsTheTimeAlreadyRested() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))
        passSeconds(30)

        viewModel.onEvent(RestTimerEvent.SetLength(60))
        passSeconds(1)

        assertEquals(60, viewModel.ready().totalSeconds)
        assertEquals(29, viewModel.ready().remainingSeconds)
    }

    @Test
    fun aWarmUpRestsForTheSavedTime() = restTest {
        warmUpRests.save("RDL", 45)
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 20.0, 10, warmUp = true))

        val rest = viewModel.ready()
        assertEquals(45, rest.totalSeconds)
        assertEquals(LoggedSetInfo("Warm-up 1", "20 kg × 10"), rest.lastSet)
        assertEquals(WarmUpRest(exerciseName = "RDL", useForAll = true), rest.warmUp)
        assertEquals(false, rest.fromSheet)
        assertEquals(listOf(30, 45, 60, 90, 120), rest.presets.map { it.seconds })
        // Warm-ups aren't counted: the first working set is still next.
        assertEquals("Set 1", rest.upNext?.label)
    }

    @Test
    fun anUnlimitedSetRestsForThePickedTime() = restTest {
        val stretch = exercise(
            "stretch", "1", "Rozciąganie",
            group(sets = null, rest = 150).copy(sets = SetCount.Unlimited),
        )
        setRests.save("Rozciąganie", 75)
        val viewModel = rest(workout("W/1/T4", "T4", single(stretch)), set("stretch", stretch.setGroups[0].id, null, 10))

        val rest = viewModel.ready()
        assertEquals(75, rest.totalSeconds)
        // Picked by the user, so not the sheet's, though the sheet has one.
        assertEquals(false, rest.fromSheet)
        assertTrue(rest.presets.none { it.fromSheet })
        assertNull(rest.warmUp)
    }

    @Test
    fun aWarmUpWithoutASavedTimeRestsAMinute() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 20.0, 10, warmUp = true))
        assertEquals(60, viewModel.ready().totalSeconds)
    }

    @Test
    fun aWarmUpTimeIsSavedForTheExerciseUnlessOnlyThisRestShouldChange() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 20.0, 10, warmUp = true))

        viewModel.onEvent(RestTimerEvent.SetLength(90))
        passSeconds(1)
        assertEquals(90, warmUpRests.rests.value["rdl"])
        assertEquals(90, viewModel.ready().totalSeconds)

        viewModel.onEvent(RestTimerEvent.UseForAllWarmUps(false))
        viewModel.onEvent(RestTimerEvent.SetLength(30))
        passSeconds(1)
        assertEquals(90, warmUpRests.rests.value["rdl"])
        assertEquals(30, viewModel.ready().totalSeconds)
    }

    @Test
    fun skippingAWarmUpRestGoesOnToTheWorkingSets() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 20.0, 10, warmUp = true))

        viewModel.onEvent(RestTimerEvent.Skip)

        assertEquals(RestTimerNavigation.StartWorkingSets, viewModel.navigation.first())
    }

    @Test
    fun anotherWarmUpEndsTheRestAndGoesBack() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 20.0, 10, warmUp = true))
        passSeconds(20)

        viewModel.onEvent(RestTimerEvent.AnotherWarmUp)

        assertEquals(RestTimerNavigation.Done, viewModel.navigation.first())
        assertEquals(Duration.ofSeconds(20), logs.logs.value[session]!!.rests.values.single())
    }

    @Test
    fun goingHomeLeavesTheRestRunning() = restTest {
        val viewModel = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))
        passSeconds(20)

        viewModel.onEvent(RestTimerEvent.Home)

        assertEquals(RestTimerNavigation.Home, viewModel.navigation.first())
        assertTrue(logs.logs.value[session]!!.rests.isEmpty())
        assertEquals(RestEndAlert.Both, alarm.scheduled?.second)
    }

    @Test
    fun aRestPicksUpWhereItWasWhenReopened() = restTest {
        val first = rest(planned, set("rdl", rdl.setGroups[0].id, 60.0, 6))
        passSeconds(10)
        first.onEvent(RestTimerEvent.Adjust(+15))
        first.onEvent(RestTimerEvent.TogglePause)
        passSeconds(1)

        val reopened = RestTimerViewModel(
            FakeActiveSessionRepository(clock, session),
            FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", planned))),
            logs, settings, warmUpRests, setRests, restClocks, alarm, clock,
        ).also { created += it }
        backgroundScope.launch { reopened.state.collect {} }
        passSeconds(30)

        val rest = reopened.ready()
        assertTrue(rest.paused)
        assertEquals(165, rest.totalSeconds)
        assertEquals(155, rest.remainingSeconds)
    }

    @Test
    fun anEndedRestHasNothingToTime() = restTest {
        val set = set("rdl", rdl.setGroups[0].id, 60.0, 6)
        session = ActiveSession(planned.id, clock.instant())
        logs.logSet(session, set)
        logs.recordRest(session, logs.logs.value.getValue(session).sets.single().id, clock.instant())
        val viewModel = RestTimerViewModel(
            FakeActiveSessionRepository(clock, session),
            FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", planned))),
            logs, settings, warmUpRests, setRests, restClocks, alarm, clock,
        ).also { created += it }

        assertEquals(RestTimerNavigation.Done, viewModel.navigation.first())
    }
}
