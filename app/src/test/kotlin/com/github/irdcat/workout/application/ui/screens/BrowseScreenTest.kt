package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
class BrowseScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun workout(id: String, name: String, title: String, status: WorkoutStatus) =
        WorkoutSummary(id, name, title, exerciseCount = 4, supersetCount = 0, status = status)

    private val june = BrowseUiState(
        yearLabel = "2026",
        months = listOf("May", "Jun", "Jul"),
        selectedMonth = 1,
        weeks = listOf(
            BrowseWeek(
                "w1", "1 – 7 Jun", WeekTiming.Past,
                listOf(workout("W/1/T1", "T1", "Przysiad", WorkoutStatus.Completed(null))),
            ),
            BrowseWeek(
                "w2", "8 – 14 Jun", WeekTiming.Current,
                listOf(
                    workout("W/2/T1", "T1", "Bench Press", WorkoutStatus.InProgress),
                    workout("W/2/T2", "T2", "RDL", WorkoutStatus.NotStarted),
                ),
            ),
            BrowseWeek("w3", "15 – 21 Jun", WeekTiming.Upcoming, emptyList(), isRestWeek = true),
        ),
        spillover = SpilloverWeek("29 Jun – 5 Jul", "June", "July", sheetMonthIndex = 2),
    )

    private val actions = mutableListOf<String>()

    private fun show(state: BrowseUiState = june) = compose.setContent {
        WorkoutTheme {
            BrowseScreen(
                state = state,
                onMonthSelected = { actions += "month $it" },
                onOpenWorkout = { actions += "open $it" },
                onTabSelected = { actions += "tab ${it.name}" },
            )
        }
    }

    @Test
    fun theCurrentWeekStartsExpanded() {
        show()

        compose.onNodeWithText("CURRENT").assertExists()
        compose.onNodeWithText("In progress").assertExists()
        compose.onNodeWithText("Not started").assertExists()
        compose.onNodeWithText("Przysiad").assertDoesNotExist()
        compose.onNodeWithText("Rest week").assertExists()

        compose.onNodeWithText("RDL").performClick()
        assertEquals(listOf("open W/2/T2"), actions)
    }

    @Test
    fun tappingAWeekExpandsIt() {
        show()

        compose.onNodeWithText("1 – 7 Jun").performClick()
        compose.onNodeWithText("Przysiad").assertExists()
        // One week open at a time.
        compose.onNodeWithText("RDL").assertDoesNotExist()

        compose.onNodeWithText("Przysiad").performClick()
        assertEquals(listOf("open W/1/T1"), actions)
    }

    @Test
    fun monthsAndTheSpilloverWeekSwitchSheets() {
        show()

        compose.onNodeWithText("May").performClick()
        compose.onNodeWithText("29 Jun – 5 Jul starts in June but is listed in the July sheet.")
            .performScrollTo()
            .performClick()

        assertEquals(listOf("month 0", "month 2"), actions)
    }
}
