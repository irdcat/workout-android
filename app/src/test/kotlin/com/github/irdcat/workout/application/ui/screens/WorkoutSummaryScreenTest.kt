package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class WorkoutSummaryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val summary = WorkoutSummaryUiState(
        workoutName = "T2",
        dateLine = "Tue 2 Jun · 17:42 – 18:41",
        totalSeconds = 59 * 60,
        workingSeconds = 20 * 60,
        restingSeconds = 39 * 60,
        previousSeconds = 63 * 60,
        avgRestSeconds = 125,
        exercisesDone = 2,
        exercisesTotal = 2,
        setsDone = 7,
        setsTotal = 7,
        results = listOf(
            SummaryResult("squat", "1 Przysiad", "RPE 9", setsDone = 3, setsTotal = 3),
            SummaryResult("a", "2a Biceps", null, setsDone = 4, setsTotal = 4),
        ),
        sheetName = "CZERWIEC",
    )

    private val actions = mutableListOf<String>()

    private fun show(state: WorkoutSummaryUiState) = compose.setContent {
        WorkoutTheme {
            WorkoutSummaryScreen(
                state = state,
                onEditResult = { actions += "edit $it" },
                onSave = { actions += "save" },
                onBackToSession = { actions += "back" },
            )
        }
    }

    @Test
    fun aCompleteWorkout() {
        show(summary)

        compose.onNodeWithText("WORKOUT COMPLETE").assertExists()
        // The total is read out in words.
        compose.onNodeWithContentDescription("59 minutes").assertExists()
        compose.onNodeWithText("4 min shorter than last week's T2 (1:03:00)").assertExists()
        compose.onNodeWithText("2:05").assertExists()
        compose.onNodeWithText("1 result will be written to CZERWIEC.", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun resultsCanBeEditedOrAdded() {
        show(summary)

        compose.onNodeWithText("RPE 9").assertExists()
        compose.onNodeWithText("No result yet").assertExists()
        compose.onNodeWithContentDescription("Edit result for 1 Przysiad").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Add result for 2a Biceps").performScrollTo().performClick()
        compose.onNodeWithText("Save results").performClick()
        compose.onNodeWithText("Back to session").performClick()

        assertEquals(listOf("edit squat", "edit a", "save", "back"), actions)
    }

    @Test
    fun endedEarlyWithoutTimingOrWritingToTheSheet() {
        show(summary.copy(setsDone = 3, totalSeconds = null, avgRestSeconds = null, writesToSheet = false))

        compose.onNodeWithText("ENDED EARLY").assertExists()
        compose.onNodeWithContentDescription("59 minutes").assertDoesNotExist()
        compose.onNodeWithText("—").assertExists()
        compose.onNodeWithText("Results stay on this device.", substring = true).performScrollTo().assertExists()
    }
}
