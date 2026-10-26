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

// A phone-sized screen: the default is small enough to push buttons out of view.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val t1 = WorkoutSummary("W/1/T1", "T1", "Przysiad · Bench Press", 5, 0, WorkoutStatus.Completed("Mon"))
    private val t2 = WorkoutSummary("W/1/T2", "T2", "Front Lever · RDL", 7, 1, WorkoutStatus.NotStarted)
    private val t3 = WorkoutSummary("W/1/T3", "T3", "Wykroki", 1, 0, WorkoutStatus.NotStarted)

    private val week = HomeUiState(
        monthLabel = "June 2026",
        weekRange = "1 – 7 Jun",
        sync = SyncStatus.Synced("14:32"),
        content = HomeContent.Plan(listOf(t1, t2, t3)),
    )

    private val actions = mutableListOf<String>()

    private fun show(state: HomeUiState) = compose.setContent {
        WorkoutTheme {
            HomeScreen(
                state = state,
                onRefresh = { actions += "refresh" },
                onResumeSession = { actions += "resume" },
                onResumeRest = { actions += "resume rest" },
                onOpenWorkout = { actions += "open $it" },
                onStartWorkout = { actions += "start $it" },
                onOpenLatestWeek = { actions += "latest week" },
                onTabSelected = { actions += "tab ${it.name}" },
            )
        }
    }

    @Test
    fun theWeeksWorkoutsCanBeOpenedOrStarted() {
        show(week)

        compose.onNodeWithText("1 – 7 JUN").assertExists()
        compose.onNodeWithText("5 exercises · Completed Mon").assertExists()
        compose.onNodeWithText("7 exercises · 1 superset").assertExists()
        compose.onNodeWithText("1 exercise").assertExists()

        compose.onNodeWithText("Przysiad · Bench Press").performClick()
        compose.onNodeWithText("Start T2").performScrollTo().performClick()
        compose.onNodeWithText("Front Lever · RDL").performScrollTo().performClick()

        assertEquals(listOf("open W/1/T1", "start W/1/T2", "open W/1/T2"), actions)
    }

    @Test
    fun anOpenSessionBlocksStartingOthers() {
        show(
            week.copy(
                activeSession = ActiveSession("T2", elapsed = "12:04", exercise = "RDL", set = 4, setCount = 5),
                content = HomeContent.Plan(listOf(t1, t2.copy(status = WorkoutStatus.InProgress), t3)),
            )
        )

        compose.onNodeWithText("SESSION IN PROGRESS").assertExists()
        compose.onNodeWithText("RDL · set 4 of 5").assertExists()
        compose.onNodeWithText("12:04").assertExists()
        compose.onNodeWithText("1 exercise · Finish T2 to start").assertExists()
        compose.onNodeWithText("Start T3").assertDoesNotExist()

        compose.onNodeWithText("Resume session").performClick()
        assertEquals(listOf("resume"), actions)
    }

    @Test
    fun syncChipRefreshesAndShowsWhenOffline() {
        show(week.copy(sync = SyncStatus.Offline("09:10")))

        compose.onNodeWithText("Showing saved workouts", substring = true).assertExists()
        compose.onNodeWithContentDescription("Retry sync. Offline, showing data cached at 09:10").performClick()
        assertEquals(listOf("refresh"), actions)
    }

    @Test
    fun noPlanPointsToTheLatestWeek() {
        show(week.copy(content = HomeContent.NoPlan("19 Oct", "WRZESIEŃ", "20 Sep", "14 – 20 Sep")))

        compose.onNodeWithText("NO PLAN FOR THIS WEEK").assertExists()
        compose.onNodeWithText("Your spreadsheet has no week starting 19 Oct", substring = true).assertExists()
        compose.onNodeWithText("Open latest week · 14 – 20 Sep").performScrollTo().performClick()
        compose.onNodeWithText("Check the sheet again").performScrollTo().performClick()

        assertEquals(listOf("latest week", "refresh"), actions)
    }

    @Test
    fun aSessionIsShownOnARestWeekToo() {
        show(week.copy(content = HomeContent.RestWeek, activeSession = ActiveSession("T3", elapsed = null, exercise = null)))

        compose.onNodeWithText("REST WEEK").assertExists()
        compose.onNodeWithText("All exercises logged").assertExists()
    }

    @Test
    fun bottomTabsSwitchScreens() {
        show(week)

        compose.onNodeWithText(MainTab.Browse.label).performClick()
        compose.onNodeWithText(MainTab.Settings.label).performClick()
        assertEquals(listOf("tab Browse", "tab Settings"), actions)
    }

    @Test
    fun aRestingSessionReopensTheRestTimer() {
        show(
            week.copy(
                activeSession = ActiveSession(
                    "T2", elapsed = "12:04", exercise = "RDL", set = 4, setCount = 5, rest = ActiveRest(left = "2:11"),
                ),
                content = HomeContent.Plan(listOf(t1, t2.copy(status = WorkoutStatus.InProgress), t3)),
            )
        )

        compose.onNodeWithText("RDL · resting 2:11 left · set 4 of 5 next").assertExists()
        compose.onNodeWithText("Resume session").performClick()
        assertEquals(listOf("resume rest"), actions)
    }

    @Test
    fun aPausedRestSaysSo() {
        show(
            week.copy(
                activeSession = ActiveSession(
                    "T2", elapsed = null, exercise = "RDL", set = 4, setCount = 5, rest = ActiveRest(left = "2:11", paused = true),
                ),
            )
        )
        compose.onNodeWithText("RDL · rest paused, 2:11 left · set 4 of 5 next").assertExists()
    }

    @Test
    fun aResultWaitingForReviewSaysSo() {
        show(week.copy(activeSession = ActiveSession("T2", elapsed = null, exercise = "RDL", reviewing = true)))
        compose.onNodeWithText("RDL · review the result").assertExists()
    }

    @Test
    fun whileLoadingTheWeekIsASkeleton() {
        compose.setContent { WorkoutTheme { HomeSkeleton() } }

        // One element for accessibility services, not a pile of grey shapes.
        compose.onNodeWithContentDescription("Loading").assertExists()
        compose.onNodeWithText("SESSION IN PROGRESS").assertDoesNotExist()
    }
}
