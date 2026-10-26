package com.github.irdcat.workout.application.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.infrastructure.google.SheetsConfigDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface SettingsNavigation {
    data object SignedOut : SettingsNavigation
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    sheetsConfigDataStore: SheetsConfigDataStore,
    private val planRepository: WorkoutPlanRepository,
    private val clock: Clock,
    private val settingsRepository: SettingsRepository,
    private val syncWorkoutPlan: SyncWorkoutPlan,
    resultRepository: ResultRepository,
) : ViewModel() {

    private val isSyncing = MutableStateFlow(false)

    /** Last sync time, sheet/week counts, whether a sync is running and results waiting for the sheet. */
    private val sync = combine(planRepository.observeSyncInfo(), isSyncing, resultRepository.observeSaved()) { info, syncing, saved ->
        Triple(info, syncing, saved.size)
    }

    private val versionName: String = readVersionName()

    val state: StateFlow<Loadable<SettingsUiState>> = combine(
        authRepository.signedInEmail,
        sheetsConfigDataStore.sheetsConfigFlow(),
        settingsRepository.settings,
        sync,
    ) { email, config, settings, (syncInfo, syncing, unwritten) ->
        Loadable.Ready(
            SettingsUiState(
                email = email.orEmpty(),
                sheetUrl = config.sheetUrl,
                blacklist = config.sheetBlacklist.toList(),
                writeResults = settings.writeResultsToSheet,
                lastSynced = formatSyncedAt(syncInfo.lastSyncedAt, LocalDate.now(clock), clock.zone),
                savedSheets = syncInfo.sheetCount,
                savedWeeks = syncInfo.weekCount,
                isSyncing = syncing,
                unwrittenResults = unwritten,
                measureTime = settings.measureWorkoutTime,
                autoRestTimer = settings.autoStartRestTimer,
                keepScreenOn = settings.keepScreenOn,
                restAlert = settings.restEndAlert,
                weightStep = settings.defaultWeightStepKg.kg(),
                prefillWeight = settings.prefillWeightFromLastTime,
                hideAnalysisTab = settings.hideAnalysisTab,
                versionName = versionName,
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    private val _navigation = Channel<SettingsNavigation>(Channel.BUFFERED)
    val navigation: Flow<SettingsNavigation> = _navigation.receiveAsFlow()

    /** Handles every [SettingsEvent] except navigation, which the route handles. */
    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.SignOut -> viewModelScope.launch {
                authRepository.signOut()
                _navigation.send(SettingsNavigation.SignedOut)
            }
            is SettingsEvent.WriteResultsChanged -> updateSettings { it.copy(writeResultsToSheet = event.enabled) }
            SettingsEvent.SyncNow -> syncNow()
            SettingsEvent.ClearSavedData -> runSync {
                // Only the plan: logged sets and results not in the sheet yet are kept apart and stay.
                planRepository.clearCache()
                syncWorkoutPlan()
            }
            is SettingsEvent.MeasureTimeChanged -> updateSettings { it.copy(measureWorkoutTime = event.enabled) }
            is SettingsEvent.AutoRestTimerChanged -> updateSettings { it.copy(autoStartRestTimer = event.enabled) }
            is SettingsEvent.KeepScreenOnChanged -> updateSettings { it.copy(keepScreenOn = event.enabled) }
            is SettingsEvent.RestAlertSelected -> updateSettings { it.copy(restEndAlert = event.alert) }
            is SettingsEvent.WeightStepSelected -> event.step.toDoubleOrNull()?.let { step ->
                updateSettings { it.copy(defaultWeightStepKg = step) }
            }
            is SettingsEvent.PrefillWeightChanged -> updateSettings { it.copy(prefillWeightFromLastTime = event.enabled) }
            is SettingsEvent.HideAnalysisTabChanged -> updateSettings { it.copy(hideAnalysisTab = event.hidden) }
            SettingsEvent.EditSpreadsheet, SettingsEvent.EditBlacklist -> Unit // Navigation; handled by the route.
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** Reads the sheet, then writes results saved in the app (when writing is on). */
    private fun syncNow() = runSync { syncWorkoutPlan() }

    /** Runs [block] unless a sync is already running; Settings shows the progress. */
    private fun runSync(block: suspend () -> Unit) {
        if (isSyncing.value) return
        isSyncing.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                isSyncing.value = false
            }
        }
    }

    private fun readVersionName(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return info.versionName.orEmpty()
    }
}
