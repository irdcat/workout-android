package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Finishing the workout or an exercise asks first; only a confirmation goes through. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class SessionDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<Any>()

    private val rdl = SessionExercise.Planned(
        position = 2,
        exerciseCount = 7,
        name = "RDL",
        tempo = null,
        restSeconds = 150,
        done = listOf(SetRow("Set 1", "60 kg × 6"), SetRow("Set 2", "75 kg × 6"), SetRow("Set 3", "90 kg × 5")),
        set = 4,
        setCount = 5,
        target = "5 reps × 92.5 kg",
        upcoming = listOf(SetRow("Set 5", "92.5 kg × 5")),
    )

    private val logging = SessionUiState(
        workoutName = "T2",
        weekRange = "1 – 7 Jun",
        elapsed = null,
        progress = listOf(ProgressSegment.Done, ProgressSegment.Current) + List(5) { ProgressSegment.Pending },
        exercise = rdl,
        entry = SetEntry(weight = "92.5", weightStep = "2.5", reps = "5"),
    )

    private fun showSession(state: SessionUiState) = compose.setContent {
        WorkoutTheme { SessionScreen(state = state, onEvent = { events += it }) }
    }

    private fun inDialog(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun finishingTheWorkoutAsksAndSaysWhatIsLeft() {
        showSession(logging)

        compose.onNodeWithText("Finish").performClick()

        inDialog("Finish T2?").assertExists()
        inDialog("6 of 7 exercises aren't finished yet; they stay as they are. Next is the summary, where the workout is saved.")
            .assertExists()
        assertEquals(emptyList<Any>(), events)

        inDialog("Finish workout").performClick()
        assertEquals(listOf(SessionEvent.FinishWorkout), events)
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun abandoningTheWorkoutAsksFirst() {
        showSession(logging)

        compose.onNodeWithContentDescription("More workout actions").performClick()
        compose.onNodeWithText("Abandon workout").performClick()

        inDialog("Abandon T2?").assertExists()
        assertEquals(emptyList<Any>(), events)
        inDialog("Cancel").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(emptyList<Any>(), events)

        compose.onNodeWithContentDescription("More workout actions").performClick()
        compose.onNodeWithText("Abandon workout").performClick()
        inDialog("Abandon workout").performClick()
        assertEquals(listOf(SessionEvent.AbandonWorkout), events)
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun cancellingKeepsTheWorkoutGoing() {
        showSession(logging)

        compose.onNodeWithText("Finish").performClick()
        inDialog("Cancel").performClick()

        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(emptyList<Any>(), events)
    }

    @Test
    fun whenEverythingIsDoneTheWorkoutStillAsks() {
        showSession(logging.copy(progress = List(7) { ProgressSegment.Done }, exercise = SessionExercise.Complete, entry = null))

        compose.onNode(hasText("Finish workout") and !hasAnyAncestor(isDialog())).performClick()

        inDialog("Every exercise is done. Next is the summary, where the workout is saved.").assertExists()
        inDialog("Finish workout").performClick()
        assertEquals(listOf(SessionEvent.FinishWorkout), events)
    }

    @Test
    fun finishingAnExerciseSaysWhatIsSkipped() {
        showSession(logging)

        compose.onNode(hasText("Finish exercise") and !hasAnyAncestor(isDialog())).performClick()

        inDialog("Finish RDL?").assertExists()
        inDialog("3 of 5 sets logged; the rest are skipped. You'll review the result next.").assertExists()
        inDialog("Cancel").performClick()
        assertEquals(emptyList<Any>(), events)

        compose.onNode(hasText("Finish exercise") and !hasAnyAncestor(isDialog())).performClick()
        inDialog("Finish exercise").performClick()
        assertEquals(listOf(SessionEvent.FinishExercise), events)
    }

    @Test
    fun openSetsCountWhatIsLogged() {
        showSession(
            logging.copy(
                exercise = SessionExercise.OpenSets(
                    position = 1, exerciseCount = 6, name = "Przysiad piramidka", repsPerSet = "5 reps", startWeight = "40 kg",
                    tempo = null, restSeconds = null, stopwatch = null,
                    done = listOf(SetRow("Set 1", "40 kg × 5")), set = 2,
                ),
            )
        )

        compose.onNode(hasText("Finish exercise") and !hasAnyAncestor(isDialog())).performClick()

        inDialog("1 set logged. You'll review the result next.").assertExists()
    }

    @Test
    fun theWarmUpScreensFinishAsksToo() {
        compose.setContent {
            WorkoutTheme {
                WarmUpScreen(
                    state = WarmUpUiState(
                        workoutName = "T2", weekRange = "1 – 7 Jun", elapsed = null, position = 2, exerciseCount = 7,
                        exerciseName = "RDL", plannedSets = 5, done = emptyList(), number = 1,
                        entry = WarmUpEntry(weight = "20", weightStep = "2.5", reps = "10", rest = "1:00"), lastTime = null,
                    ),
                    onEvent = { events += it },
                )
            }
        }

        compose.onNodeWithText("Finish").performClick()
        inDialog("Cancel").performClick()
        assertEquals(emptyList<Any>(), events)

        compose.onNodeWithText("Finish").performClick()
        inDialog("Finish workout").performClick()
        assertEquals(listOf(WarmUpEvent.FinishWorkout), events)
    }
}
