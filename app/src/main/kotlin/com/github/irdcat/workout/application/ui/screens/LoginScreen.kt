package com.github.irdcat.workout.application.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.irdcat.workout.application.ui.theme.Colors
import com.github.irdcat.workout.application.ui.theme.TextStyles
import com.github.irdcat.workout.application.ui.theme.Shapes
import com.github.irdcat.workout.application.ui.theme.WorkoutTheme

// Google sign-in button follows Google's light button branding, not the app palette.
private val GoogleButtonContainer = Color(0xFFFFFFFF)
private val GoogleButtonContent = Color(0xFF1F1F1F)

/**
 * Login backed by [LoginViewModel]. Only navigation leaves this composable:
 * [onSignedIn] runs once the account is picked and Sheets access is granted.
 */
@Composable
fun LoginRoute(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentResult(if (result.resultCode == Activity.RESULT_OK) result.data else null)
    }

    LaunchedEffect(state.pendingConsent) {
        state.pendingConsent?.let { consent ->
            viewModel.onConsentLaunched()
            consentLauncher.launch(IntentSenderRequest.Builder(consent.intentSender).build())
        }
    }
    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) onSignedIn()
    }

    LoginScreen(
        onContinueWithGoogle = { viewModel.onContinueWithGoogle(context) },
        isLoading = state.isLoading,
        error = state.error,
    )
}

@Composable
fun LoginScreen(
    onContinueWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Colors.Canvas,
    ) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterVertically),
        ) {
            Brand(modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ContinueWithGoogleButton(
                    onClick = onContinueWithGoogle,
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = TextStyles.bodyMd,
                        color = Colors.Critical,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.FitnessCenter,
            contentDescription = null,
            tint = Colors.Primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "WORKOUT",
            style = TextStyles.headlineXl.copy(fontSize = 64.sp, lineHeight = 64.sp),
            color = Colors.TextPrimary,
        )
    }
}

@Composable
private fun ContinueWithGoogleButton(
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.heightIn(min = 56.dp),
        shape = Shapes.xl,
        colors = ButtonDefaults.buttonColors(
            containerColor = GoogleButtonContainer,
            contentColor = GoogleButtonContent,
            disabledContainerColor = GoogleButtonContainer,
            disabledContentColor = GoogleButtonContent,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = GoogleButtonContent,
                strokeWidth = 2.5.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Signing in…",
                style = TextStyles.bodyLg.copy(fontWeight = FontWeight.SemiBold),
            )
            return@Button
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(BorderStroke(2.dp, GoogleButtonContent), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "G",
                style = TextStyles.bodyMd.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Continue with Google",
            style = TextStyles.bodyLg.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Preview(
    name = "Login",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFF0B0F14,
)
@Composable
private fun LoginScreenPreview() {
    WorkoutTheme {
        LoginScreen(onContinueWithGoogle = {})
    }
}

@Preview(
    name = "Login, signing in",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFF0B0F14,
)
@Composable
private fun LoginScreenLoadingPreview() {
    WorkoutTheme {
        LoginScreen(onContinueWithGoogle = {}, isLoading = true)
    }
}

private class LoginErrorProvider : PreviewParameterProvider<String> {
    override val values = LoginErrors.all.asSequence()
}

/** One preview per error the view model can show. */
@Preview(
    name = "Login, error",
    widthDp = 390,
    heightDp = 844,
    showBackground = true,
    backgroundColor = 0xFF0B0F14,
)
@Composable
private fun LoginScreenErrorPreview(
    @PreviewParameter(LoginErrorProvider::class) error: String,
) {
    WorkoutTheme {
        LoginScreen(onContinueWithGoogle = {}, error = error)
    }
}
