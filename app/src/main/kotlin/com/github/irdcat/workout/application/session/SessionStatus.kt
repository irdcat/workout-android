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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val status: Flow<SessionStatus?> =
        settingsRepository.settings.map { it.measureWorkoutTime }.distinctUntilChanged().flatMapLatest { measure ->
            activeWorkoutFlow(sessionRepository, planRepository, logRepository).flatMapLatest { active ->
                if (active == null) {
                    flowOf(null)
                } else {
                    combine(
                        elapsedSince(active.session.startedAt, clock),
                        chosenRestFlow(flowOf(active), warmUpRests, setRests),
                        restClocks.changes,
                    ) { elapsed, chosenRest, changes ->
                        SessionStatus(active, elapsed.takeIf { measure }, restNow(active, chosenRest, changes))
                    }
                }
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
