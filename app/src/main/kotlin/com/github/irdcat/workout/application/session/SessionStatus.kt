package com.github.irdcat.workout.application.session

import com.github.irdcat.workout.application.ui.screens.ActiveWorkout
import com.github.irdcat.workout.application.ui.screens.activeWorkoutFlow
import com.github.irdcat.workout.application.ui.screens.chosenRestFlow
import com.github.irdcat.workout.application.ui.screens.elapsedSince
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.RestChanges
import com.github.irdcat.workout.domain.session.RestClocks
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The active session as Home's card and the session notification show it: its clock, and the rest being taken, if any. */
data class SessionStatus(
    val active: ActiveWorkout,
    /** null when "measure workout time" is off. */
    val elapsed: Duration?,
    val rest: RestStatus?,
)

data class RestStatus(
    val remainingSeconds: Int,
    val paused: Boolean,
    /** When the rest runs out; null while paused. */
    val endsAt: Instant?,
)

/** Longest wait between checks on a rest that runs out while nothing ticks. */
private const val MAX_WAIT_MILLIS = 15_000L

/** Where [SessionStatus] comes from, shared by Home and the session notification. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionStatusSource @Inject constructor(
    private val sessionRepository: ActiveSessionRepository,
    private val planRepository: WorkoutPlanRepository,
    private val logRepository: SessionLogRepository,
    private val settingsRepository: SettingsRepository,
    private val warmUpRests: WarmUpRestRepository,
    private val setRests: SetRestRepository,
    private val restClocks: RestClocks,
    private val clock: Clock,
) {

    /** The active session, ticking every second for its clock and any rest; null when none is open. */
    val status: Flow<SessionStatus?> = statusOf(ticking = true)

    /**
     * The active session as of its last change, for a view whose clocks run on their own (the
     * notification's chronometer): it doesn't tick, it only comes again when the session or its
     * rest changes, or when the rest runs out. Its [SessionStatus.elapsed] is as of that moment.
     */
    val changes: Flow<SessionStatus?> = statusOf(ticking = false)

    private fun statusOf(ticking: Boolean): Flow<SessionStatus?> =
        settingsRepository.settings.map { it.measureWorkoutTime }.distinctUntilChanged().flatMapLatest { measure ->
            activeWorkoutFlow(sessionRepository, planRepository, logRepository).flatMapLatest { active ->
                if (active == null) flowOf(null) else statusOf(active, measure, ticking)
            }
        }

    private fun statusOf(active: ActiveWorkout, measure: Boolean, ticking: Boolean): Flow<SessionStatus> =
        combine(chosenRestFlow(flowOf(active), warmUpRests, setRests), restClocks.changes, ::Pair)
            .flatMapLatest { (chosenRest, changes) ->
                fun now(elapsed: Duration = Duration.between(active.session.startedAt, clock.instant())) =
                    SessionStatus(active, elapsed.takeIf { measure }, restNow(active, chosenRest, changes))

                val ticks = elapsedSince(active.session.startedAt, clock).map { now(it) }
                when {
                    !ticking -> untilRestEnds { now() }
                    measure -> ticks
                    // Without the session clock, only a rest counting down moves, until it's over.
                    now().rest?.endsAt != null -> ticks.transformWhile { status -> emit(status); status.rest != null }
                    else -> flowOf(now())
                }
            }

    /** [now], and again once the rest it shows has run out. */
    private fun untilRestEnds(now: () -> SessionStatus): Flow<SessionStatus> = flow {
        var status = now()
        emit(status)
        while (true) {
            val endsAt = status.rest?.endsAt ?: break
            // Checked against the wall clock again now and then: a delay doesn't count deep sleep.
            delay(Duration.between(clock.instant(), endsAt).toMillis().coerceIn(0, MAX_WAIT_MILLIS))
            status = now()
            if (status.rest == null) emit(status)
        }
    }

    /** A rest still running (or paused) after the last set; one that has run out isn't shown. */
    private fun restNow(active: ActiveWorkout, chosenRest: Int?, changes: Map<Long, RestChanges>): RestStatus? {
        val (last, planned) = active.currentRest(chosenRest) ?: return null
        val rest = changes[last.id] ?: RestChanges()
        val remaining = rest.remainingSeconds(planned, last.loggedAt, clock.instant())
        return RestStatus(remaining, rest.pause.isPaused, rest.endsAt(planned, last.loggedAt)).takeIf { remaining > 0 }
    }
}
