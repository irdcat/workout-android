package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor's content; the bottom sheet around it is Material's own.
 * Phone-sized: the default screen is small enough to push buttons out of view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class ResultEditorSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val editor = ResultEditorUiState(
        exerciseLabel = "2",
        exerciseName = "RDL",
        targets = listOf("Last row · 2×5 92.5 kg", "First row · 1×6 60 kg"),
        selectedTarget = 0,
        text = "",
        suggestions = listOf("92.5×5"),
        lastTime = LastResult(date = "25 May", text = "RPE 8, easy"),
    )

    private val events = mutableListOf<ResultEditorEvent>()

    private fun show(state: ResultEditorUiState = editor) = compose.setContent {
        WorkoutTheme { ResultEditorContent(state = state, onEvent = { events += it }) }
    }

    @Test
    fun showsTheExerciseAndWhereTheResultGoes() {
        show()

        compose.onNodeWithText("RESULT · EXERCISE 2").assertExists()
        compose.onNodeWithText("Last row · 2×5 92.5 kg").assertExists()
        compose.onNodeWithText("“RPE 8, easy”").assertExists()
        compose.onNodeWithText("LAST TIME · 25 MAY").assertExists()
    }

    @Test
    fun aResultOnlyFromTheSheetHasNoDate() {
        show(editor.copy(lastTime = LastResult(date = null, text = "RPE 8")))
        compose.onNodeWithText("LAST TIME").assertExists()
    }

    @Test
    fun everyControlSendsItsEvent() {
        show()

        compose.onNodeWithText("Last row · 2×5 92.5 kg").performClick()
        compose.onNodeWithText("First row · 1×6 60 kg").performClick()
        compose.onNodeWithText("92.5×5").performClick()
        compose.onNodeWithContentDescription("Increase RPE").performClick()
        compose.onNodeWithContentDescription("Decrease RPE").performClick()
        compose.onNodeWithText("Use").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Save result").performClick()
        compose.onNodeWithContentDescription("Close without saving").performClick()

        assertEquals(
            listOf(
                ResultEditorEvent.TargetSelected(1),
                ResultEditorEvent.Insert("92.5×5"),
                ResultEditorEvent.AdjustRpe(+1),
                ResultEditorEvent.AdjustRpe(-1),
                ResultEditorEvent.UseLastTime,
                ResultEditorEvent.Clear,
                ResultEditorEvent.Save,
                ResultEditorEvent.Dismiss,
            ),
            events,
        )
    }

    @Test
    fun typingSendsTheText() {
        show()
        compose.onNodeWithText("YOUR RESULT · ANY TEXT").assertExists()
        compose.onNodeWithText("RPE · REQUIRED").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("easy")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("9")

        // The state isn't fed back here, so leaving the text field may also send its old, empty text.
        assertEquals(ResultEditorEvent.TextChanged("easy"), events.first())
        assertEquals(ResultEditorEvent.RpeChanged("9"), events.last())
    }

    @Test
    fun aResultWithoutAnRpeCantBeSaved() {
        show(editor.copy(text = "92.5×5"))
        compose.onNodeWithText("Save result").assertIsNotEnabled()
    }

    @Test
    fun showsTheRpeTarget() {
        show(editor.copy(rpe = "9", rpeTarget = "8"))
        compose.onNodeWithText("Plan: RPE 8").assertExists()
    }

    @Test
    fun anRpeAsPlannedIsLeftOutOfTheResult() {
        show(editor.copy(rpe = "8", rpeTarget = "8"))
        compose.onNodeWithText("As planned (RPE 8): not added to the result").assertExists()
        compose.onNodeWithText("Save result").assertIsEnabled()
    }

    @Test
    fun aSingleTargetCantBeChanged() {
        show(editor.copy(targets = listOf("Last row · 2×5 92.5 kg"), suggestions = emptyList(), lastTime = null))

        compose.onNodeWithText("Last row · 2×5 92.5 kg").performClick()
        compose.onNodeWithText("INSERT FROM THIS SESSION").assertDoesNotExist()
        compose.onNodeWithText("Use").assertDoesNotExist()
        assertEquals(emptyList<ResultEditorEvent>(), events)
    }
}
