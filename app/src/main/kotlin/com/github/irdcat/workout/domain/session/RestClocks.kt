package com.github.irdcat.workout.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/** How a rest was changed from its planned length. */
data class RestChanges(
    /** Replaces the planned length: a restart, a preset or a typed time. */
    val lengthSeconds: Int? = null,
    /** A restart counts from here instead of from the set being logged. */
    val restartedAt: Instant? = null,
    /** ±15 s taps on top of the length. */
    val adjustSeconds: Int = 0,
    val pause: StopwatchPause = StopwatchPause(),
) {
    fun totalSeconds(plannedSeconds: Int): Int = ((lengthSeconds ?: plannedSeconds) + adjustSeconds).coerceAtLeast(0)

    /**
     * Whole seconds left, rounded up so "0:01" shows until the rest really ends;
     * negative once it has run over.
     */
    fun remainingSeconds(plannedSeconds: Int, loggedAt: Instant, now: Instant): Int {
        val rested: Duration = pause.elapsed(restartedAt ?: loggedAt, now)
        return ceil((totalSeconds(plannedSeconds) * 1000L - rested.toMillis()) / 1000.0).toInt()
    }

    /** When the rest ends, counting time spent paused; null while paused. */
    fun endsAt(plannedSeconds: Int, loggedAt: Instant): Instant? =
        if (pause.isPaused) {
            null
        } else {
            (restartedAt ?: loggedAt).plus(pause.pausedFor).plusSeconds(totalSeconds(plannedSeconds).toLong())
        }
}

/**
 * Changes to rests, by the [LoggedSet.id] each follows. Kept for as long as the app
 * runs, so a rest carries on (paused, restarted, adjusted) while the user is on
 * another screen and comes back to it.
 */
@Singleton
class RestClocks @Inject constructor() {

    private val _changes = MutableStateFlow<Map<Long, RestChanges>>(emptyMap())
    val changes: StateFlow<Map<Long, RestChanges>> = _changes.asStateFlow()

    fun update(setId: Long, transform: (RestChanges) -> RestChanges) {
        _changes.update { all -> all + (setId to transform(all[setId] ?: RestChanges())) }
    }
}
