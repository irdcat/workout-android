package com.github.irdcat.workout.domain.settings

import kotlinx.coroutines.flow.Flow

/** The user's preferences; the defaults are what a new install starts with. */
data class AppSettings(
    /** Write logged results into each exercise's WYNIK cell. */
    val writeResultsToSheet: Boolean = true,
    /** Time the session from start to finish and show it on the summary. */
    val measureWorkoutTime: Boolean = true,
    /** Open the rest timer after each set that has a rest time. */
    val autoStartRestTimer: Boolean = true,
    /** Keep the display on while a session is open. */
    val keepScreenOn: Boolean = true,
    val restEndAlert: RestEndAlert = RestEndAlert.Vibrate,
    /** Step of the weight −/+ buttons. */
    val defaultWeightStepKg: Double = 2.5,
    /** For RPE-only targets, start from the weight used last time. */
    val prefillWeightFromLastTime: Boolean = true,
    /** Beta: leave Analysis out of the navigation bar. */
    val hideAnalysisTab: Boolean = false,
    /** Beta: leave Scores (the DOTS/Wilks calculator) out of the navigation bar. */
    val hideScoresTab: Boolean = false,
)

/** What happens when a rest timer reaches zero. */
enum class RestEndAlert { Vibrate, Sound, Both, Off }

interface SettingsRepository {

    val settings: Flow<AppSettings>

    /** Current value of [settings]. */
    suspend fun getSettings(): AppSettings

    /** Applies [transform] to the stored settings atomically. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
