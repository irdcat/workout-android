package com.github.irdcat.workout.domain.workout

import com.github.irdcat.workout.domain.results.ResultTarget
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.results.WriteResultsOutcome
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SyncWorkoutPlanTest {

    private val clock = fixedClock("2026-06-03")
    private val t1 = workout("W/1/T1", "T1", single(exercise("e1", "1", "Przysiad", group())))
    private val t2 = workout("W/1/T2", "T2", single(exercise("e2", "1", "RDL", group())))
    private val session = ActiveSession("W/1/T2", clock.instant())

    private val plan = FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", t1, t2)))
    private val sessions = FakeActiveSessionRepository(clock, session)
    private val results = FakeResultRepository()
    private val settings = FakeSettingsRepository()
    private val sync = SyncWorkoutPlan(plan, sessions, results, settings)

    private val saved = SavedResult(ResultTarget("W/1/T1", "1", 0, "Przysiad"), "RPE 9", clock.instant())

    @Test
    fun endsTheSessionWhenItsWorkoutIsGoneAfterASync() = runTest {
        plan.monthsAfterSync = months(week("w1", "2026-06-01", t1))

        val outcome = sync()

        assertEquals(session, outcome.endedSession)
        assertNull(sessions.session.value)
    }

    @Test
    fun keepsTheSessionWhenItsWorkoutIsStillThere() = runTest {
        plan.monthsAfterSync = months(week("w1", "2026-06-01", t1, t2))

        val outcome = sync()

        assertNull(outcome.endedSession)
        assertEquals(session, sessions.session.value)
    }

    @Test
    fun aFailedSyncNeverEndsTheSession() = runTest {
        plan.months.value = emptyList()
        plan.syncResult = SyncResult.Failed(IOException("offline"))

        val outcome = sync()

        assertEquals(SyncResult.Failed::class, outcome.result::class)
        assertNull(outcome.endedSession)
        assertEquals(session, sessions.session.value)
    }

    @Test
    fun withoutASessionThereIsNothingToEnd() = runTest {
        sessions.session.value = null

        assertNull(sync().endedSession)
    }

    // ---- Results -------------------------------------------------------------

    @Test
    fun savedResultsAreWrittenAfterTheSheetIsRead() = runTest {
        results.save(saved)

        val outcome = sync()

        assertEquals(1, plan.syncCalls)
        assertEquals(WriteResultsOutcome.Written(1), outcome.written)
        assertTrue(results.saved.value.isEmpty())
    }

    @Test
    fun withWritingOffResultsStayOnThePhone() = runTest {
        settings.update { it.copy(writeResultsToSheet = false) }
        results.save(saved)

        assertEquals(WriteResultsOutcome.NothingToWrite, sync().written)
        assertEquals(0, results.writeCalls)
        assertEquals(listOf(saved), results.saved.value)
    }

    @Test
    fun aFailedSyncWritesNothing() = runTest {
        // The rows come from what was just read: without a sync they may be out of date.
        plan.syncResult = SyncResult.Failed(IOException("offline"))
        results.save(saved)

        sync()

        assertEquals(0, results.writeCalls)
        assertEquals(listOf(saved), results.saved.value)
    }
}
