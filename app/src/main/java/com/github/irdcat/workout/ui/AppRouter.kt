package com.github.irdcat.workout.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.irdcat.workout.domain.model.WorkoutWeek
import com.github.irdcat.workout.domain.usecase.UpdateExerciseResultUseCase
import com.github.irdcat.workout.ui.screens.WorkoutWeekScreen
import com.github.irdcat.workout.ui.screens.SignInErrorScreen
import com.github.irdcat.workout.ui.screens.WorkoutDayScreen
import com.github.irdcat.workout.ui.screens.WorkoutWeekListScreen
import java.time.LocalDate

@Composable
fun AppRouter(
    data: List<WorkoutWeek>,
    updateExerciseResultUseCase: UpdateExerciseResultUseCase
) {
    val navController = rememberNavController()
    val now = LocalDate.now()
    val currentWeek = data.first {
        now.betweenInclusive(it.startDate, it.endDate)
    }

    NavHost(navController, startDestination = "current") {
        composable("current") {
            WorkoutWeekScreen("Workout", currentWeek, navController)
        }
        composable("history") {
            // Get past weeks data
            val pastWeeks = data
                .filter { now.isAfter(it.endDate) }
                .sortedByDescending { it.startDate }
            WorkoutWeekListScreen("History", pastWeeks, navController)
        }
        composable("upcoming") {
            // Get upcoming weeks data
            val upcomingWeeks = data
                .filter { now.isBefore(it.startDate) }
                .sortedBy { it.startDate }
            WorkoutWeekListScreen("Upcoming", upcomingWeeks, navController)
        }
        composable("workout/{weekStart}") { backStackEntry ->
            val weekStartString = backStackEntry.arguments?.getString("weekStart") ?: ""

            if (weekStartString.isBlank()) {
                SignInErrorScreen("Invalid path workout/$weekStartString")
            }
            val weekStartDate = LocalDate.parse(weekStartString)
            val week = data.first { it.startDate.isEqual(weekStartDate) }

            WorkoutWeekScreen("${week.startDate} - ${week.endDate}", week, navController)
        }
        composable("workout/{weekStart}/{day}") { backStackEntry ->
            val weekStartString = backStackEntry.arguments?.getString("weekStart") ?: ""
            val dayString = backStackEntry.arguments?.getString("day") ?: ""

            if (weekStartString.isBlank() || dayString.isBlank()) {
                SignInErrorScreen("Invalid path workout/$weekStartString/$dayString")
            }

            val weekStartDate = LocalDate.parse(weekStartString)
            val week = data.first { it.startDate.isEqual(weekStartDate) }
            val day = week.days.first { it.index == dayString }
            WorkoutDayScreen(day, navController)
        }
        composable("workout/{weekStart}/{day}/edit") { backStackEntry ->
            val weekStartString = backStackEntry.arguments?.getString("weekStart") ?: ""
            val dayString = backStackEntry.arguments?.getString("day") ?: ""

            if (weekStartString.isBlank() || dayString.isBlank()) {
                SignInErrorScreen("Invalid path workout/$weekStartString/$dayString")
            }

            val weekStartDate = LocalDate.parse(weekStartString)
            val week = data.first { it.startDate.isEqual(weekStartDate) }
            val day = week.days.first { it.index == dayString }
            WorkoutDayScreen(day, navController, updateExerciseResultUseCase)
        }
    }
}

fun LocalDate.betweenInclusive(start: LocalDate, end: LocalDate): Boolean {
    return (isEqual(start) || isAfter(start))
            && (isEqual(end) || isBefore(end))
}