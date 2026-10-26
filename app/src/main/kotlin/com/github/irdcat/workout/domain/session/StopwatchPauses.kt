package com.github.irdcat.workout.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Time an open-sets stopwatch spent paused, and since when it's paused now. */
data class StopwatchPause(
    val pausedFor: Duration = Duration.ZERO,
    val pausedSince: Instant? = null,
) {
    val isPaused: Boolean get() = pausedSince != null

    /** Running time since [start], not counting pauses. */
    fun elapsed(start: Instant, now: Instant): Duration {
        val ongoing = pausedSince?.let { Duration.between(it, now) } ?: Duration.ZERO
        return Duration.between(start, now).minus(pausedFor).minus(ongoing).coerceAtLeast(Duration.ZERO)
    }

    fun toggled(now: Instant): StopwatchPause = when (val since = pausedSince) {
        null -> copy(pausedSince = now)
        else -> StopwatchPause(pausedFor = pausedFor + Duration.between(since, now))
    }
}

private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

/**
 * Pauses of open-sets stopwatches, per session and exercise. Kept for as long as the
 * app runs, across screens; the stopwatch itself runs from the first logged set.
 */
@Singleton
class StopwatchPauses @Inject constructor() {

    private val _pauses = MutableStateFlow<Map<Pair<ActiveSession, String>, StopwatchPause>>(emptyMap())
    val pauses: StateFlow<Map<Pair<ActiveSession, String>, StopwatchPause>> = _pauses.asStateFlow()

    fun toggle(session: ActiveSession, exerciseId: String, now: Instant) {
        _pauses.update { all ->
            val key = session to exerciseId
            all + (key to (all[key] ?: StopwatchPause()).toggled(now))
        }
    }
}
