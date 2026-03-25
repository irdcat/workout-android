package com.github.irdcat.workout.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.data.google.GoogleSheetsDataSource
import com.github.irdcat.workout.data.google.GoogleSheetsRepository
import com.github.irdcat.workout.data.google.GoogleSignInDataSource
import com.github.irdcat.workout.domain.AuthState
import com.github.irdcat.workout.domain.DataLoadState
import com.github.irdcat.workout.domain.model.WorkoutWeek
import com.github.irdcat.workout.domain.usecase.CheckExistingAccountUseCase
import com.github.irdcat.workout.domain.usecase.GetAuthStateUseCase
import com.github.irdcat.workout.domain.usecase.GetSignInIntentUseCase
import com.github.irdcat.workout.domain.usecase.GetWorkoutsUseCase
import com.github.irdcat.workout.domain.usecase.SignInUseCase
import com.github.irdcat.workout.domain.usecase.UpdateExerciseResultUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class UiState {
    object Loading : UiState()
    object Unauthenticated : UiState()
    data class Success(val data: List<WorkoutWeek>) : UiState()
    data class Error(val message: String) : UiState()
}

class ApplicationViewModel(
    private val checkExistingAccountUseCase: CheckExistingAccountUseCase,
    private val getAuthStateUseCase: GetAuthStateUseCase,
    private val getWorkoutsUseCase: GetWorkoutsUseCase,
    private val signInUseCase: SignInUseCase,
    private val getSignInIntentUseCase: GetSignInIntentUseCase,
    val updateExerciseResultUseCase: UpdateExerciseResultUseCase
): ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            checkExistingAccountUseCase()
        }
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            getAuthStateUseCase()
                .catch { e ->
                    _uiState.value = UiState.Error(e.message ?: "Unknown Auth Error")
                }
                .collect { authState ->
                    when(authState) {
                        is AuthState.Unauthenticated -> {
                            _uiState.value = UiState.Unauthenticated
                        }
                        is AuthState.Authenticating -> {
                            _uiState.value = UiState.Loading
                        }
                        is AuthState.Authenticated -> {
                            loadSheetData()
                        }
                        is AuthState.Error -> {
                            _uiState.value = UiState.Error(authState.message)
                        }
                    }
                }
        }
    }

    fun loadSheetData() {
        viewModelScope.launch {
            getWorkoutsUseCase()
                .catch { e ->
                    _uiState.value = UiState.Error(e.message ?: "Data load error")
                }
                .collect { dataLoadState ->
                    when(dataLoadState) {
                        is DataLoadState.Loading -> {
                            _uiState.value = UiState.Loading
                        }
                        is DataLoadState.Success -> {
                            _uiState.value = UiState.Success(dataLoadState.data)
                        }
                        is DataLoadState.Error -> {
                            _uiState.value = UiState.Error(dataLoadState.message)
                        }
                    }
                }
        }
    }

    fun handleSignInResult(data: Intent?) {
        data?.let {
            viewModelScope.launch {
                signInUseCase(it)
            }
        }
    }

    fun getSignInIntent(): Intent = getSignInIntentUseCase()

    class Factory(private val context: Context): ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ApplicationViewModel::class.java)) {

                val application = context.applicationContext

                val googleSignInDataSource = GoogleSignInDataSource(application)
                val googleSheetsDataSource = GoogleSheetsDataSource(application)

                val repository = GoogleSheetsRepository(
                    googleSignInDataSource,
                    googleSheetsDataSource,
                    application
                )

                val checkExistingAccountUseCase = CheckExistingAccountUseCase(repository)
                val getAuthStateUseCase = GetAuthStateUseCase(repository)
                val getWorkoutsUseCase = GetWorkoutsUseCase(repository)
                val signInUseCase = SignInUseCase(repository)
                val getSignInIntentUseCase = GetSignInIntentUseCase(repository)
                val updateExerciseResultUseCase = UpdateExerciseResultUseCase(repository)

                return ApplicationViewModel(
                    checkExistingAccountUseCase,
                    getAuthStateUseCase,
                    getWorkoutsUseCase,
                    signInUseCase,
                    getSignInIntentUseCase,
                    updateExerciseResultUseCase
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class ${modelClass.simpleName}")
        }
    }
}