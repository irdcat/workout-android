package com.github.irdcat.workout.domain.session

import com.github.irdcat.workout.domain.results.ResultTarget
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.RecordingRestAlarm
import com.github.irdcat.workout.testing.fixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AbandonSessionTest {

    private val clock = fixedClock("2026-06-03")
    private val session = ActiveSession("W/1/T2", clock.instant())
    private val earlier = ActiveSession("W/1/T2", clock.instant().minus(Duration.ofDays(7)))

    private val sessions = FakeActiveSessionRepository(clock, session)
    private val logs = FakeSessionLogRepository()
    private val results = FakeResultRepository()
    private val alarm = RecordingRestAlarm()
    private val abandon = AbandonSession(sessions, logs, results, alarm)

    private fun set(at: ActiveSession) =
        LoggedSet(exerciseId = "rdl", setGroupId = "T2!5", weightKg = 60.0, reps = 6, rpe = null, warmUp = false, loggedAt = at.startedAt)

    private fun result(workoutId: String, number: String, savedAt: Instant) =
        SavedResult(ResultTarget(workoutId, number, 0, "RDL"), "60×6", savedAt)

    @Test
    fun deletesWhatWasLoggedAndSavedInTheSessionAndEndsIt() = runTest {
        logs.logSet(session, set(session))
        logs.logSet(earlier, set(earlier))
        val duringSession = result("W/1/T2", "1", session.startedAt.plusSeconds(600))
        val beforeSession = result("W/1/T2", "2", earlier.startedAt)
        val otherWorkout = result("W/1/T1", "1", session.startedAt.plusSeconds(600))
        results.save(duringSession)
        results.save(beforeSession)
        results.save(otherWorkout)
        alarm.schedule(clock.instant().plusSeconds(90), RestEndAlert.Sound)

        abandon()

        assertEquals(emptyList<LoggedSet>(), logs.observe(session).first().sets)
        assertEquals(1, logs.observe(earlier).first().sets.size)
        assertEquals(listOf(beforeSession, otherWorkout), results.saved.value)
        assertNull(alarm.scheduled)
        assertNull(sessions.session.value)
    }

    @Test
    fun doesNothingWithoutASession() = runTest {
        sessions.end()
        val saved = result("W/1/T2", "1", session.startedAt.plusSeconds(600))
        results.save(saved)

        abandon()

        assertEquals(listOf(saved), results.saved.value)
        assertEquals(emptyList<Any?>(), alarm.history)
    }
}
