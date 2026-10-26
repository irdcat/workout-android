package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A phone-sized screen: the default is small enough to push buttons out of view.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class RestTimerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resting = RestTimerUiState(
        workoutName = "T2",
        elapsed = "14:51",
        remainingSeconds = 131,
        totalSeconds = 150,
        fromSheet = true,
        presets = listOf(60, 90, 120, 150, 180).map { RestPreset(it, selected = it == 150, fromSheet = it == 150) },
        lastSet = LoggedSetInfo(label = "Set 3", summary = "90 kg × 5"),
        upNext = UpNext(exerciseName = "RDL", label = "Set 4", targets = listOf("5 reps", "92.5 kg"), tempo = Tempo("3010"), setCount = 5),
    )

    private val actions = mutableListOf<RestTimerEvent>()

    private fun show(state: RestTimerUiState) = compose.setContent {
        WorkoutTheme {
            RestTimerScreen(state = state, onEvent = { actions += it })
        }
    }

    @Test
    fun showsTheRestAndWhatComesNext() {
        show(resting)

        compose.onNodeWithText("REST · AFTER SET 3").assertExists()
        compose.onNodeWithText("T2 · 14:51").assertExists()
        compose.onNodeWithText("RDL").assertExists()
        compose.onNodeWithText("Skip rest · start set 4").assertExists()
        compose.onNodeWithText("of 2:30 · from the sheet", useUnmergedTree = true).assertExists()
    }

    @Test
    fun buttonsReachTheirCallbacks() {
        show(resting)

        compose.onNodeWithContentDescription("Add 15 seconds").performClick()
        compose.onNodeWithContentDescription("Subtract 15 seconds").performClick()
        compose.onNodeWithText("Skip rest · start set 4").performClick()
        compose.onNodeWithContentDescription("Undo set 3").performClick()
        compose.onNodeWithContentDescription("Pause rest timer").performClick()
        compose.onNodeWithText("1:30").performClick()

        assertEquals(
            listOf(
                RestTimerEvent.Adjust(15),
                RestTimerEvent.Adjust(-15),
                RestTimerEvent.Skip,
                RestTimerEvent.Undo,
                RestTimerEvent.TogglePause,
                RestTimerEvent.Restart(90),
            ),
            actions,
        )
    }

    @Test
    fun pausedTheTimeCanBeTyped() {
        show(resting.copy(paused = true))

        compose.onNodeWithText("PAUSED · TYPE A TIME").assertExists()
        compose.onNodeWithText("2:11 left of 2:30").assertExists()
        compose.onNodeWithContentDescription("Rest time, minutes and seconds").performTextReplacement("3:00")
        compose.onNodeWithContentDescription("Resume rest timer").performClick()

        assertEquals(listOf(RestTimerEvent.SetLength(180), RestTimerEvent.TogglePause), actions)
    }

    @Test
    fun afterAWarmUpItsOwnTimeAndAnotherWarmUp() {
        show(
            resting.copy(
                fromSheet = false,
                totalSeconds = 60,
                remainingSeconds = 48,
                presets = listOf(30, 45, 60, 90, 120).map { RestPreset(it, selected = it == 60) },
                lastSet = LoggedSetInfo(label = "Warm-up 3", summary = "60 kg × 5"),
                upNext = UpNext(exerciseName = "RDL", label = "Set 1", targets = listOf("6 reps", "60 kg"), target = "6 × 60 kg"),
                warmUp = WarmUpRest(exerciseName = "RDL", useForAll = true),
            )
        )

        compose.onNodeWithText("REST · AFTER WARM-UP 3").assertExists()
        compose.onNodeWithText("RDL · working set 1").assertExists()
        compose.onNodeWithText("The sheet has no warm-up rest. Saved for RDL.").assertExists()
        compose.onNodeWithText("0:45").performClick()
        compose.onNodeWithContentDescription("Use this time for all warm-ups of this exercise").performClick()
        compose.onNodeWithText("Another warm-up").performClick()

        assertEquals(
            listOf(RestTimerEvent.SetLength(45), RestTimerEvent.UseForAllWarmUps(false), RestTimerEvent.AnotherWarmUp),
            actions,
        )
    }

    @Test
    fun withoutAWorkoutClockOnlyTheNameIsShown() {
        show(resting.copy(elapsed = null))
        compose.onNodeWithText("T2").assertExists()
    }

    @Test
    fun theMainButtonFollowsTheRest() {
        show(resting.copy(remainingSeconds = -5))
        compose.onNodeWithText("Start set 4").assertExists()
    }

    @Test
    fun theLastSetOfTheWorkoutEndsTheRest() {
        show(resting.copy(upNext = null))
        compose.onNodeWithText("End rest").assertExists()
    }

    @Test
    fun aWarmUpTimeForThisRestOnly() {
        show(
            resting.copy(
                lastSet = LoggedSetInfo(label = "Warm-up 1", summary = "20 kg × 10"),
                warmUp = WarmUpRest(exerciseName = "RDL", useForAll = false),
            )
        )
        compose.onNodeWithText("The sheet has no warm-up rest. Only this rest changes.").assertExists()
    }

    @Test
    fun overTimeShowsHowFarOver() {
        show(resting.copy(remainingSeconds = -12))
        compose.onNodeWithText("+0:12", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("over 2:30 rest", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("12 seconds over the rest time").assertExists()
    }

    @Test
    fun backGoesHomeWithTheTimerRunning() {
        show(resting)

        compose.onNodeWithContentDescription("Back to home, the timer keeps running").performClick()

        assertEquals(listOf(RestTimerEvent.Home), actions)
    }
}
