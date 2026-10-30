package com.github.irdcat.workout.infrastructure.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val WriteResultsKey = booleanPreferencesKey("write_results_to_sheet")
private val MeasureTimeKey = booleanPreferencesKey("measure_workout_time")
private val AutoRestTimerKey = booleanPreferencesKey("auto_start_rest_timer")
private val KeepScreenOnKey = booleanPreferencesKey("keep_screen_on")
private val RestEndAlertKey = stringPreferencesKey("rest_end_alert")
private val WeightStepKey = doublePreferencesKey("default_weight_step_kg")
private val PrefillWeightKey = booleanPreferencesKey("prefill_weight_from_last_time")
private val HideAnalysisTabKey = booleanPreferencesKey("hide_analysis_tab")
private val HideScoresTabKey = booleanPreferencesKey("hide_scores_tab")

/** Settings in their own DataStore file; unset keys fall back to [AppSettings]' defaults. */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun getSettings(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences.write(transform(preferences.toSettings()))
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            writeResultsToSheet = this[WriteResultsKey] ?: defaults.writeResultsToSheet,
            measureWorkoutTime = this[MeasureTimeKey] ?: defaults.measureWorkoutTime,
            autoStartRestTimer = this[AutoRestTimerKey] ?: defaults.autoStartRestTimer,
            keepScreenOn = this[KeepScreenOnKey] ?: defaults.keepScreenOn,
            // A value from a future version that this one doesn't know falls back to the default.
            restEndAlert = this[RestEndAlertKey]?.let { name -> RestEndAlert.entries.firstOrNull { it.name == name } }
                ?: defaults.restEndAlert,
            defaultWeightStepKg = this[WeightStepKey] ?: defaults.defaultWeightStepKg,
            prefillWeightFromLastTime = this[PrefillWeightKey] ?: defaults.prefillWeightFromLastTime,
            hideAnalysisTab = this[HideAnalysisTabKey] ?: defaults.hideAnalysisTab,
            hideScoresTab = this[HideScoresTabKey] ?: defaults.hideScoresTab,
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[WriteResultsKey] = settings.writeResultsToSheet
        this[MeasureTimeKey] = settings.measureWorkoutTime
        this[AutoRestTimerKey] = settings.autoStartRestTimer
        this[KeepScreenOnKey] = settings.keepScreenOn
        this[RestEndAlertKey] = settings.restEndAlert.name
        this[WeightStepKey] = settings.defaultWeightStepKg
        this[PrefillWeightKey] = settings.prefillWeightFromLastTime
        this[HideAnalysisTabKey] = settings.hideAnalysisTab
        this[HideScoresTabKey] = settings.hideScoresTab
    }
}
