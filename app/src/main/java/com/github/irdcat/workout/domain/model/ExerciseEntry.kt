package com.github.irdcat.workout.domain.model

data class ExerciseEntry(
    val sets: List<ExerciseSet>,
    val results: Map<String, String> = mapOf(),
    val resultDestinations: Map<String, String> = mapOf()
) {
    fun isCompleted() = results.isNotEmpty()
            && results.values.all { it.isNotBlank() }
}
