package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.results.ResultTarget
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.infrastructure.google.SheetsConfigDataStore
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeAuthRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric provides the Context the spreadsheet link and the version name come from. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = fixedClock("2026-06-03", "17:42")
    private val sheetsConfig = SheetsConfigDataStore(ApplicationProvider.getApplicationContext())
    private val auth = FakeAuthRepository().apply { email.value = "your.name@gmail.com" }
    private val settings = FakeSettingsRepository()
    private val results = FakeResultRepository()
    private val planMonths = months(week("w1", "2026-06-01", workout("W/1/T1", "T1")))
    private val plan = FakeWorkoutPlanRepository(planMonths).apply { monthsAfterSync = planMonths }

    private val link = "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit"

    // The DataStore is a process-wide singleton, so start every test from the same link.
    @Before
    fun reset() = runTest { sheetsConfig.updateSheetsConfig(link, setOf("Notatki")) }

    private fun TestScope.settingsScreen(): SettingsViewModel {
        val viewModel = SettingsViewModel(
            ApplicationProvider.getApplicationContext(),
            auth,
            sheetsConfig,
            plan,
            clock,
            settings,
            SyncWorkoutPlan(plan, FakeActiveSessionRepository(clock), results, settings),
            results,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    /** The link is read on Dispatchers.IO, so wait for it instead of the test clock. */
    private suspend fun SettingsViewModel.ready(): SettingsUiState =
        (state.first { it is Loadable.Ready } as Loadable.Ready).value

    @Test
    fun showsTheAccountSheetAndSettings() = runTest(main.dispatcher) {
        plan.syncInfo.value = plan.syncInfo.value.copy(sheetCount = 2, weekCount = 9)
        val state = settingsScreen().ready()

        assertEquals("your.name@gmail.com", state.email)
        assertEquals(link, state.sheetUrl)
        assertEquals(listOf("Notatki"), state.blacklist)
        assertEquals(2, state.savedSheets)
        assertEquals(9, state.savedWeeks)
        assertEquals("2.5", state.weightStep)
        assertEquals(RestEndAlert.Vibrate, state.restAlert)
        assertFalse(state.isSyncing)
    }

    @Test
    fun everySwitchIsSaved() = runTest(main.dispatcher) {
        val viewModel = settingsScreen()

        viewModel.onEvent(SettingsEvent.WriteResultsChanged(false))
        viewModel.onEvent(SettingsEvent.MeasureTimeChanged(false))
        viewModel.onEvent(SettingsEvent.AutoRestTimerChanged(false))
        viewModel.onEvent(SettingsEvent.KeepScreenOnChanged(false))
        viewModel.onEvent(SettingsEvent.RestAlertSelected(RestEndAlert.Both))
        viewModel.onEvent(SettingsEvent.WeightStepSelected("0.5"))
        viewModel.onEvent(SettingsEvent.PrefillWeightChanged(false))
        viewModel.onEvent(SettingsEvent.HideAnalysisTabChanged(true))
        viewModel.onEvent(SettingsEvent.HideScoresTabChanged(true))

        val expected = AppSettings(
            writeResultsToSheet = false,
            measureWorkoutTime = false,
            autoStartRestTimer = false,
            keepScreenOn = false,
            restEndAlert = RestEndAlert.Both,
            defaultWeightStepKg = 0.5,
            prefillWeightFromLastTime = false,
            hideAnalysisTab = true,
            hideScoresTab = true,
        )
        assertEquals(expected, settings.state.value)
        assertEquals("0.5", viewModel.ready().weightStep)
        assertEquals(true, viewModel.ready().hideAnalysisTab)
        assertEquals(true, viewModel.ready().hideScoresTab)
    }

    @Test
    fun anUnreadableWeightStepIsIgnored() = runTest(main.dispatcher) {
        settingsScreen().onEvent(SettingsEvent.WeightStepSelected("abc"))
        assertEquals(AppSettings(), settings.state.value)
    }

    @Test
    fun countsResultsNotInTheSheetYet() = runTest(main.dispatcher) {
        val viewModel = settingsScreen()
        assertEquals(0, viewModel.ready().unwrittenResults)

        results.save(SavedResult(ResultTarget("W/1/T1", "1", 0, "Przysiad"), "RPE 9", clock.instant()))

        assertEquals(1, viewModel.ready().unwrittenResults)
    }

    @Test
    fun syncNowSyncsThePlan() = runTest(main.dispatcher) {
        val viewModel = settingsScreen()
        viewModel.onEvent(SettingsEvent.SyncNow)

        assertEquals(1, plan.syncCalls)
        assertFalse(viewModel.ready().isSyncing)
    }

    @Test
    fun clearingSavedDataReloadsThePlan() = runTest(main.dispatcher) {
        val viewModel = settingsScreen()

        viewModel.onEvent(SettingsEvent.ClearSavedData)

        // Cleared, then read again from the sheet.
        assertEquals(1, plan.syncCalls)
        assertEquals(planMonths, plan.months.value)
        assertFalse(viewModel.ready().isSyncing)
    }

    @Test
    fun signingOut() = runTest(main.dispatcher) {
        val viewModel = settingsScreen()
        viewModel.onEvent(SettingsEvent.SignOut)

        assertEquals(SettingsNavigation.SignedOut, viewModel.navigation.first())
        assertNull(auth.email.value)
    }
}
