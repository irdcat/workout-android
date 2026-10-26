package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
class WarmUpScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val third = WarmUpUiState(
        workoutName = "T2",
        weekRange = "1 – 7 Jun",
        elapsed = "11:36",
        position = 2,
        exerciseCount = 7,
        exerciseName = "RDL",
        plannedSets = 5,
        done = listOf("20×10", "40×8"),
        number = 3,
        entry = WarmUpEntry(weight = "60", weightStep = "5", reps = "5", rest = "1:00"),
        lastTime = LastTime(date = "25 May", summary = "20×10, 40×8, 60×5", note = null),
    )

    private val events = mutableListOf<WarmUpEvent>()

    private fun show(state: WarmUpUiState) = compose.setContent {
        WorkoutTheme { WarmUpScreen(state = state, onEvent = { events += it }) }
    }

    @Test
    fun showsTheWarmUpApartFromThePlan() {
        show(third)

        compose.onNodeWithText("EXERCISE 2 OF 7 · WARM-UP").assertExists()
        compose.onNodeWithText("RDL").assertExists()
        compose.onNodeWithText("Warm-up sets are separate from the 5 prescribed sets. They aren't counted and aren't written to the sheet.").assertExists()
        compose.onNodeWithText("WARM-UP DONE · KG × REPS").assertExists()
        compose.onNodeWithText("40×8").assertExists()
        compose.onNodeWithText("WARM-UP 3").assertExists()
        compose.onNodeWithText("20×10, 40×8, 60×5", substring = true).assertExists()
    }

    @Test
    fun theFirstWarmUpForOpenSetsWithNothingLastTime() {
        show(third.copy(plannedSets = null, done = emptyList(), number = 1, lastTime = null))

        compose.onNodeWithText("Warm-up sets are separate from the prescribed sets.", substring = true).assertExists()
        compose.onNodeWithText("WARM-UP DONE · KG × REPS").assertDoesNotExist()
        compose.onNodeWithText("LAST TIME", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Log warm-up 1").assertExists()
    }

    @Test
    fun controlsReachTheirEvents() {
        show(third)

        compose.onNodeWithContentDescription("Increase weight").performClick()
        compose.onNodeWithContentDescription("Decrease reps").performClick()
        compose.onNodeWithContentDescription("Weight step 2.5").performClick()
        compose.onNodeWithContentDescription("Add 15 seconds").performClick()
        compose.onNodeWithContentDescription("Subtract 15 seconds").performClick()
        compose.onNodeWithContentDescription("Rest after").performTextReplacement("1:30")
        compose.onNodeWithContentDescription("Weight").performTextReplacement("62.5")
        compose.onNodeWithContentDescription("Reps").performTextReplacement("4")
        compose.onNodeWithText("Log warm-up 3").performClick()
        compose.onNodeWithText("Start working sets").performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.onNode(hasText("Finish workout") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithContentDescription("Back to working sets").performClick()

        // The state here never changes, so each typed field also echoes its old value back: leave those out.
        val expected = listOf(
            WarmUpEvent.AdjustWeight(+1),
            WarmUpEvent.AdjustReps(-1),
            WarmUpEvent.WeightStepSelected("2.5"),
            WarmUpEvent.AdjustRest(+1),
            WarmUpEvent.AdjustRest(-1),
            WarmUpEvent.RestChanged("1:30"),
            WarmUpEvent.WeightChanged("62.5"),
            WarmUpEvent.RepsChanged("4"),
            WarmUpEvent.LogWarmUp,
            WarmUpEvent.StartWorkingSets,
            WarmUpEvent.FinishWorkout,
            WarmUpEvent.Back,
        )
        assertEquals(expected, events.filter { it in expected })
    }
}
