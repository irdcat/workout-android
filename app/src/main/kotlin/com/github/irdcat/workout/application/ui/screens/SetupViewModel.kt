package com.github.irdcat.workout.application.ui.screens

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.infrastructure.google.SheetsConfig
import com.github.irdcat.workout.infrastructure.google.SheetsConfigDataStore
import com.github.irdcat.workout.infrastructure.google.SheetsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SetupFormState(
    val email: String = "",
    val sheetUrl: String = "",
    /** Sheet names found at [sheetUrl]; null until the link is checked. */
    val availableSheets: List<String>? = null,
    val blacklist: List<String> = emptyList(),
    val blacklistInput: String = "",
    val writeResults: Boolean = true,
    val isSaving: Boolean = false,
    /** False until the saved account, link and settings are read; the form can't be edited before. */
    val isLoaded: Boolean = false,
) {
    val sheetsToLoad: List<String> get() = availableSheets.orEmpty().filterNot { it in blacklist }

    /**
     * Sheets in the spreadsheet that could be blacklisted, matching what's typed (ignoring
     * case); names starting with it come first. All of them while nothing is typed.
     */
    val blacklistSuggestions: List<String>
        get() {
            val query = blacklistInput.trim()
            return sheetsToLoad
                .filter { it.contains(query, ignoreCase = true) }
                .sortedBy { !it.startsWith(query, ignoreCase = true) }
        }
}

sealed interface SetupNavigation {
    data object Saved : SetupNavigation
    data object SignedOut : SetupNavigation
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sheetsConfigDataStore: SheetsConfigDataStore,
    private val sheetsRepository: SheetsRepository,
    private val planRepository: WorkoutPlanRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupFormState())
    val state: StateFlow<SetupFormState> = _state.asStateFlow()

    private var savedConfig: SheetsConfig? = null

    private val _navigation = Channel<SetupNavigation>(Channel.BUFFERED)
    val navigation: Flow<SetupNavigation> = _navigation.receiveAsFlow()

    init {
        viewModelScope.launch {
            val email = authRepository.getSignedInEmail().orEmpty()
            val config = sheetsConfigDataStore.sheetsConfigFlow().first()
            val settings = settingsRepository.getSettings()
            savedConfig = config
            _state.update {
                it.copy(
                    email = email,
                    sheetUrl = config.sheetUrl,
                    blacklist = config.sheetBlacklist.toList(),
                    writeResults = settings.writeResultsToSheet,
                    isLoaded = true,
                )
            }
            if (config.sheetUrl.isNotBlank()) lookUpSheets(config.sheetUrl)

            // Check the link once typing pauses. The saved link, already checked above,
            // doesn't count as typing; anything typed since loading does.
            _state.map { it.sheetUrl }
                .distinctUntilChanged()
                .dropWhile { it == config.sheetUrl }
                .debounce(600)
                .collect { lookUpSheets(it) }
        }
    }

    fun onSheetUrlChange(url: String) {
        edit { it.copy(sheetUrl = url, availableSheets = null) }
    }

    fun onRemoveFromBlacklist(name: String) {
        edit { it.copy(blacklist = it.blacklist - name) }
    }

    fun onBlacklistInputChange(value: String) {
        edit { it.copy(blacklistInput = value) }
    }

    /**
     * Adds what's typed. Sheets are skipped by their exact name, so a name typed in
     * another case becomes the spreadsheet's own spelling of it.
     */
    fun onAddToBlacklist() {
        edit { form ->
            val typed = form.blacklistInput.trim()
            val name = form.availableSheets?.firstOrNull { it.equals(typed, ignoreCase = true) } ?: typed
            form.withBlacklisted(name)
        }
    }

    fun onPickBlacklistSuggestion(name: String) {
        edit { it.withBlacklisted(name) }
    }

    private fun SetupFormState.withBlacklisted(name: String): SetupFormState =
        if (name.isEmpty() || name in blacklist) copy(blacklistInput = "") else copy(blacklist = blacklist + name, blacklistInput = "")

    fun onWriteResultsChange(enabled: Boolean) {
        edit { it.copy(writeResults = enabled) }
    }

    fun onSignOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _navigation.send(SetupNavigation.SignedOut)
        }
    }

    fun onSave() {
        val form = _state.value
        if (!form.isLoaded || form.isSaving || form.sheetUrl.isBlank()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val sheetUrl = form.sheetUrl.trim()
            val blacklist = form.blacklist.toSet()
            sheetsConfigDataStore.updateSheetsConfig(sheetUrl, blacklist)
            // A different sheet or blacklist makes the cached plan wrong; Home re-syncs an empty cache.
            if (savedConfig?.sheetUrl != sheetUrl || savedConfig?.sheetBlacklist != blacklist) {
                planRepository.clearCache()
            }
            settingsRepository.update { it.copy(writeResultsToSheet = form.writeResults) }
            _state.update { it.copy(isSaving = false) }
            _navigation.send(SetupNavigation.Saved)
        }
    }

    /** Loading replaces the whole form, so edits made before it finishes would be lost. */
    private fun edit(transform: (SetupFormState) -> SetupFormState) {
        _state.update { if (it.isLoaded) transform(it) else it }
    }

    private suspend fun lookUpSheets(url: String) {
        if (url.isBlank()) return
        // null when the link has no spreadsheet ID or can't be opened.
        val names = runCatching {
            sheetsRepository.listSheets(url).sortedBy { it.index }.map { it.name }
        }.getOrNull()
        _state.update { if (it.sheetUrl == url) it.copy(availableSheets = names) else it }
    }
}
