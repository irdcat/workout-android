package com.github.irdcat.workout.domain.usecase

import android.content.Intent
import com.github.irdcat.workout.data.google.GoogleSheetsRepository

class SignInUseCase(private val repository: GoogleSheetsRepository) {
    suspend operator fun invoke(data: Intent) = repository.signIn(data)
}