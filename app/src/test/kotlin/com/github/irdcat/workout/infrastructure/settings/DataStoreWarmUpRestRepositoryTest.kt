package com.github.irdcat.workout.infrastructure.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The DataStore is a process-wide singleton, so each test uses exercises of its own.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DataStoreWarmUpRestRepositoryTest {

    private val repository = DataStoreWarmUpRestRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun nothingSavedForANewExercise() = runTest {
        assertNull(repository.observe("Never Trained").first())
    }

    @Test
    fun savedPerExerciseAndKeptForTheNextTime() = runTest {
        repository.save("Martwy ciąg", 90)
        repository.save("Wiosłowanie", 45)

        assertEquals(90, repository.observe("Martwy ciąg").first())
        assertEquals(45, repository.observe("Wiosłowanie").first())
        // A fresh instance (the app reopened) reads the same.
        assertEquals(90, DataStoreWarmUpRestRepository(ApplicationProvider.getApplicationContext()).observe("Martwy ciąg").first())
    }

    @Test
    fun theSameExerciseHoweverItIsWritten() = runTest {
        repository.save("Front  Squat ", 75)

        assertEquals(75, repository.observe("front squat").first())
    }

    @Test
    fun aNegativeTimeIsSavedAsNoRest() = runTest {
        repository.save("Hip Thrust", -15)

        assertEquals(0, repository.observe("Hip Thrust").first())
    }
}
