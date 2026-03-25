package com.github.irdcat.workout.domain.usecase

import com.github.irdcat.workout.domain.SheetsRepository

class GetWorkoutsUseCase(private val repository: SheetsRepository) {
    suspend operator fun invoke() = repository.getSheetData()
}