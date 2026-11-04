package com.github.irdcat.workout.application.session

import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.RestClocks
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeSetRestRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWarmUpRestRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MutableClock
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.loggedSet
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the session notification shows, and when, against fakes of the session and the plan. */
class SessionNotifierTest {

    private val clock = MutableClock(fixedClock("2026-06-03").instant())
    private val rdl = exercise(
        "rdl", "1", "RDL",
        group(sets = 2, reps = Reps.Count(6), load = Load.Weight(60.0), rest = 150),
        group(sets = 1, reps = Reps.Count(5), load = Load.Weight(90.0), rest = 150),
    )
    private val t2 = workout("W/1/T2", "T2", single(rdl))
    private val plan = FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", t2)))
    private val session = ActiveSession("W/1/T2", clock.instant())
    private val sessions = FakeActiveSessionRepository(clock, session)
    private val logs = FakeSessionLogRepository()
    private val settings = FakeSettingsRepository()
    private val restClocks = RestClocks()
    private val notification = RecordingSessionNotification()
    private val foreground = MutableStateFlow(false)

    private fun TestScope.startNotifier() {
        val source = SessionStatusSource(
            sessions, plan, logs, settings, FakeWarmUpRestRepository(), FakeSetRestRepository(), restClocks, clock,
        )
        SessionNotifier(source, notification, backgroundScope).start(foreground)
        runCurrent()
    }

    private fun TestScope.letTimePass(seconds: Long) {
        clock.advance(seconds)
        testScheduler.advanceTimeBy(seconds * 1_000)
        runCurrent()
    }

    @Test
    fun inTheBackgroundTheSessionIsShownWithItsClock() = runTest {
        startNotifier()

        assertEquals(
            SessionNotificationContent("T2", "RDL · set 1 of 3", Chronometer(session.startedAt, countDown = false)),
            notification.shown,
        )
    }

    @Test
    fun withTheAppInSightThereIsNoNotification() = runTest {
        foreground.value = true
        startNotifier()
        assertNull(notification.shown)

        foreground.value = false
        runCurrent()
        assertEquals("T2", notification.shown?.title)

        foreground.value = true
        runCurrent()
        assertNull(notification.shown)
    }

    @Test
    fun aRestCountsDownToItsEndThenTheWorkoutClockIsBack() = runTest {
        logs.logSet(session, loggedSet("rdl", rdl.setGroups.first().id, kg = 60.0, reps = 6, at = clock.instant()))
        startNotifier()

        assertEquals(
            SessionNotificationContent(
                "T2", "RDL · resting · set 2 of 3 next", Chronometer(clock.instant().plusSeconds(150), countDown = true),
            ),
            notification.shown,
        )

        // The clock ticking doesn't post it again; only a change does.
        letTimePass(60)
        assertEquals(1, notification.posts)

        letTimePass(90)
        assertEquals(
            SessionNotificationContent("T2", "RDL · set 2 of 3", Chronometer(session.startedAt, countDown = false)),
            notification.shown,
        )
    }

    @Test
    fun aPausedRestShowsWhatIsLeft() = runTest {
        logs.logSet(session, loggedSet("rdl", rdl.setGroups.first().id, kg = 60.0, reps = 6, at = clock.instant()))
        clock.advance(19)
        restClocks.update(logs.logs.value.getValue(session).sets.single().id) { it.copy(pause = it.pause.toggled(clock.instant())) }
        startNotifier()

        assertEquals(
            SessionNotificationContent("T2", "RDL · rest paused, 2:11 left · set 2 of 3 next", Chronometer(session.startedAt, false)),
            notification.shown,
        )
    }

    @Test
    fun withoutMeasuringTimeThereIsNoClock() = runTest {
        settings.state.value = AppSettings(measureWorkoutTime = false)
        startNotifier()

        assertEquals(SessionNotificationContent("T2", "RDL · set 1 of 3", chronometer = null), notification.shown)
    }

    @Test
    fun endingTheSessionTakesTheNotificationAway() = runTest {
        startNotifier()
        assertEquals("T2", notification.shown?.title)

        sessions.end()
        runCurrent()

        assertNull(notification.shown)
    }
}

private class RecordingSessionNotification : SessionNotification {
    var shown: SessionNotificationContent? = null
        private set
    var posts = 0
        private set

    override fun show(content: SessionNotificationContent) {
        shown = content
        posts++
    }

    override fun hide() {
        shown = null
    }
}
