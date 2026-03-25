package com.github.irdcat.workout.ui

import java.time.LocalDate

const val CURRENT_WEEK_SCREEN_PATH = "current"
const val HISTORY_SCREEN_PATH = "history"
const val UPCOMING_SCREEN_PATH = "upcoming"

fun buildWorkoutWeekScreenPath(weekStart: LocalDate) =
    "workout/${weekStart}"

fun buildWorkoutDayScreenPath(weekStart: LocalDate, day: String) =
    "workout/$weekStart/$day"

fun buildWorkoutDayScreenEditPath(weekStart: LocalDate, day: String) =
    buildWorkoutDayScreenPath(weekStart, day).plus("/edit")