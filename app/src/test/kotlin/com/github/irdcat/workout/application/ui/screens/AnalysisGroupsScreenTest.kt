package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class AnalysisGroupsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<AnalysisGroupsEvent>()

    private val state = AnalysisGroupsUiState(
        groups = listOf(GroupEditor("Squat", listOf("Squat", "Przysiad")), GroupEditor("Bench", listOf("Bench Press"))),
        isDefault = false,
    )

    private fun show(state: AnalysisGroupsUiState = this.state) = compose.setContent {
        WorkoutTheme { AnalysisGroupsScreen(state = state, onEvent = { events += it }) }
    }

    @Test
    fun exercisesAreAddedAndRemovedInTheirGroup() {
        show()

        compose.onNodeWithContentDescription("Remove Przysiad from Squat").performClick()
        compose.onNodeWithContentDescription("Add to Bench").performClick()
        compose.onNodeWithContentDescription("Move Squat up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Squat down").performClick()

        assertEquals(
            listOf(
                AnalysisGroupsEvent.RemoveExercise("Squat", "Przysiad"),
                AnalysisGroupsEvent.AddExercise("Bench"),
                AnalysisGroupsEvent.MoveGroup("Squat", 1),
            ),
            events,
        )
    }

    // The name dialog isn't tested here: a text field in an AlertDialog never lets Compose idle under
    // Robolectric. The rule it applies, isFreeGroupName, is tested in ExerciseGroupTest.

    @Test
    fun removingAGroupAndResettingAskFirst() {
        show()

        compose.onNodeWithContentDescription("Remove Bench").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Reset to defaults").performScrollTo().performClick()
        compose.onAllNodesWithText("Reset").onLast().performClick()

        assertEquals(listOf(AnalysisGroupsEvent.RemoveGroup("Bench"), AnalysisGroupsEvent.ResetToDefaults), events)
    }

    @Test
    fun theDefaultsHaveNothingToReset() {
        show(state.copy(isDefault = true))
        compose.onNodeWithText("Reset to defaults").assertDoesNotExist()
    }
}
