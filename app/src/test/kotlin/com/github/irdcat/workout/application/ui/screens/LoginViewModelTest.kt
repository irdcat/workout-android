package com.github.irdcat.workout.application.ui.screens

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.auth.AuthorizationResult
import com.github.irdcat.workout.domain.auth.SignInResult
import com.github.irdcat.workout.testing.FakeAuthRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only provides the Context and PendingIntent the API needs. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LoginViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val auth = FakeAuthRepository()

    private fun login(clientId: String = "client-id") = LoginViewModel(auth, clientId)

    @Test
    fun missingClientIdIsReported() = runTest(main.dispatcher) {
        val viewModel = login(clientId = "")
        viewModel.onContinueWithGoogle(context)

        assertEquals(LoginErrors.NotConfigured, viewModel.state.value.error)
        assertEquals(0, auth.signInCalls)
    }

    @Test
    fun signsInWhenAccessIsGranted() = runTest(main.dispatcher) {
        val viewModel = login()
        viewModel.onContinueWithGoogle(context)

        val state = viewModel.state.value
        assertTrue(state.isSignedIn)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun closingThePickerIsNotAnError() = runTest(main.dispatcher) {
        auth.signInResult = SignInResult.Cancelled
        val viewModel = login()
        viewModel.onContinueWithGoogle(context)

        assertEquals(LoginUiState(), viewModel.state.value)
    }

    @Test
    fun noAccountOnThePhone() = runTest(main.dispatcher) {
        auth.signInResult = SignInResult.NoAccount
        val viewModel = login()
        viewModel.onContinueWithGoogle(context)

        assertEquals(LoginErrors.NoAccount, viewModel.state.value.error)
    }

    @Test
    fun consentScreenIsLaunchedOnceAndADenialIsReported() = runTest(main.dispatcher) {
        val consent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        auth.authorizeResult = AuthorizationResult.ConsentRequired(consent)
        auth.consentResult = AuthorizationResult.Denied
        val viewModel = login()

        viewModel.onContinueWithGoogle(context)
        assertNotNull(viewModel.state.value.pendingConsent)
        viewModel.onConsentLaunched()
        assertNull(viewModel.state.value.pendingConsent)
        assertTrue(viewModel.state.value.isLoading)

        viewModel.onConsentResult(null)
        assertEquals(LoginErrors.AccessDenied, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isSignedIn)
    }

    @Test
    fun aFailedSignInIsReported() = runTest(main.dispatcher) {
        auth.signInResult = SignInResult.Failed(RuntimeException("offline"))
        val viewModel = login()
        viewModel.onContinueWithGoogle(context)

        assertEquals(LoginErrors.SignInFailed, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun aFailedAuthorizationIsReportedAndCanBeDismissed() = runTest(main.dispatcher) {
        auth.authorizeResult = AuthorizationResult.Failed(RuntimeException("offline"))
        val viewModel = login()
        viewModel.onContinueWithGoogle(context)
        assertEquals(LoginErrors.AuthorizationFailed, viewModel.state.value.error)

        viewModel.onErrorShown()

        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSignedIn)
    }

    @Test
    fun everyErrorHasAMessage() {
        assertEquals(5, LoginErrors.all.toSet().size)
        assertTrue(LoginErrors.all.all { it.isNotBlank() })
    }
}
