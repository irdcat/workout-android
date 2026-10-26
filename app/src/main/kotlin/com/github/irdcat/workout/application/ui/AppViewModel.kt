package com.github.irdcat.workout.application.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.MainTab
import com.github.irdcat.workout.application.ui.components.visibleMainTabs
import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.infrastructure.google.SheetsConfigDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Decides where the app opens: sign-in, first-time setup, or the current week. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sheetsConfigDataStore: SheetsConfigDataStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<Route?>(null)

    /** null while the saved account and spreadsheet are read. */
    val startDestination: StateFlow<Route?> = _startDestination.asStateFlow()

    /** The "keep screen on" setting, applied while training (session and rest timer). */
    val keepScreenOnWhileTraining: StateFlow<Boolean> = settingsRepository.settings
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Tabs in the navigation bar; Analysis can be hidden in beta settings. */
    val mainTabs: StateFlow<List<MainTab>> = settingsRepository.settings
        .map { visibleMainTabs(it.hideAnalysisTab) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainTab.entries.toList())

    init {
        viewModelScope.launch {
            _startDestination.value =
                if (authRepository.getSignedInEmail() == null) Route.Login else destinationAfterSignIn()
        }
    }

    /** Setup until a spreadsheet link is saved, then Home. */
    suspend fun destinationAfterSignIn(): Route =
        if (sheetsConfigDataStore.sheetsConfigFlow().first().isConfigured()) Route.Home else Route.Setup()
}
