package com.github.irdcat.workout.domain.model

data class WorkoutDay(
    val index: String,
    val exercises: List<ExerciseEntry>
) {
     val workoutName: String
        get() {
            return index.takeIf {
                it.length == 2 && it.startsWith('L') || it.startsWith('U')
            }?.let {
                if(it.startsWith('L')) {
                    "Lower ${index[1]}"
                } else if (it.startsWith('U')) {
                    "Upper ${index[1]}"
                } else {
                    index
                }
            } ?: index
        }

    fun isCompleted() = exercises.all {
        val resultValues = it.results.values
        resultValues.isNotEmpty() && resultValues.all {
            result -> result.isNotBlank()
        }
    }
}
