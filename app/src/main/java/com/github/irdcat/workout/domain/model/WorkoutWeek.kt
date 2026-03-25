package com.github.irdcat.workout.domain.model

import java.time.LocalDate

data class WorkoutWeek(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: List<WorkoutDay>,
)
