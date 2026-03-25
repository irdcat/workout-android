package com.github.irdcat.workout.domain.usecase

import com.github.irdcat.workout.domain.SheetsRepository

class CheckExistingAccountUseCase(private val repository: SheetsRepository) {
    suspend operator fun invoke() = repository.checkExistingAccount()
}