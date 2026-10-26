package com.github.irdcat.workout.infrastructure.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.StartSessionResult
import com.github.irdcat.workout.testing.fixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DataStoreActiveSessionRepositoryTest {

    private val clock = fixedClock("2026-06-03", "17:42")
    private val repository = DataStoreActiveSessionRepository(ApplicationProvider.getApplicationContext(), clock)

    // The DataStore is a process-wide singleton, so start every test from no session.
    @Before
    fun clear() = runTest { repository.end() }

    @Test
    fun startsAndPersistsASession() = runTest {
        val result = repository.start("CZERWIEC2026/1/T2")

        val expected = ActiveSession("CZERWIEC2026/1/T2", clock.instant())
        assertEquals(StartSessionResult.Started(expected), result)
        assertEquals(expected, repository.activeSession.first())

        // A fresh instance (the app reopened) still sees it.
        val reopened = DataStoreActiveSessionRepository(ApplicationProvider.getApplicationContext(), fixedClock("2026-06-04"))
        assertEquals(expected, reopened.getActiveSession())
    }

    @Test
    fun startingTheActiveWorkoutAgainKeepsItsStartTime() = runTest {
        repository.start("CZERWIEC2026/1/T2")
        val later = DataStoreActiveSessionRepository(ApplicationProvider.getApplicationContext(), fixedClock("2026-06-03", "18:00"))

        val result = later.start("CZERWIEC2026/1/T2")

        assertEquals(StartSessionResult.Started(ActiveSession("CZERWIEC2026/1/T2", clock.instant())), result)
    }

    @Test
    fun anotherWorkoutCantStartWhileOneIsActive() = runTest {
        repository.start("CZERWIEC2026/1/T2")

        val result = repository.start("CZERWIEC2026/1/T3")

        assertEquals(StartSessionResult.AnotherActive(ActiveSession("CZERWIEC2026/1/T2", clock.instant())), result)
        assertEquals("CZERWIEC2026/1/T2", repository.getActiveSession()?.workoutId)
    }

    @Test
    fun endClearsTheSession() = runTest {
        repository.start("CZERWIEC2026/1/T2")
        repository.end()

        assertNull(repository.getActiveSession())
        assertEquals(
            StartSessionResult.Started(ActiveSession("CZERWIEC2026/1/T3", clock.instant())),
            repository.start("CZERWIEC2026/1/T3"),
        )
    }
}
