package com.github.irdcat.workout.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RestClocksTest {

    private val loggedAt = Instant.parse("2026-06-03T18:00:00Z")
    private fun at(seconds: Long): Instant = loggedAt.plusSeconds(seconds)

    @Test
    fun aPlainRestRunsFromTheSet() {
        val rest = RestChanges()

        assertEquals(150, rest.totalSeconds(150))
        assertEquals(120, rest.remainingSeconds(150, loggedAt, at(30)))
        assertEquals(-10, rest.remainingSeconds(150, loggedAt, at(160)))
        assertEquals(at(150), rest.endsAt(150, loggedAt))
    }

    @Test
    fun partOfASecondLeftStillShowsOne() {
        assertEquals(1, RestChanges().remainingSeconds(150, loggedAt, at(149).plusMillis(400)))
    }

    @Test
    fun pausedTimeDoesNotCountAndMovesTheEnd() {
        val paused = RestChanges(pause = StopwatchPause().toggled(at(30)))
        assertNull(paused.endsAt(150, loggedAt))
        assertEquals(120, paused.remainingSeconds(150, loggedAt, at(90)))

        val resumed = paused.copy(pause = paused.pause.toggled(at(90)))
        assertEquals(at(210), resumed.endsAt(150, loggedAt))
        assertEquals(110, resumed.remainingSeconds(150, loggedAt, at(100)))
    }

    @Test
    fun aRestartCountsFromItsOwnTimeAndLength() {
        val restarted = RestChanges(lengthSeconds = 60, restartedAt = at(100), adjustSeconds = 15)

        assertEquals(75, restarted.totalSeconds(150))
        assertEquals(at(175), restarted.endsAt(150, loggedAt))
    }

    @Test
    fun adjustingNeverGoesBelowNothing() {
        assertEquals(0, RestChanges(adjustSeconds = -300).totalSeconds(150))
    }

    @Test
    fun changesAreKeptPerSet() {
        val clocks = RestClocks()

        clocks.update(1) { it.copy(adjustSeconds = it.adjustSeconds + 15) }
        clocks.update(1) { it.copy(adjustSeconds = it.adjustSeconds + 15) }
        clocks.update(2) { it.copy(lengthSeconds = 60) }

        assertEquals(RestChanges(adjustSeconds = 30), clocks.changes.value[1])
        assertEquals(RestChanges(lengthSeconds = 60), clocks.changes.value[2])
    }
}
