package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.SavedStateHandle
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkoutDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val done = workout(
        "W/1/T1", "T1",
        single(exercise("t1-1", "1", "Przysiad", group(result = "RPE 9"))),
    )
    private val notStarted = workout(
        "W/1/T2", "T2",
        single(exercise("t2-1", "1", "RDL", group())),
    )
    private val other = workout(
        "W/1/T3", "T3",
        single(exercise("t3-1", "1", "Bench Press", group(result = "RPE 8"))),
        single(exercise("t3-2", "2", "Wykroki", group())),
    )
    private val plan = FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", done, notStarted, other)))

    private fun TestScope.detail(
        workoutId: String,
        today: String = "2026-06-03",
        session: ActiveSession? = null,
    ): Pair<WorkoutDetailViewModel, FakeActiveSessionRepository> {
        val clock = fixedClock(today)
        val sessions = FakeActiveSessionRepository(clock, session)
        val viewModel = WorkoutDetailViewModel(
            SavedStateHandle(mapOf(WORKOUT_ID_ARG to workoutId)), plan, sessions, FakeSessionLogRepository(), clock,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel to sessions
    }

    private fun WorkoutDetailViewModel.ready(): WorkoutDetailUiState = (state.value as Loadable.Ready).value

    @Test
    fun completedWorkout() = runTest(main.dispatcher) {
        val (viewModel, _) = detail("W/1/T1")
        val detail = viewModel.ready()
        assertEquals("1 – 7 Jun", detail.weekRange)
        assertEquals(WorkoutStatus.Completed(day = null), detail.status)
        assertEquals(WorkoutDetailFooter.Completed(on = null), detail.footer)
    }

    @Test
    fun footerDependsOnTheWeekAndTheActiveSession() = runTest(main.dispatcher) {
        assertEquals(WorkoutDetailFooter.Start, detail("W/1/T2").first.ready().footer)
        assertEquals(
            WorkoutDetailFooter.ViewOnly("Past week · view only"),
            detail("W/1/T2", today = "2026-06-20").first.ready().footer,
        )
        assertEquals(
            WorkoutDetailFooter.ViewOnly("Upcoming · view only"),
            detail("W/1/T2", today = "2026-05-20").first.ready().footer,
        )
        val anotherActive = ActiveSession("W/1/T3", fixedClock("2026-06-03").instant())
        assertEquals(
            WorkoutDetailFooter.ViewOnly("Finish T3 to start"),
            detail("W/1/T2", session = anotherActive).first.ready().footer,
        )
    }

    @Test
    fun activeWorkoutResumesAndMarksTheCurrentExercise() = runTest(main.dispatcher) {
        val session = ActiveSession("W/1/T3", fixedClock("2026-06-03").instant())
        val detail = detail("W/1/T3", session = session).first.ready()

        assertEquals(WorkoutDetailFooter.Resume, detail.footer)
        assertEquals(WorkoutStatus.InProgress, detail.status)
        val progress = detail.items.map { (it as WorkoutItem.Single).exercise.progress }
        assertEquals(listOf(ExerciseProgress.Done, ExerciseProgress.Current(set = 1, setCount = 1)), progress)
    }

    @Test
    fun startOpensASession() = runTest(main.dispatcher) {
        val (viewModel, sessions) = detail("W/1/T2")

        viewModel.onStartWorkout()

        assertEquals("W/1/T2", sessions.session.value?.workoutId)
        assertEquals(WorkoutDetailNavigation.SessionStarted, viewModel.navigation.first())
    }
}
