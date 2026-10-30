package com.github.irdcat.workout.application.ui

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.infrastructure.google.SheetsConfigDataStore
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeAuthRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MutableClock
import com.github.irdcat.workout.testing.RecordingRestAlarm
import com.github.irdcat.workout.testing.TestToday
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import javax.inject.Inject

/**
 * The real activity and navigation graph, with the fakes from
 * [com.github.irdcat.workout.testing.TestAppModule] in place of Google, sessions and settings.
 * Runs on the JVM (Robolectric, configured in `robolectric.properties`) and on a device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var auth: FakeAuthRepository
    @Inject lateinit var plan: FakeWorkoutPlanRepository
    @Inject lateinit var sessions: FakeActiveSessionRepository
    @Inject lateinit var logs: FakeSessionLogRepository
    @Inject lateinit var clock: MutableClock
    @Inject lateinit var restAlarm: RecordingRestAlarm
    @Inject lateinit var results: FakeResultRepository

    private val sheetsConfig = SheetsConfigDataStore(ApplicationProvider.getApplicationContext())
    private val link = "https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit"

    private val rdl = exercise(
        "rdl", "1", "RDL",
        group(reps = Reps.Count(6), load = Load.Weight(60.0), rest = 150),
        group(reps = Reps.Count(5), load = Load.Weight(90.0), rest = 150),
    )
    private val t1 = workout("W/1/T1", "T1", single(exercise("squat", "1", "Przysiad", group(result = "RPE 9"))))
    private val t2 = workout("W/1/T2", "T2", single(rdl))
    private val thisWeek = months(week("w1", "2026-06-01", t1, t2))

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hilt.inject()
        plan.months.value = thisWeek
        plan.monthsAfterSync = thisWeek
        // The link DataStore is a process-wide singleton, so start every test with none saved.
        runBlocking { sheetsConfig.updateSheetsConfig("", emptySet()) }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun signedIn(withSpreadsheet: Boolean = true) {
        auth.email.value = "your.name@gmail.com"
        if (withSpreadsheet) runBlocking { sheetsConfig.updateSheetsConfig(link, emptySet()) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** Screens load on background threads (DataStore), so wait for them to appear. */
    private fun ComposeTestRule.waitForText(text: String, substring: Boolean = false) = waitUntil(5_000) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun SemanticsNodeInteractionsProvider.tap(text: String) = onNodeWithText(text).performClick()

    private fun pressBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    /** Starts T2 from Home and logs its first set, which opens the rest timer. */
    private fun startT2AndLogASet() {
        signedIn()
        launch()
        compose.waitForText("Start T2")
        compose.tap("Start T2")
        compose.waitForText("Log set 1")
        compose.tap("Log set 1")
        compose.waitForText("REST · AFTER SET 1")
    }

    // ---- Start ---------------------------------------------------------------

    @Test
    fun signingInWithoutASpreadsheetOpensSetupAndSavingOpensHome() {
        launch()
        compose.waitForText("Continue with Google")
        compose.tap("Continue with Google")

        // The form becomes editable once the account is shown.
        compose.waitForText("your.name@gmail.com")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(link)
        compose.tap("Save and load workouts")

        compose.waitForText("1 – 7 JUN")
        assertEquals(link, runBlocking { sheetsConfig.sheetsConfigFlow().first().sheetUrl })
    }

    @Test
    fun signedInWithASpreadsheetOpensHome() {
        signedIn()
        launch()

        compose.waitForText("1 – 7 JUN")
        compose.onNodeWithText("Start T2").assertExists()
    }

    // ---- Tabs ----------------------------------------------------------------

    @Test
    fun tabsSwitchBetweenTheWeekBrowseAndSettings() {
        signedIn()
        launch()
        compose.waitForText("1 – 7 JUN")

        compose.tap("Browse")
        compose.waitForText("BROWSE")
        compose.tap("Scores")
        compose.waitForText("SCORES")
        compose.tap("Settings")
        compose.waitForText("your.name@gmail.com")
        compose.tap("Week")
        compose.waitForText("1 – 7 JUN")
    }

    @Test
    fun signingOutFromSettingsReturnsToSignIn() {
        signedIn()
        launch()
        compose.waitForText("1 – 7 JUN")

        compose.tap("Settings")
        compose.waitForText("Sign out")
        compose.tap("Sign out")

        compose.waitForText("Continue with Google")
        assertNull(auth.email.value)
    }

    // ---- Workout ---------------------------------------------------------------

    @Test
    fun aWorkoutOpensItsDetailAndBackReturns() {
        signedIn()
        launch()
        compose.waitForText("1 – 7 JUN")

        compose.tap("Przysiad")
        compose.waitForText("Completed", substring = true)
        compose.onNodeWithText("RPE 9").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.waitForText("Start T2")
    }

    @Test
    fun aWholeSession() {
        signedIn()
        launch()
        compose.waitForText("Start T2")

        // Starting opens the session.
        compose.tap("Start T2")
        compose.waitForText("Log set 1")
        assertEquals("W/1/T2", sessions.session.value?.workoutId)

        // A set with a rest opens the rest timer; undo goes back to the same set.
        compose.tap("Log set 1")
        compose.waitForText("REST · AFTER SET 1")
        compose.onNodeWithContentDescription("Undo set 1").performClick()
        compose.waitForText("Log set 1")

        // Skipping the rest returns to the next set.
        compose.tap("Log set 1")
        compose.waitForText("Skip rest · start set 2")
        compose.tap("Skip rest · start set 2")
        compose.waitForText("Log set 2")

        // Finishing shows the summary, and saving lands on the week with the session ended.
        compose.tap("Finish")
        compose.onNode(hasText("Finish workout") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForText("Save results")
        compose.tap("Save results")
        compose.waitForText("1 – 7 JUN")
        assertNull(sessions.session.value)
        assertEquals(1, logs.logs.value.values.single().sets.size)
        compose.onNodeWithText("SESSION IN PROGRESS").assertDoesNotExist()
        // The logged set became RDL's result, written to the sheet after a sync.
        compose.waitUntil(5_000) { results.writeCalls == 1 }
        assertEquals(emptyList<Any>(), results.saved.value)
    }

    @Test
    fun warmUpsHaveTheirOwnScreenAndRest() {
        signedIn()
        launch()
        compose.waitForText("Start T2")
        compose.tap("Start T2")
        compose.waitForText("Log set 1")

        compose.onNodeWithContentDescription("Warm-up sets, separate from the prescribed sets").performClick()
        compose.waitForText("Log warm-up 1")
        compose.onNodeWithContentDescription("Reps").performTextReplacement("10")
        compose.tap("Log warm-up 1")

        // Its rest is the user's own, and leads back to the working sets.
        compose.waitForText("REST · AFTER WARM-UP 1")
        compose.tap("Another warm-up")
        compose.waitForText("Log warm-up 2")
        compose.onNodeWithContentDescription("Reps").performTextReplacement("8")
        compose.tap("Log warm-up 2")
        compose.waitForText("REST · AFTER WARM-UP 2")
        compose.tap("Skip rest · start set 1")

        // Warm-ups don't count: set 1 is still next.
        compose.waitForText("Log set 1")
        compose.onNodeWithText("Warm-up · 2 sets").assertExists()
        assertEquals(listOf(true, true), logs.logs.value.values.single().sets.map { it.warmUp })
    }

    @Test
    fun anOpenSessionIsResumedFromHome() {
        signedIn()
        sessions.session.value = ActiveSession(t2.id, TestToday.instant())
        launch()

        compose.waitForText("SESSION IN PROGRESS")
        compose.tap("Resume session")
        compose.waitForText("Log set 1")
    }

    @Test
    fun theSessionScreenLeavesWhenTheSessionEndsElsewhere() {
        signedIn()
        sessions.session.value = ActiveSession(t2.id, TestToday.instant())
        launch()
        compose.waitForText("Resume session")
        compose.tap("Resume session")
        compose.waitForText("Log set 1")

        runBlocking { sessions.end() }

        compose.waitForText("Start T2")
    }

    @Test
    fun aRestSetsAnAlarmAndOffersTheNextSetOnceItRunsOut() {
        val loggedAt = clock.instant()
        startT2AndLogASet()
        compose.onNodeWithText("Skip rest · start set 2").assertExists()
        // An alarm, so the alert comes even with the app closed.
        compose.waitUntil(5_000) { restAlarm.scheduled != null }
        assertEquals(loggedAt.plusSeconds(150) to RestEndAlert.Vibrate, restAlarm.scheduled)

        clock.advance(160)

        // The timer ticks once a second, so it notices within a tick.
        compose.waitForText("Start set 2")
        compose.tap("Start set 2")
        compose.waitForText("Log set 2")
        assertNull(restAlarm.scheduled)
        // The rest really took as long as until the tap.
        assertEquals(Duration.ofSeconds(160), logs.logs.value.values.single().rests.values.single())
    }

    @Test
    fun theRestKeepsRunningFromHomeAndCanBeReopened() {
        startT2AndLogASet()
        clock.advance(30)

        compose.onNodeWithContentDescription("Back to home, the timer keeps running").performClick()

        compose.waitForText("SESSION IN PROGRESS")
        compose.waitForText("resting", substring = true)
        // Not ended: no rest recorded, and its alarm is still set.
        assertTrue(logs.logs.value.values.single().rests.isEmpty())
        assertNotNull(restAlarm.scheduled)

        compose.tap("Resume session")
        compose.waitForText("REST · AFTER SET 1")
        compose.tap("Skip rest · start set 2")
        compose.waitForText("Log set 2")
    }

    @Test
    fun backFromTheRestTimerEndsTheRest() {
        startT2AndLogASet()
        clock.advance(30)

        pressBack()

        compose.waitForText("Log set 2")
        assertNull(restAlarm.scheduled)
        assertEquals(Duration.ofSeconds(30), logs.logs.value.values.single().rests.values.single())
    }

    @Test
    fun theSummaryCanGoBackToTheSession() {
        startT2AndLogASet()
        compose.tap("Skip rest · start set 2")
        compose.waitForText("Log set 2")

        compose.tap("Finish")
        compose.onNode(hasText("Finish workout") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForText("Back to session")
        compose.tap("Back to session")

        compose.waitForText("Log set 2")
        assertEquals("W/1/T2", sessions.session.value?.workoutId)
    }

    // ---- Changing the spreadsheet ------------------------------------------------

    private fun openSpreadsheetFromSettings() {
        signedIn()
        launch()
        compose.waitForText("1 – 7 JUN")
        compose.tap("Settings")
        compose.waitForText(link)
        compose.tap("Spreadsheet link")
        // The form becomes editable once the saved values are shown.
        compose.waitForText("YOUR SPREADSHEET")
        compose.waitForText("your.name@gmail.com")
    }

    @Test
    fun savingANewLinkFromSettingsReturnsThereAndDropsTheOldPlan() {
        openSpreadsheetFromSettings()
        val newLink = "https://docs.google.com/spreadsheets/d/9ZyXwVuTsRqPoNmLkJiHgFeDcBa/edit"

        compose.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(newLink)
        compose.tap("Save and load workouts")

        // Back on Settings, not Home: Setup was opened for editing.
        compose.waitForText("Spreadsheet link")
        compose.waitForText(newLink)
        assertEquals(newLink, runBlocking { sheetsConfig.sheetsConfigFlow().first().sheetUrl })
        assertEquals(emptyList<Any>(), plan.months.value)
    }

    @Test
    fun backFromEditingTheSpreadsheetKeepsTheLink() {
        openSpreadsheetFromSettings()

        pressBack()

        compose.waitForText("Spreadsheet link")
        compose.onNodeWithText(link).assertExists()
        assertEquals(link, runBlocking { sheetsConfig.sheetsConfigFlow().first().sheetUrl })
        assertEquals(thisWeek, plan.months.value)
    }
}
