package com.github.irdcat.workout.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.irdcat.workout.ui.screens.SignInErrorScreen
import com.github.irdcat.workout.ui.screens.LoadingScreen
import com.github.irdcat.workout.ui.screens.SignInScreen

@Composable
fun Application() {
    val context = LocalContext.current
    val factory = remember(context) { ApplicationViewModel.Factory(context.applicationContext) }
    val viewModel: ApplicationViewModel = viewModel(factory = factory)

    val uiState by viewModel.uiState.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.handleSignInResult(result.data)
        }
    }

    when(uiState) {
        is UiState.Loading -> {
            LoadingScreen(message = "Loading...")
        }
        is UiState.Unauthenticated -> {
            SignInScreen {
                signInLauncher.launch(viewModel.getSignInIntent())
            }
        }
        is UiState.Success -> {
            val data = (uiState as UiState.Success).data
            AppRouter(data, viewModel.updateExerciseResultUseCase)
        }
        is UiState.Error -> {
            val message = (uiState as UiState.Error).message
            SignInErrorScreen(message) {
                signInLauncher.launch(viewModel.getSignInIntent())
            }
        }
    }
}

