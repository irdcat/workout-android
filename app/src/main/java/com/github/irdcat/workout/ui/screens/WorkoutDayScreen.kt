package com.github.irdcat.workout.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.composables.composetheme.ComposeTheme
import com.composables.composetheme.textStyles
import com.composables.composetheme.xs
import com.github.irdcat.workout.domain.model.WorkoutDay
import com.github.irdcat.workout.domain.usecase.UpdateExerciseResultUseCase
import com.github.irdcat.workout.ui.components.ExerciseEntrySummary
import com.github.irdcat.workout.ui.components.TimerDialog
import com.github.irdcat.workout.ui.components.WorkoutAppBar
import com.github.irdcat.workout.ui.components.icons.ArrowLeft
import com.github.irdcat.workout.ui.components.icons.ArrowRight
import com.github.irdcat.workout.ui.components.icons.Timer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDayScreen(
    day: WorkoutDay,
    navController: NavController,
    updateExerciseResultUseCase: UpdateExerciseResultUseCase? = null
) {
    var displayedExerciseIndex by remember { mutableIntStateOf(0) }
    var previouslyDisplayedExerciseIndex by remember { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    val readonly = updateExerciseResultUseCase == null

    Scaffold(
        topBar = {
            WorkoutAppBar(day.workoutName, navController)
        },
        bottomBar = {
            WorkoutDayNavigationBar(
                prevEnabled = displayedExerciseIndex != 0,
                onPrev = {
                    displayedExerciseIndex -= 1
                },
                timerEnabled = !readonly,
                onTimer = {
                    showDialog = true
                },
                nextEnabled = displayedExerciseIndex != day.exercises.size - 1,
                onNext = {
                    displayedExerciseIndex += 1
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            day.exercises.forEachIndexed { index, exercise ->
                AnimatedVisibility(
                    visible = index == displayedExerciseIndex,
                    enter = enterAnimation(previouslyDisplayedExerciseIndex, displayedExerciseIndex),
                    exit = exitAnimation(previouslyDisplayedExerciseIndex, displayedExerciseIndex)
                ) {
                    ExerciseEntrySummary(exercise, updateExerciseResultUseCase)
                }
            }
        }
        if (showDialog) {
            TimerDialog {
                showDialog = false
            }
        }
    }
}

private fun slideInFromRight() = slideInHorizontally(initialOffsetX = { -it })
private fun slideInFromLeft() = slideInHorizontally(initialOffsetX = { it })

private fun slideOutToRight() = slideOutHorizontally(targetOffsetX = { -it })
private fun slideOutToLeft() = slideOutHorizontally(targetOffsetX = { it })

private fun enterAnimation(prev: Int, next: Int) =
    if (prev < next) {
        slideInFromLeft()
    } else {
        slideInFromRight()
    }

private fun exitAnimation(prev: Int, next: Int) =
    if (prev < next) {
        slideOutToRight()
    } else {
        slideOutToLeft()
    }

@Composable
private fun WorkoutDayNavigationBar(
    prevEnabled: Boolean,
    onPrev: () -> Unit,
    timerEnabled: Boolean,
    onTimer: () -> Unit,
    nextEnabled: Boolean,
    onNext: () -> Unit
) {
    NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
        NavigationBarItem(
            selected = false,
            enabled = prevEnabled,
            icon = {
                Icon(ArrowLeft, contentDescription = "Previous exercise")
            },
            label = {
                Text("Previous", style = ComposeTheme.textStyles.xs)
            },
            onClick = onPrev
        )
        NavigationBarItem(
            selected = false,
            enabled = timerEnabled,
            icon = {
                Icon(Timer, contentDescription = "Show timer at the bottom")
            },
            label = {
                Text("Show timer", style = ComposeTheme.textStyles.xs)
            },
            onClick = onTimer
        )
        NavigationBarItem(
            selected = false,
            enabled = nextEnabled,
            icon = {
                Icon(ArrowRight, contentDescription = "Next exercise")
            },
            label = {
                Text("Next", style = ComposeTheme.textStyles.xs)
            },
            onClick = onNext
        )
    }
}