package com.github.irdcat.workout.domain.usecase

import com.github.irdcat.workout.data.google.GoogleSheetsRepository

class GetAuthStateUseCase(private val repository: GoogleSheetsRepository) {
    operator fun invoke() = repository.getAuthState()
}