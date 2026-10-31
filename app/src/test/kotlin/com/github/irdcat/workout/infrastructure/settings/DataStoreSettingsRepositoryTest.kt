package com.github.irdcat.workout.infrastructure.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.analysis.ExerciseGroup
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.RestEndAlert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DataStoreSettingsRepositoryTest {

    private val repository = DataStoreSettingsRepository(ApplicationProvider.getApplicationContext())

    // The DataStore is a process-wide singleton, so start every test from the defaults.
    @Before
    fun reset() = runTest { repository.update { AppSettings() } }

    @Test
    fun newInstallUsesTheDefaults() = runTest {
        assertEquals(AppSettings(), repository.getSettings())
    }

    @Test
    fun changesArePersisted() = runTest {
        val changed = AppSettings(
            writeResultsToSheet = false,
            measureWorkoutTime = false,
            autoStartRestTimer = false,
            keepScreenOn = false,
            restEndAlert = RestEndAlert.Both,
            defaultWeightStepKg = 0.5,
            prefillWeightFromLastTime = false,
            hideAnalysisTab = true,
            hideScoresTab = true,
            analysisGroups = listOf(ExerciseGroup("Ławka", listOf("Bench Press", "Wyciskanie leżąc")), ExerciseGroup("Empty", emptyList())),
        )

        repository.update { changed }

        assertEquals(changed, repository.settings.first())
        // A fresh instance (the app reopened) reads the same.
        assertEquals(changed, DataStoreSettingsRepository(ApplicationProvider.getApplicationContext()).getSettings())
    }

    @Test
    fun updateOnlyChangesWhatItTouches() = runTest {
        repository.update { it.copy(restEndAlert = RestEndAlert.Sound) }
        repository.update { it.copy(defaultWeightStepKg = 5.0) }

        assertEquals(AppSettings(restEndAlert = RestEndAlert.Sound, defaultWeightStepKg = 5.0), repository.getSettings())
    }
}
