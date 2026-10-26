package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
class SetupScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<String>()

    private fun show(
        enabled: Boolean = true,
        sheetsFound: Int? = 10,
        blacklist: List<String> = listOf("Zawodnik"),
        suggestions: List<String> = emptyList(),
        input: String = "",
    ) =
        compose.setContent {
            WorkoutTheme {
                SetupScreen(
                    email = "your.name@gmail.com",
                    sheetUrl = "docs.google.com/spreadsheets/d/1dpBR/edit",
                    onSheetUrlChange = { actions += "url $it" },
                    sheetsFound = sheetsFound,
                    sheetsToLoad = listOf("CZERWIEC", "LIPIEC"),
                    blacklist = blacklist,
                    onRemoveFromBlacklist = { actions += "remove $it" },
                    blacklistInput = input,
                    onBlacklistInputChange = { actions += "input $it" },
                    onAddToBlacklist = { actions += "add" },
                    writeResults = true,
                    onWriteResultsChange = { actions += "write $it" },
                    onSignOut = { actions += "sign out" },
                    onSave = { actions += "save" },
                    blacklistSuggestions = suggestions,
                    onPickBlacklistSuggestion = { actions += "pick $it" },
                    enabled = enabled,
                )
            }
        }

    @Test
    fun showsTheSheetsThatWillLoad() {
        show()

        compose.onNodeWithText("your.name@gmail.com").assertExists()
        compose.onNodeWithText("10 sheets found · 2 will load").assertExists()
        compose.onNodeWithText("WILL LOAD · 2").performScrollTo().assertExists()
    }

    @Test
    fun everyControlReachesItsCallback() {
        show()

        compose.onNodeWithContentDescription("Remove Zawodnik from blacklist").performClick()
        compose.onNodeWithContentDescription("Add to blacklist").performScrollTo().performClick()
        compose.onNodeWithText("Write results to the sheet").performScrollTo()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Save and load workouts").performClick()
        compose.onNodeWithText("Sign out").performClick()

        assertEquals(listOf("remove Zawodnik", "add", "write false", "save", "sign out"), actions)
    }

    @Test
    fun typingInTheLinkField() {
        show()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("x")
        assertEquals(1, actions.count { it.startsWith("url ") })
    }

    @Test
    fun whileLoadingOnlySigningOutWorks() {
        show(enabled = false, sheetsFound = null)

        compose.onNodeWithText("Save and load workouts").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove Zawodnik from blacklist").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Add to blacklist").assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsNotEnabled()
        // A disabled field has no text input, so find it by its value.
        compose.onNodeWithText("docs.google.com/spreadsheets/d/1dpBR/edit").assertIsNotEnabled()
        compose.onNodeWithText("Sign out").assertIsEnabled().performClick()

        assertEquals(listOf("sign out"), actions)
    }

    @Test
    fun theBlacklistFieldSuggestsSheets() {
        show(suggestions = listOf("Archiwum", "Notatki"), input = "i")

        compose.onNodeWithText("Archiwum").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("i")).performScrollTo().performClick()

        compose.onNodeWithText("Archiwum", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Notatki", useUnmergedTree = true).performClick()
        assertEquals(listOf("pick Notatki"), actions)
    }
}
