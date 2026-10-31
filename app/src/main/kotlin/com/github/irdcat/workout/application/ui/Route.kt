package com.github.irdcat.workout.application.ui

import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.screens.WORKOUT_ID_ARG
import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object Login : Route

    /** [editing] is true when opened from Settings: saving goes back instead of on to Home. */
    @Serializable
    data class Setup(val editing: Boolean = false) : Route

    // ---- Tabs ----------------------------------------------------------------

    @Serializable
    data object Home : Route

    @Serializable
    data object Browse : Route

    @Serializable
    data object Analysis : Route

    @Serializable
    data object Scores : Route

    @Serializable
    data object Settings : Route

    /** The exercise groups Analysis compares; opened from [Settings]. */
    @Serializable
    data object AnalysisGroups : Route

    // ---- Workout -------------------------------------------------------------

    /** The property name must match [WORKOUT_ID_ARG]; WorkoutDetailViewModel reads it from there. */
    @Serializable
    data class WorkoutDetail(val workoutId: String) : Route

    @Serializable
    data object Session : Route

    /** Warm-up sets for the exercise up next; opened over [Session]. */
    @Serializable
    data object WarmUp : Route

    @Serializable
    data object RestTimer : Route

    @Serializable
    data object Summary : Route
}

val MainTab.route: Route
    get() = when (this) {
        MainTab.Week -> Route.Home
        MainTab.Browse -> Route.Browse
        MainTab.Analysis -> Route.Analysis
        MainTab.Scores -> Route.Scores
        MainTab.Settings -> Route.Settings
    }
