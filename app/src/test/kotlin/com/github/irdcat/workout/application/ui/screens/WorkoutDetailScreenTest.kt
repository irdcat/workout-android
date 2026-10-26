package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
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
class WorkoutDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val squat = ExerciseDetail(
        id = "squat", name = "Przysiad",
        setGroups = listOf(SetGroup(1, "5", "60 kg"), SetGroup(2, "5", "90 kg")),
        restSeconds = 180, result = "RPE 9",
    )
    private val rdl = ExerciseDetail("rdl", "RDL", listOf(SetGroup(3, "6", "75 kg")), restSeconds = 150)
    private val biceps = ExerciseDetail("a", "Biceps", listOf(SetGroup(3, "12", "15 kg")), restSeconds = 45)
    private val flies = ExerciseDetail("b", "Reverse Flies", listOf(SetGroup(2, "10", "bw")), restSeconds = 45)

    private val detail = WorkoutDetailUiState(
        weekRange = "1 – 7 Jun",
        workoutName = "T2",
        status = WorkoutStatus.NotStarted,
        items = listOf(
            WorkoutItem.Single(1, squat),
            WorkoutItem.Single(2, rdl),
            WorkoutItem.Superset(3, listOf(biceps, flies)),
        ),
        footer = WorkoutDetailFooter.Start,
    )

    private val actions = mutableListOf<String>()

    private fun show(state: WorkoutDetailUiState) = compose.setContent {
        WorkoutTheme {
            WorkoutDetailScreen(
                state = state,
                onBack = { actions += "back" },
                onEditResult = { actions += "edit $it" },
                onResumeSession = { actions += "resume" },
                onStartWorkout = { actions += "start" },
            )
        }
    }

    @Test
    fun showsEveryExerciseWithItsSetGroups() {
        show(detail)

        compose.onNodeWithText("T2").assertExists()
        compose.onNodeWithText("4 exercises").assertExists()
        compose.onNodeWithText("Not started").assertExists()
        compose.onNodeWithText("1 × 5").assertExists()
        compose.onNodeWithText("2 × 5").assertExists()
        // Two groups add up to the total.
        compose.onNodeWithText("3 sets").assertExists()
        compose.onNodeWithText("2 exercises · 3 rounds").performScrollTo().assertExists()
        compose.onNodeWithText("Reverse Flies").assertExists()
    }

    @Test
    fun resultsCanBeEditedAndTheWorkoutStarted() {
        show(detail)

        compose.onNodeWithText("RPE 9").assertExists()
        compose.onAllNodesWithContentDescription("Edit result").onFirst().performClick()
        compose.onNodeWithText("Start T2").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("edit squat", "start", "back"), actions)
    }

    @Test
    fun anOpenSessionMarksTheCurrentExercise() {
        show(
            detail.copy(
                status = WorkoutStatus.InProgress,
                items = listOf(
                    WorkoutItem.Single(1, squat.copy(progress = ExerciseProgress.Done)),
                    WorkoutItem.Single(2, rdl.copy(progress = ExerciseProgress.Current(set = 2, setCount = 3))),
                ),
                footer = WorkoutDetailFooter.Resume,
            )
        )

        compose.onNodeWithText("In progress").assertExists()
        compose.onNodeWithText("Current · set 2 of 3").assertExists()
        compose.onNodeWithContentDescription("Done").assertExists()
        // Set 2 of 3 is next: RDL's first group is done and the rest is under way.
        compose.onAllNodesWithContentDescription("Current sets").assertCountEquals(1)
        compose.onNodeWithText("Resume session").performClick()
        assertEquals(listOf("resume"), actions)
    }

    @Test
    fun footerExplainsWhyItCantBeStarted() {
        show(detail.copy(footer = WorkoutDetailFooter.ViewOnly("Finish T3 to start")))

        compose.onNodeWithText("Finish T3 to start").assertExists()
        compose.onNodeWithText("Start T2").assertDoesNotExist()
    }

    @Test
    fun completedWorkoutsSayWhen() {
        show(detail.copy(status = WorkoutStatus.Completed(null), footer = WorkoutDetailFooter.Completed("Mon 1 Jun")))

        compose.onNodeWithText("Completed Mon 1 Jun.", substring = true).assertExists()
    }

    @Test
    fun anExerciseAwaitingReviewHasEverySetDone() {
        show(
            detail.copy(
                status = WorkoutStatus.InProgress,
                items = listOf(WorkoutItem.Single(1, squat.copy(progress = ExerciseProgress.Current(review = true)))),
                footer = WorkoutDetailFooter.Resume,
            )
        )

        compose.onNodeWithText("Current · review result").assertExists()
        compose.onAllNodesWithContentDescription("Sets done").assertCountEquals(2)
    }
}
