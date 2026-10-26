package com.github.irdcat.workout.application.ui.screens

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.auth.AuthorizationResult
import com.github.irdcat.workout.domain.auth.SignInResult
import com.github.irdcat.workout.infrastructure.google.GOOGLE_SERVER_CLIENT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "LoginViewModel"

@Immutable
data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Google's consent screen, waiting to be launched by the UI. */
    val pendingConsent: PendingIntent? = null,
    /** Sign-in and Sheets access are done; the UI should move on. */
    val isSignedIn: Boolean = false,
)

/** Messages shown under the sign-in button. */
object LoginErrors {
    const val NotConfigured = "Google sign-in isn't set up in this build."
    const val NoAccount = "There's no Google account on this phone. Add one in system settings, then try again."
    const val SignInFailed = "Sign-in didn't work. Check your connection and try again."
    const val AccessDenied = "Workout needs access to Google Sheets to load your workouts."
    const val AuthorizationFailed = "Couldn't get access to Google Sheets. Try again."

    val all = listOf(NotConfigured, NoAccount, SignInFailed, AccessDenied, AuthorizationFailed)
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @param:Named(GOOGLE_SERVER_CLIENT_ID) private val serverClientId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /** [activityContext] hosts the account picker; it is not kept. */
    fun onContinueWithGoogle(activityContext: Context) {
        if (_state.value.isLoading) return
        if (serverClientId.isBlank()) {
            _state.update { it.copy(error = LoginErrors.NotConfigured) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.signIn(activityContext, serverClientId)) {
                is SignInResult.SignedIn -> handleAuthorization(authRepository.authorize())
                SignInResult.Cancelled -> _state.update { it.copy(isLoading = false) }
                SignInResult.NoAccount -> fail(LoginErrors.NoAccount)
                is SignInResult.Failed -> {
                    Log.e(TAG, "Google sign-in failed", result.cause)
                    fail(LoginErrors.SignInFailed)
                }
            }
        }
    }

    /** The UI has started [LoginUiState.pendingConsent]; don't launch it again. */
    fun onConsentLaunched() {
        _state.update { it.copy(pendingConsent = null) }
    }

    /** Result of the consent screen; null when the user backed out. */
    fun onConsentResult(data: Intent?) {
        handleAuthorization(authRepository.completeAuthorization(data))
    }

    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    private fun handleAuthorization(result: AuthorizationResult) {
        when (result) {
            AuthorizationResult.Granted -> _state.update { it.copy(isLoading = false, isSignedIn = true) }
            is AuthorizationResult.ConsentRequired -> _state.update { it.copy(pendingConsent = result.pendingIntent) }
            AuthorizationResult.Denied -> fail(LoginErrors.AccessDenied)
            is AuthorizationResult.Failed -> {
                Log.e(TAG, "Sheets authorization failed", result.cause)
                fail(LoginErrors.AuthorizationFailed)
            }
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(isLoading = false, pendingConsent = null, error = message) }
    }
}
