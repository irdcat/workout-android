package com.github.irdcat.workout.application.ui.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainNavigationBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hidingAnalysisLeavesTheOtherTabs() {
        assertEquals(listOf(MainTab.Week, MainTab.Browse, MainTab.Scores, MainTab.Settings), visibleMainTabs(hideAnalysisTab = true))
        assertEquals(MainTab.entries.toList(), visibleMainTabs(hideAnalysisTab = false))
    }

    @Test
    fun hidingScoresLeavesTheOtherTabs() {
        assertEquals(listOf(MainTab.Week, MainTab.Browse, MainTab.Analysis, MainTab.Settings), visibleMainTabs(hideScoresTab = true))
        assertEquals(
            listOf(MainTab.Week, MainTab.Browse, MainTab.Settings),
            visibleMainTabs(hideAnalysisTab = true, hideScoresTab = true),
        )
    }

    @Test
    fun showsOnlyTheProvidedTabs() {
        compose.setContent {
            WorkoutTheme {
                CompositionLocalProvider(LocalMainTabs provides visibleMainTabs(hideAnalysisTab = true)) {
                    MainNavigationBar(selected = MainTab.Week, onSelect = {})
                }
            }
        }

        compose.onNodeWithText("Week").assertExists()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Analysis").assertDoesNotExist()
    }
}
