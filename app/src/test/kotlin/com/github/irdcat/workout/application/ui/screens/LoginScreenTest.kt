package com.github.irdcat.workout.application.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun continueStartsSignIn() {
        var clicks = 0
        compose.setContent { WorkoutTheme { LoginScreen(onContinueWithGoogle = { clicks++ }) } }

        compose.onNodeWithText("Continue with Google").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun theButtonIsDisabledWhileSigningIn() {
        compose.setContent { WorkoutTheme { LoginScreen(onContinueWithGoogle = {}, isLoading = true) } }

        compose.onNode(hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun errorsAreShown() {
        compose.setContent { WorkoutTheme { LoginScreen(onContinueWithGoogle = {}, error = LoginErrors.NoAccount) } }

        compose.onNodeWithText(LoginErrors.NoAccount).assertExists()
    }
}
