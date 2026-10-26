package com.github.irdcat.workout.application.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.infrastructure.google.SheetsConfigDataStore
import com.github.irdcat.workout.testing.FakeAuthRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric provides the Context the saved spreadsheet link is stored with. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val sheetsConfig = SheetsConfigDataStore(ApplicationProvider.getApplicationContext())
    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()

    // The DataStore is a process-wide singleton, so start every test with no link saved.
    @Before
    fun reset() = runTest { sheetsConfig.updateSheetsConfig("", emptySet()) }

    private fun app() = AppViewModel(auth, sheetsConfig, settings)

    private suspend fun AppViewModel.start(): Route = startDestination.filterNotNull().first()

    @Test
    fun signedOutOpensSignIn() = runTest(main.dispatcher) {
        assertEquals(Route.Login, app().start())
    }

    @Test
    fun signedInWithoutASpreadsheetOpensSetup() = runTest(main.dispatcher) {
        auth.email.value = "your.name@gmail.com"
        assertEquals(Route.Setup(), app().start())
    }

    @Test
    fun signedInWithASpreadsheetOpensHome() = runTest(main.dispatcher) {
        auth.email.value = "your.name@gmail.com"
        sheetsConfig.updateSheetsConfig("https://docs.google.com/spreadsheets/d/abc/edit", emptySet())
        assertEquals(Route.Home, app().start())
    }

    @Test
    fun afterSignInTheSavedSpreadsheetDecides() = runTest(main.dispatcher) {
        val viewModel = app()
        assertEquals(Route.Setup(), viewModel.destinationAfterSignIn())

        sheetsConfig.updateSheetsConfig("https://docs.google.com/spreadsheets/d/abc/edit", emptySet())
        assertEquals(Route.Home, viewModel.destinationAfterSignIn())
    }

    @Test
    fun keepScreenOnFollowsTheSetting() = runTest(main.dispatcher) {
        val viewModel = app()
        assertTrue(viewModel.keepScreenOnWhileTraining.value)

        settings.update { it.copy(keepScreenOn = false) }
        assertFalse(viewModel.keepScreenOnWhileTraining.value)
    }
}
