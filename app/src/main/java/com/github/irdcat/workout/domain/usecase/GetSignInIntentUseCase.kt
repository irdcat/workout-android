package com.github.irdcat.workout.domain.usecase

import com.github.irdcat.workout.domain.SheetsRepository

class GetSignInIntentUseCase(private val repository: SheetsRepository) {
    operator fun invoke() = repository.getSignInIntent()
}