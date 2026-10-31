package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.RestClocks
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.SyncResult
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.infrastructure.google.SheetFixtures
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeSetRestRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWarmUpRestRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.MutableClock
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.loggedSet
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val plan = FakeWorkoutPlanRepository(SheetFixtures.months())

    private val settings = FakeSettingsRepository()
    private val logs = FakeSessionLogRepository()
    private val restClocks = RestClocks()

    private fun TestScope.home(clock: Clock, session: ActiveSession? = null): Pair<HomeViewModel, FakeActiveSessionRepository> {
        val sessions = FakeActiveSessionRepository(clock, session)
        val viewModel = HomeViewModel(
            plan, sessions, logs, clock, settings, SyncWorkoutPlan(plan, sessions, FakeResultRepository(), settings),
            FakeWarmUpRestRepository(), FakeSetRestRepository(), restClocks,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel to sessions
    }

    private fun HomeViewModel.ready(): HomeUiState = (state.value as Loadable.Ready).value

    @Test
    fun showsThisWeeksPlan() = runTest(main.dispatcher) {
        val (viewModel, _) = home(fixedClock("2026-06-03"))
        val home = viewModel.ready()

        assertEquals("June 2026", home.monthLabel)
        assertEquals("1 – 7 Jun", home.weekRange)
        val content = home.content as HomeContent.Plan
        assertEquals(listOf("T1", "T2", "T3"), content.workouts.map { it.name })
        assertTrue(content.workouts.all { it.status == WorkoutStatus.Completed(day = null) })
        assertEquals(null, home.activeSession)
    }

    @Test
    fun noPlanPointsAtTheNewestWeek() = runTest(main.dispatcher) {
        val (viewModel, _) = home(fixedClock("2026-10-21"))
        val home = viewModel.ready()

        assertEquals("19 – 25 Oct", home.weekRange)
        assertEquals(
            HomeContent.NoPlan(
                weekStart = "19 Oct",
                newestSheet = "WRZESIEŃ",
                newestSheetEnd = "20 Sep",
                latestWeekRange = "14 – 20 Sep",
            ),
            home.content,
        )
    }

    @Test
    fun restWeek() = runTest(main.dispatcher) {
        val (viewModel, _) = home(fixedClock("2026-05-06"))
        assertEquals(HomeContent.RestWeek, viewModel.ready().content)
    }

    @Test
    fun syncsOnFirstLaunch() = runTest(main.dispatcher) {
        plan.months.value = emptyList()
        plan.syncInfo.value = plan.syncInfo.value.copy(lastSyncedAt = null)
        plan.monthsAfterSync = SheetFixtures.months()

        val (viewModel, _) = home(fixedClock("2026-06-03"))

        assertEquals(1, plan.syncCalls)
        assertTrue(viewModel.ready().content is HomeContent.Plan)
    }

    @Test
    fun failedRefreshWithoutConnectionShowsOffline() = runTest(main.dispatcher) {
        val (viewModel, _) = home(fixedClock("2026-06-03"))
        plan.syncResult = SyncResult.Failed(IOException("no network"))

        viewModel.onRefresh()

        val home = viewModel.ready()
        assertTrue(home.sync is SyncStatus.Offline)
        assertEquals(false, home.isRefreshing)
    }

    @Test
    fun startingAWorkoutOpensASession() = runTest(main.dispatcher) {
        val (viewModel, sessions) = home(fixedClock("2026-06-03"))

        viewModel.onStartWorkout("CZERWIEC2026/1/T3")

        assertEquals("CZERWIEC2026/1/T3", sessions.session.value?.workoutId)
        assertEquals(HomeNavigation.SessionStarted, viewModel.navigation.first())
    }

    @Test
    fun activeSessionShowsItsCardAndClock() = runTest(main.dispatcher) {
        val clock = fixedClock("2026-06-03")
        val started = clock.instant().minus(Duration.ofMinutes(12).plusSeconds(4))
        val (viewModel, _) = home(clock, ActiveSession("CZERWIEC2026/1/T2", started))

        val home = viewModel.ready()
        val content = home.content as HomeContent.Plan
        val card = home.activeSession!!
        assertEquals("T2", card.workoutName)
        assertEquals("12:04", card.elapsed)
        assertEquals(WorkoutStatus.InProgress, content.workouts.first { it.name == "T2" }.status)
    }

    @Test
    fun sessionCardHidesTheClockWhenTimeIsNotMeasured() = runTest(main.dispatcher) {
        settings.state.value = AppSettings(measureWorkoutTime = false)
        val clock = fixedClock("2026-06-03")
        val (viewModel, _) = home(clock, ActiveSession("CZERWIEC2026/1/T2", clock.instant()))

        val card = viewModel.ready().activeSession!!
        assertEquals("T2", card.workoutName)
        assertEquals(null, card.elapsed)
    }

    @Test
    fun sessionCardStaysOnWeeksWithoutAPlan() = runTest(main.dispatcher) {
        val clock = fixedClock("2026-10-21")
        val (noPlan, _) = home(clock, ActiveSession("WRZESIEŃ2026/3/T1", clock.instant()))
        assertTrue(noPlan.ready().content is HomeContent.NoPlan)
        assertEquals("T1", noPlan.ready().activeSession?.workoutName)

        val restClock = fixedClock("2026-05-06")
        val (restWeek, _) = home(restClock, ActiveSession("CZERWIEC2026/1/T2", restClock.instant()))
        assertEquals(HomeContent.RestWeek, restWeek.ready().content)
        assertEquals("T2", restWeek.ready().activeSession?.workoutName)
    }

    @Test
    fun refreshEndsASessionWhoseWorkoutLeftTheSheet() = runTest(main.dispatcher) {
        val clock = fixedClock("2026-06-03")
        val (viewModel, sessions) = home(clock, ActiveSession("CZERWIEC2026/1/T2", clock.instant()))
        assertEquals("T2", viewModel.ready().activeSession?.workoutName)

        // The sheet no longer has June.
        plan.monthsAfterSync = SheetFixtures.months().filterNot { it.month.monthValue == 6 }
        viewModel.onRefresh()

        assertEquals(null, sessions.session.value)
        assertEquals(null, viewModel.ready().activeSession)
    }

    private suspend fun logFirstSetOfT2(session: ActiveSession, at: Instant): Int {
        val workout = plan.observeWorkout(session.workoutId).first()!!
        val exercise = workout.exercises.first { e -> e.setGroups.first().restSeconds != null }
        val group = exercise.setGroups.first()
        logs.logSet(session, loggedSet(exercise.id, group.id, kg = null, reps = 10, at = at))
        return group.restSeconds!!
    }

    @Test
    fun aRunningRestShowsOnTheCardUntilItRunsOut() = runTest(main.dispatcher) {
        val clock = MutableClock(fixedClock("2026-06-03").instant())
        val session = ActiveSession("CZERWIEC2026/1/T2", clock.instant())
        val rest = logFirstSetOfT2(session, clock.instant())
        val (viewModel, _) = home(clock, session)

        clock.advance(30)
        testScheduler.advanceTimeBy(30_000)
        testScheduler.runCurrent()
        assertEquals(ActiveRest(left = formatRestTime(rest - 30)), viewModel.ready().activeSession!!.rest)

        clock.advance(rest.toLong())
        testScheduler.advanceTimeBy(rest * 1_000L)
        testScheduler.runCurrent()
        assertNull(viewModel.ready().activeSession!!.rest)
    }

    @Test
    fun aPausedRestShowsAsPaused() = runTest(main.dispatcher) {
        val clock = MutableClock(fixedClock("2026-06-03").instant())
        val session = ActiveSession("CZERWIEC2026/1/T2", clock.instant())
        val rest = logFirstSetOfT2(session, clock.instant())
        val setId = logs.logs.value.getValue(session).sets.single().id
        restClocks.update(setId) { it.copy(pause = it.pause.toggled(clock.instant())) }
        val (viewModel, _) = home(clock, session)

        clock.advance(600)
        testScheduler.advanceTimeBy(600_000)
        testScheduler.runCurrent()

        assertEquals(ActiveRest(left = formatRestTime(rest), paused = true), viewModel.ready().activeSession!!.rest)
    }

    @Test
    fun anEndedRestIsNotShown() = runTest(main.dispatcher) {
        val clock = fixedClock("2026-06-03")
        val session = ActiveSession("CZERWIEC2026/1/T2", clock.instant())
        logFirstSetOfT2(session, clock.instant())
        val setId = logs.logs.value.getValue(session).sets.single().id
        logs.recordRest(session, setId, clock.instant())

        val (viewModel, _) = home(clock, session)

        assertNull(viewModel.ready().activeSession!!.rest)
    }

    @Test
    fun afterAnExercisesLastSetTheCardAsksForAReviewAndShowsNoRest() = runTest(main.dispatcher) {
        val clock = fixedClock("2026-06-03")
        val session = ActiveSession("CZERWIEC2026/1/T2", clock.instant())
        val workout = plan.observeWorkout(session.workoutId).first()!!
        val exercise = workout.exercises.first { e -> e.setGroups.all { it.sets is SetCount.Fixed } }
        exercise.setGroups.forEach { group ->
            repeat((group.sets as SetCount.Fixed).count) {
                logs.logSet(session, loggedSet(exercise.id, group.id, kg = null, reps = 5, at = clock.instant()))
            }
        }

        val (viewModel, _) = home(clock, session)

        val card = viewModel.ready().activeSession!!
        assertEquals(exercise.name, card.exercise)
        assertTrue(card.reviewing)
        assertNull(card.rest)
    }
}
