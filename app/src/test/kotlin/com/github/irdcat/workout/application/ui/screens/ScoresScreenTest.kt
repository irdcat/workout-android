package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme
import com.github.irdcat.workout.domain.strength.Sex
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class ScoresScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<Any>()

    private fun show(state: ScoresUiState) = compose.setContent {
        WorkoutTheme { ScoresScreen(state = state, onEvent = { events += it }, onTabSelected = { events += it }) }
    }

    @Test
    fun showsBothScores() {
        show(ScoresUiState(bodyweight = "90", total = "700").withScores())

        compose.onNodeWithContentDescription("DOTS 452.62").assertExists()
        compose.onNodeWithContentDescription("Wilks 446.88").assertExists()
        compose.onNodeWithContentDescription("Wilks 2020 536.90").assertExists()
    }

    @Test
    fun withoutNumbersThereIsNoScoreYet() {
        show(ScoresUiState())

        compose.onNodeWithContentDescription("DOTS not available").assertExists()
    }

    @Test
    fun inputsSendEvents() {
        show(ScoresUiState())

        compose.onNodeWithText("Female").performClick()
        compose.onNodeWithContentDescription("Bodyweight").performTextReplacement("80")
        compose.onNodeWithContentDescription("Increase total").performClick()

        assertEquals(
            listOf(ScoresEvent.SexSelected(Sex.Female), ScoresEvent.BodyweightChanged("80"), ScoresEvent.AdjustTotal(1)),
            events,
        )
    }
}
