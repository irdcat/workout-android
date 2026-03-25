package com.github.irdcat.workout.domain.model

data class ExerciseSet(
    val name: String,
    val reps: Reps,
    val weight: Weight,
    val tempo: Tempo,
    val rest: Rest
)
