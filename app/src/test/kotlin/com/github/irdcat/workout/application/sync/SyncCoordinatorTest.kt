package com.github.irdcat.workout.application.sync

import com.github.irdcat.workout.domain.workout.SyncResult
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.fixedClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SyncCoordinatorTest {

    private val fake = FakeWorkoutPlanRepository()

    /** Each sync waits here until the test lets it finish. */
    private var gate = CompletableDeferred<Unit>()

    private val plan = object : WorkoutPlanRepository by fake {
        override suspend fun sync(): SyncResult {
            gate.await()
            return fake.sync()
        }
    }

    private fun TestScope.coordinator(): SyncCoordinator {
        val settings = FakeSettingsRepository()
        val sync = SyncWorkoutPlan(plan, FakeActiveSessionRepository(fixedClock("2026-06-03")), FakeResultRepository(), settings)
        return SyncCoordinator(sync, plan, backgroundScope)
    }

    @Test
    fun showsASyncWhileItRuns() = runTest {
        val coordinator = coordinator()

        coordinator.sync()
        runCurrent()
        assertTrue(coordinator.isSyncing.value)

        gate.complete(Unit)
        runCurrent()
        assertFalse(coordinator.isSyncing.value)
        assertEquals(SyncResult.Success(0, 0), coordinator.lastResult.value)
    }

    @Test
    fun askingAgainWhileSyncingJoinsTheRunningSync() = runTest {
        val coordinator = coordinator()

        val first = coordinator.sync()
        val second = coordinator.sync()
        val clear = coordinator.clearAndSync()
        gate.complete(Unit)
        runCurrent()

        assertSame(first, second)
        assertSame(first, clear)
        assertEquals(1, fake.syncCalls)
    }

    @Test
    fun syncAfterChangesRunsAgainOnceTheRunningSyncEnds() = runTest {
        val coordinator = coordinator()

        coordinator.sync()
        runCurrent()
        coordinator.syncAfterChanges()
        gate.complete(Unit)
        runCurrent()

        assertEquals(2, fake.syncCalls)
        assertFalse(coordinator.isSyncing.value)
    }

    @Test
    fun remembersAFailedSync() = runTest {
        val coordinator = coordinator()
        assertNull(coordinator.lastResult.value)
        fake.syncResult = SyncResult.Failed(IOException("no network"))
        gate.complete(Unit)

        coordinator.sync().await()

        assertTrue(coordinator.lastResult.value is SyncResult.Failed)
    }
}
