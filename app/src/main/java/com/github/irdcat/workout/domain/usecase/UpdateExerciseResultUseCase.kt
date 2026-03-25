package com.github.irdcat.workout.domain.usecase

import com.github.irdcat.workout.domain.SheetsRepository

class UpdateExerciseResultUseCase(private val repository: SheetsRepository) {
    suspend operator fun invoke(references: Map<String, String>) = repository.updateSheet(references)
}