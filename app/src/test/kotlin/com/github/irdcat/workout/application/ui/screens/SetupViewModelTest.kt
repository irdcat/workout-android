package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.infrastructure.google.SheetsConfig
import com.github.irdcat.workout.infrastructure.google.SheetsConfigDataStore
import com.github.irdcat.workout.testing.FakeAuthRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.fakeSheetsRepository
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Robolectric provides the Context the spreadsheet link is stored with. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SetupViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val sheetsConfig = SheetsConfigDataStore(ApplicationProvider.getApplicationContext())
    private val auth = FakeAuthRepository().apply { email.value = "your.name@gmail.com" }
    private val plan = FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", workout("W/1/T1", "T1"))))
    private val settings = FakeSettingsRepository(AppSettings(writeResultsToSheet = false))

    private val link = "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit"

    // The DataStore is a process-wide singleton, so start every test with no link saved.
    @Before
    fun reset() = runTest { sheetsConfig.updateSheetsConfig("", emptySet()) }

    /** Requests made to the Sheets API; it runs on Dispatchers.IO, hence atomic. */
    private val sheetRequests = AtomicInteger()

    private fun newSetup(authRepository: AuthRepository = auth) = SetupViewModel(
        authRepository,
        sheetsConfig,
        fakeSheetsRepository(sheetsConfig, listOf("CZERWIEC", "LIPIEC", "Notatki")) { sheetRequests.incrementAndGet() },
        plan,
        settings,
    )

    /** The saved values are read on Dispatchers.IO, so wait for them instead of the test clock. */
    private suspend fun setup(): SetupViewModel = newSetup().also { viewModel ->
        viewModel.state.first { it.isLoaded }
    }

    /** The Sheets call runs on Dispatchers.IO, so wait for its answer instead of the test clock. */
    private suspend fun SetupViewModel.checkedSheets(): List<String>? =
        state.first { it.availableSheets != null }.availableSheets

    @Test
    fun startsFromTheSavedAccountAndSettings() = runTest(main.dispatcher) {
        sheetsConfig.updateSheetsConfig(link, setOf("Notatki"))
        val viewModel = setup()

        assertEquals(listOf("CZERWIEC", "LIPIEC", "Notatki"), viewModel.checkedSheets())
        val form = viewModel.state.value
        assertEquals("your.name@gmail.com", form.email)
        assertEquals(link, form.sheetUrl)
        assertEquals(listOf("Notatki"), form.blacklist)
        assertEquals(false, form.writeResults)
        assertEquals(listOf("CZERWIEC", "LIPIEC"), form.sheetsToLoad)
    }

    @Test
    fun theSavedLinkIsCheckedOnce() = runTest(main.dispatcher) {
        sheetsConfig.updateSheetsConfig(link, emptySet())
        val viewModel = setup()
        viewModel.checkedSheets()

        // Long past the pause that checks a typed link.
        testScheduler.advanceTimeBy(2_000)
        testScheduler.runCurrent()
        assertEquals(1, sheetRequests.get())
    }

    @Test
    fun theFormIsReadOnlyUntilTheSavedValuesAreLoaded() = runTest(main.dispatcher) {
        sheetsConfig.updateSheetsConfig(link, setOf("Notatki"))
        // Holds the load back at its first step, reading the account.
        val loaded = CompletableDeferred<Unit>()
        val slowAuth = object : AuthRepository by auth {
            override suspend fun getSignedInEmail(): String? {
                loaded.await()
                return auth.getSignedInEmail()
            }
        }
        val viewModel = newSetup(slowAuth)

        viewModel.onSheetUrlChange("typed too early")
        viewModel.onBlacklistInputChange("Zawodnik")
        viewModel.onAddToBlacklist()
        viewModel.onWriteResultsChange(true)
        viewModel.onSave()
        assertEquals(SetupFormState(), viewModel.state.value)

        loaded.complete(Unit)
        val form = viewModel.state.first { it.isLoaded }
        assertEquals(link, form.sheetUrl)
        assertEquals(listOf("Notatki"), form.blacklist)
        assertEquals(false, form.writeResults)
        // Nothing was saved either.
        assertEquals(SheetsConfig(link, setOf("Notatki")), sheetsConfig.sheetsConfigFlow().first())

        viewModel.onSheetUrlChange("typed after loading")
        assertEquals("typed after loading", viewModel.state.value.sheetUrl)
    }

    @Test
    fun aTypedLinkIsCheckedOnceTypingPauses() = runTest(main.dispatcher) {
        val viewModel = setup()
        viewModel.onSheetUrlChange(link)
        testScheduler.advanceTimeBy(300)
        assertNull(viewModel.state.value.availableSheets)

        testScheduler.advanceTimeBy(301)
        assertEquals(listOf("CZERWIEC", "LIPIEC", "Notatki"), viewModel.checkedSheets())
    }

    @Test
    fun blacklistIgnoresBlanksAndDuplicates() = runTest(main.dispatcher) {
        val viewModel = setup()

        viewModel.onBlacklistInputChange("  Notatki ")
        viewModel.onAddToBlacklist()
        viewModel.onBlacklistInputChange("Notatki")
        viewModel.onAddToBlacklist()
        viewModel.onBlacklistInputChange("   ")
        viewModel.onAddToBlacklist()
        assertEquals(listOf("Notatki"), viewModel.state.value.blacklist)
        assertEquals("", viewModel.state.value.blacklistInput)

        viewModel.onRemoveFromBlacklist("Notatki")
        assertTrue(viewModel.state.value.blacklist.isEmpty())
    }

    @Test
    fun savingStoresTheLinkAndSettingsAndClearsAPlanFromAnotherSheet() = runTest(main.dispatcher) {
        val viewModel = setup()
        viewModel.onSheetUrlChange("  $link ")
        viewModel.onBlacklistInputChange("Notatki")
        viewModel.onAddToBlacklist()
        viewModel.onWriteResultsChange(true)

        viewModel.onSave()

        assertEquals(SetupNavigation.Saved, viewModel.navigation.first())
        assertEquals(SheetsConfig(link, setOf("Notatki")), sheetsConfig.sheetsConfigFlow().first())
        assertTrue(settings.state.value.writeResultsToSheet)
        assertTrue(plan.months.value.isEmpty())
    }

    @Test
    fun savingTheSameSheetKeepsThePlan() = runTest(main.dispatcher) {
        sheetsConfig.updateSheetsConfig(link, setOf("Notatki"))
        val viewModel = setup()
        viewModel.checkedSheets()

        viewModel.onSave()

        assertEquals(SetupNavigation.Saved, viewModel.navigation.first())
        assertEquals(1, plan.months.value.size)
    }

    @Test
    fun aBlankLinkIsNotSaved() = runTest(main.dispatcher) {
        val viewModel = setup()
        viewModel.onSave()

        assertEquals(SheetsConfig(), sheetsConfig.sheetsConfigFlow().first())
        assertEquals(false, viewModel.state.value.isSaving)
    }

    @Test
    fun signingOut() = runTest(main.dispatcher) {
        val viewModel = setup()
        viewModel.onSignOut()

        assertEquals(SetupNavigation.SignedOut, viewModel.navigation.first())
        assertNull(auth.email.value)
    }

    @Test
    fun suggestionsAreTheSpreadsheetsSheetsMatchingWhatIsTyped() = runTest(main.dispatcher) {
        sheetsConfig.updateSheetsConfig(link, setOf("Notatki"))
        val viewModel = setup()
        viewModel.checkedSheets()

        // Everything not yet blacklisted while nothing is typed.
        assertEquals(listOf("CZERWIEC", "LIPIEC"), viewModel.state.value.blacklistSuggestions)

        viewModel.onBlacklistInputChange("ec")
        // Both contain it; neither starts with it.
        assertEquals(listOf("CZERWIEC", "LIPIEC"), viewModel.state.value.blacklistSuggestions)
        viewModel.onBlacklistInputChange("li")
        assertEquals(listOf("LIPIEC"), viewModel.state.value.blacklistSuggestions)
        viewModel.onBlacklistInputChange("xyz")
        assertTrue(viewModel.state.value.blacklistSuggestions.isEmpty())
    }

    @Test
    fun namesStartingWithWhatIsTypedComeFirst() {
        val form = SetupFormState(availableSheets = listOf("PAŹDZIERNIK", "MAJ", "MAJ 2"), blacklistInput = "maj")
        assertEquals(listOf("MAJ", "MAJ 2"), form.blacklistSuggestions)

        val contains = SetupFormState(availableSheets = listOf("STYCZEŃ", "LUTY"), blacklistInput = "y")
        assertEquals(listOf("STYCZEŃ", "LUTY"), contains.blacklistSuggestions)
    }

    @Test
    fun pickingASuggestionBlacklistsIt() = runTest(main.dispatcher) {
        sheetsConfig.updateSheetsConfig(link, emptySet())
        val viewModel = setup()
        viewModel.checkedSheets()
        viewModel.onBlacklistInputChange("lip")

        viewModel.onPickBlacklistSuggestion("LIPIEC")

        assertEquals(listOf("LIPIEC"), viewModel.state.value.blacklist)
        assertEquals("", viewModel.state.value.blacklistInput)
        assertEquals(listOf("CZERWIEC", "Notatki"), viewModel.state.value.blacklistSuggestions)
    }

    @Test
    fun aTypedNameTakesTheSheetsOwnSpelling() = runTest(main.dispatcher) {
        sheetsConfig.updateSheetsConfig(link, emptySet())
        val viewModel = setup()
        viewModel.checkedSheets()

        viewModel.onBlacklistInputChange(" notatki ")
        viewModel.onAddToBlacklist()
        // Not a sheet here: kept as typed, for a sheet that may be added later.
        viewModel.onBlacklistInputChange("Archiwum")
        viewModel.onAddToBlacklist()

        assertEquals(listOf("Notatki", "Archiwum"), viewModel.state.value.blacklist)
    }
}
