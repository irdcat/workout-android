package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class AnalysisScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<Any>()

    private val weeks = listOf("25.5", "1.6", "8.6").mapIndexed { index, label ->
        ChartWeek(LocalDate.of(2026, 5, 25).plusWeeks(index.toLong()), label, current = index == 1, future = index == 2)
    }

    private val state = AnalysisUiState(
        groups = listOf("Squat", "Bench", "Deadlift"),
        group = "Deadlift",
        exercise = ExerciseAnalysis(
            name = "Deadlift",
            coverage = "In your sheet for 30 weeks · logged on this device for 4 weeks",
            weeks = weeks,
            selectedWeek = 1,
            volume = MetricPanel.Chart(
                "Volume", "Sets × reps × kg", "kg", ChartKind.Bars,
                planned = listOf(2160.0, 2185.0, 2250.0),
                actual = listOf(2060.0, 1260.0, null),
                note = "Actual so far this week.",
            ),
            intensity = MetricPanel.Unavailable(
                "Intensity", "Top set weight", "Not available",
                "Bodyweight exercise. The sheet gives no weight, so there is nothing to chart.",
            ),
            rpe = MetricPanel.Chart(
                "RPE", "Effort, 6 to 10", "", ChartKind.Lines,
                planned = listOf(null, 8.5, null),
                actual = listOf(7.0, 8.0, null),
                note = null,
                axis = 6.0..10.0,
            ),
        ),
        range = AnalysisRange.EightWeeks,
        showPlanned = true,
        showActual = true,
    )

    private fun show(state: AnalysisUiState = this.state) = compose.setContent {
        WorkoutTheme { AnalysisScreen(state = state, onEvent = { events += it }, onTabSelected = { events += it }) }
    }

    @Test
    fun showsTheWeeksNumbersAndWhatIsMissing() {
        show()

        compose.onNodeWithText("In your sheet for 30 weeks · logged on this device for 4 weeks").assertExists()
        compose.onNodeWithText("2,185", substring = true).assertExists()
        compose.onNodeWithText("Actual so far this week.").assertExists()
        compose.onNodeWithText("Not available").performScrollTo().assertExists()
        compose.onNodeWithContentDescription(
            "Volume by week, planned and actual. Week of 1.6: planned 2,185 kg, actual 1,260 kg. Tap a week for its numbers.",
        ).assertExists()
        compose.onNodeWithContentDescription(
            "Week of 1.6, Volume: planned 2,185, actual 1,260, RPE: planned 8.5, actual 8",
        ).performScrollTo().assertExists()
    }

    @Test
    fun controlsReachTheirEvents() {
        show()

        compose.onNodeWithText("Squat").performClick()
        compose.onNodeWithText("16 weeks").performClick()
        compose.onNodeWithText("Planned").performClick()
        compose.onNodeWithText("Actual").performClick()
        compose.onNodeWithText("Analysis").performClick()

        assertEquals(
            listOf(
                AnalysisEvent.GroupPicked("Squat"),
                AnalysisEvent.RangeSelected(AnalysisRange.SixteenWeeks),
                AnalysisEvent.TogglePlanned,
                AnalysisEvent.ToggleActual,
                MainTab.Analysis,
            ),
            events,
        )
    }

    @Test
    fun withoutGroupsItSaysSo() {
        show(state.copy(exercise = null, groups = emptyList(), group = null))
        compose.onNodeWithText("None of your analysis groups is in your sheet yet.", substring = true).assertExists()
    }

    @Test
    fun manyGroupsStillPick() {
        show(state.copy(groups = listOf("Squat", "Bench", "Deadlift", "OHP", "Rows")))
        compose.onNodeWithText("Rows").performClick()
        assertEquals(listOf<Any>(AnalysisEvent.GroupPicked("Rows")), events)
    }
}
