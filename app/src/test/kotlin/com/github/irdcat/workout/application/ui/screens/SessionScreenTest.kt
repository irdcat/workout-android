package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import com.github.irdcat.workout.domain.session.AbandonSession
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.StopwatchPauses
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeSetRestRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.RecordingRestAlarm
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.superset
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Session screen driven by its real view model, with the repositories faked.
 * Phone-sized: the default screen is small enough to push buttons out of view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class SessionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val clock = fixedClock("2026-06-03")
    private val rdl = exercise(
        "rdl", "1", "RDL",
        group(reps = Reps.Count(6), load = Load.Weight(60.0)),
        group(sets = 2, reps = Reps.Count(5), load = Load.Weight(90.0)),
    )
    private val pull = exercise("pull", "2", "Podciąganie", group(sets = 2))
    private val planned = workout("W/1/T2", "T2", single(rdl), single(pull))
    private val logs = FakeSessionLogRepository()

    private lateinit var session: ActiveSession
    private lateinit var viewModel: SessionViewModel

    @After
    fun endViewModel() = viewModel.viewModelScope.cancel()

    /** [earlier] weeks come before this one, for "last time". */
    private fun show(workout: Workout = planned, earlier: List<TrainingWeek> = emptyList()) {
        session = ActiveSession(workout.id, clock.instant())
        // Rest is off so logging stays on this screen; the clock is off so nothing ticks.
        val plan = FakeWorkoutPlanRepository(months(*earlier.toTypedArray(), week("w1", "2026-06-01", workout)))
        val sessions = FakeActiveSessionRepository(clock, session)
        viewModel = SessionViewModel(
            sessions,
            plan,
            logs,
            clock,
            FakeSettingsRepository(AppSettings(autoStartRestTimer = false, measureWorkoutTime = false)),
            FindLastTime(plan, logs),
            StopwatchPauses(),
            AbandonSession(sessions, logs, FakeResultRepository(), RecordingRestAlarm()),
            FakeSetRestRepository(),
        )
        compose.setContent { content() }
    }

    @Composable
    private fun content() {
        WorkoutTheme {
            val state by viewModel.state.collectAsStateWithLifecycle()
            (state as? Loadable.Ready)?.let { SessionScreen(state = it.value, onEvent = viewModel::onEvent) }
        }
    }

    /** Presses [label] in the confirmation dialog that's showing. */
    private fun confirm(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    private fun logged(): List<String> = logs.logs.value[session]!!.sets.map { it.exerciseId }

    @Test
    fun loggingASetMovesToTheNextOne() {
        show()
        compose.onNodeWithText("Log set 1").performClick()

        compose.onNodeWithText("Log set 2").assertExists()
        // The entry is reset to the next set's target.
        compose.onNodeWithContentDescription("Weight").assertTextEquals("90")
        assertEquals(listOf(60.0), logs.logs.value[session]!!.sets.map { it.weightKg })
    }

    @Test
    fun theSteppersChangeWhatIsLogged() {
        show()
        compose.onNodeWithContentDescription("Increase weight").performClick()
        compose.onNodeWithContentDescription("Decrease reps").performClick()
        compose.onNodeWithText("Log set 1").performClick()

        val logged = logs.logs.value[session]!!.sets.single()
        assertEquals(62.5, logged.weightKg)
        assertEquals(5, logged.reps)
    }

    @Test
    fun finishingTheExerciseMovesOn() {
        show()
        compose.onNodeWithText("Finish exercise").performClick()
        confirm("Finish exercise")

        compose.onNodeWithContentDescription("Exercise 2 of 2").assertExists()
        compose.onNodeWithText("Log set 1").assertExists()
    }

    // ---- Superset ----------------------------------------------------------

    private val biceps = exercise("a", "1a", "Biceps", group(sets = 2, reps = Reps.Count(12), load = Load.Weight(15.0), rest = 45))
    private val flies = exercise("b", "1b", "Reverse Flies", group(sets = 1, reps = Reps.Count(10), load = Load.Bodyweight, rest = 45))
    private val withSuperset = workout("W/1/T3", "T3", superset(1, biceps, flies), single(pull))

    @Test
    fun anUnevenSupersetGoesRoundByRound() {
        show(withSuperset)

        compose.onNodeWithText("SUPERSET 1").assertExists()
        compose.onNodeWithContentDescription("Order of sets: 1a 1b, 1a").assertExists()
        compose.onNodeWithText("ROUND 1 · 1a").assertExists()
        compose.onNodeWithText("Biceps").assertExists()

        compose.onNodeWithText("Log 1a · round 1").performClick()
        compose.onNodeWithText("Reverse Flies").assertExists()
        // Logged, and 1a again as the next set after 1b.
        compose.onNodeWithText("DONE · 1 SET").assertExists()
        compose.onNodeWithText("1a R1").assertExists()
        compose.onNodeWithText("1a R2").assertExists()
        compose.onAllNodesWithText("15 kg × 12").assertCountEquals(2)

        compose.onNodeWithText("Log 1b · round 1").performClick()
        // 1b has no second set, so round 2 is 1a alone.
        compose.onNodeWithText("Log 1a · round 2").performClick()
        assertEquals(listOf("a", "b", "a"), logged())

        // Every set logged: the results are reviewed before moving on.
        compose.onNodeWithText("1A · BICEPS").assertExists()
        compose.onNodeWithText("Review result").assertExists()
        compose.onNodeWithText("Log", substring = true).assertDoesNotExist()

        // Reviewing (in the result editor) finishes each member.
        runBlocking { logs.finishExercises(session, listOf("a", "b")) }
        // The progress bar has a segment per exercise: both members, then Podciąganie.
        compose.onNodeWithContentDescription("Exercise 3 of 3").assertExists()
    }

    @Test
    fun finishingASupersetFinishesEveryMember() {
        show(withSuperset)

        compose.onNodeWithText("Finish exercise").performClick()
        confirm("Finish exercise")

        compose.onNodeWithContentDescription("Exercise 3 of 3").assertExists()
        compose.onNodeWithText("Log set 1").assertExists()
    }

    // ---- Open sets ---------------------------------------------------------

    private val pyramid = exercise("pyramid", "1", "Przysiad piramidka", group(sets = null, reps = Reps.Count(5), rest = null))
    private val withOpenSets = workout("W/1/T4", "T4", single(pyramid), single(pull))

    @Test
    fun openSetsKeepGoingUntilFinished() {
        show(withOpenSets)

        compose.onNodeWithText("Open sets").assertExists()
        // No rest in the sheet: it's picked on the set card.
        compose.onNodeWithText("Your time: it starts from the one you picked last.").assertExists()
        // The stopwatch starts with the first set.
        compose.onNodeWithText("since first set").assertDoesNotExist()

        repeat(3) { compose.onNodeWithText("Log set ${it + 1}").performClick() }
        compose.onNodeWithText("SET 4").assertExists()
        compose.onNodeWithText("since first set").assertExists()
        compose.onNodeWithText("DONE · 3 SETS").assertExists()

        compose.onNodeWithText("Finish exercise").performClick()
        confirm("Finish exercise")
        // The result is reviewed first; saving it finishes the exercise.
        compose.onNodeWithContentDescription("Exercise 1 of 2").assertExists()
        runBlocking { logs.finishExercises(session, listOf("pyramid")) }
        compose.onNodeWithContentDescription("Exercise 2 of 2").assertExists()
        assertEquals(List(3) { "pyramid" }, logged())
    }

    // ---- Last time and the stopwatch ----------------------------------------------

    @Test
    fun lastTimeFromTheSheetShowsItsResultWithoutADate() {
        val before = week("w0", "2026-05-25", workout("W/0/T2", "T2", single(exercise("rdl-before", "1", "RDL", group(result = "RPE 8")))))
        show(earlier = listOf(before))

        compose.onNodeWithText("LAST TIME  “RPE 8”").assertExists()
    }

    @Test
    fun theStopwatchCanBePaused() {
        show(withOpenSets)
        compose.onNodeWithText("Log set 1").performClick()

        compose.onNodeWithContentDescription("Pause stopwatch").performClick()
        compose.onNodeWithContentDescription("Resume stopwatch").performClick()
        compose.onNodeWithContentDescription("Pause stopwatch").assertExists()
    }
}
