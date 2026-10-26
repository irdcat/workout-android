package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import com.github.irdcat.workout.domain.settings.RestEndAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A phone-sized screen: the default is small enough to push buttons out of view.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val settings = SettingsUiState(
        email = "your.name@gmail.com",
        sheetUrl = "docs.google.com/spreadsheets/d/1dpBR…",
        blacklist = listOf("Zawodnik"),
        writeResults = true,
        lastSynced = "Today 14:32",
        savedSheets = 8,
        savedWeeks = 30,
        measureTime = true,
        autoRestTimer = true,
        keepScreenOn = true,
        restAlert = RestEndAlert.Vibrate,
        weightStep = "2.5",
        prefillWeight = true,
        versionName = "1.0",
    )

    private val events = mutableListOf<SettingsEvent>()

    private fun show(state: SettingsUiState = settings) = compose.setContent {
        WorkoutTheme { SettingsScreen(state = state, onEvent = { events += it }, onTabSelected = {}) }
    }

    @Test
    fun switchesSendTheirNewValue() {
        show()

        compose.onNodeWithText("Keep screen on").performScrollTo().performClick()
        compose.onNodeWithText("Write results to the sheet").performScrollTo().performClick()

        assertEquals(
            listOf(SettingsEvent.KeepScreenOnChanged(false), SettingsEvent.WriteResultsChanged(false)),
            events,
        )
    }

    @Test
    fun theAnalysisTabCanBeHiddenAsABetaFeature() {
        show()

        compose.onNodeWithText("BETA FEATURES").performScrollTo().assertExists()
        compose.onNodeWithText("Hide Analysis tab").performScrollTo().performClick()

        assertEquals(listOf(SettingsEvent.HideAnalysisTabChanged(true)), events)
    }

    @Test
    fun choicesSendTheSelectedOption() {
        show()

        compose.onNodeWithText("Both").performScrollTo().performClick()
        compose.onNodeWithText("0.5").performScrollTo().performClick()

        assertEquals(listOf(SettingsEvent.RestAlertSelected(RestEndAlert.Both), SettingsEvent.WeightStepSelected("0.5")), events)
    }

    @Test
    fun clearingSavedDataAsksFirst() {
        show()

        compose.onNodeWithText("Clear").performScrollTo().performClick()
        compose.onNodeWithText("Clear saved data?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Clear saved data?").assertDoesNotExist()
        assertTrue(events.isEmpty())

        compose.onNodeWithText("Clear").performScrollTo().performClick()
        // The dialog's button; the row's own "Clear" sits behind the dialog.
        compose.onAllNodesWithText("Clear").onLast().performClick()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.ClearSavedData), events)
    }

    @Test
    fun lastSyncShowsWhatIsSaved() {
        show()
        compose.onNodeWithText("Today 14:32 · 8 sheets, 30 weeks", substring = true).assertExists()
        compose.onNodeWithText("not in the sheet yet", substring = true).assertDoesNotExist()
    }

    @Test
    fun resultsWaitingForTheSheetAreCounted() {
        show(settings.copy(unwrittenResults = 2))
        compose.onNodeWithText("2 results not in the sheet yet", substring = true).assertExists()
    }
}
