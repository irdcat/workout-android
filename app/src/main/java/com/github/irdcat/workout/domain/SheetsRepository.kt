package com.github.irdcat.workout.domain

import android.content.Intent
import com.github.irdcat.workout.domain.model.WorkoutWeek
import kotlinx.coroutines.flow.Flow

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class DataLoadState {
    object Loading : DataLoadState()
    data class Success(val data: List<WorkoutWeek>) : DataLoadState()
    data class Error(val message: String) : DataLoadState()
}

interface SheetsRepository {

    suspend fun checkExistingAccount()
    fun getAuthState(): Flow<AuthState>
    suspend fun getSheetData(): Flow<DataLoadState>
    suspend fun signIn(data: Intent)
    fun getSignInIntent(): Intent
    suspend fun updateSheet(references: Map<String, String>)
}