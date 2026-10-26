package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.DEFAULT_SET_REST_SECONDS
import com.github.irdcat.workout.domain.session.DEFAULT_WARM_UP_REST_SECONDS
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.SessionLog
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.SessionProgress
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
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
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The active session together with its workout, week and the sets logged so far. */
data class ActiveWorkout(
    val session: ActiveSession,
    val workout: Workout,
    val week: TrainingWeek,
    val log: SessionLog,
) {
    val progress: SessionProgress = SessionProgress(workout, log)

    /** The exercise to do next; null once every exercise is complete. */
    val currentExercise: Exercise? get() = progress.next?.exercise

    /**
     * The rest after the last logged set and its planned length in seconds: the sheet's for a
     * working set, the user's ([chosenRestSeconds], see [chosenRestFlow]) for a warm-up or an
     * unlimited set. null when there's no set, its group has no rest, the rest was already
     * ended, or none follows: after the last set of an exercise its result is reviewed instead.
     */
    fun currentRest(chosenRestSeconds: Int?): Pair<LoggedSet, Int>? {
        val last = log.lastSet ?: return null
        if (last.id in log.rests) return null
        val exercise = progress.exerciseOf(last)
        val item = exercise?.let { progress.itemOf(it)?.index }
        if (progress.next?.itemIndex != item) return null
        val sheetRest = progress.setGroup(last.setGroupId)?.restSeconds
        val planned = when {
            last.warmUp -> chosenRestSeconds ?: DEFAULT_WARM_UP_REST_SECONDS
            exercise?.isUnlimited == true -> chosenRestSeconds ?: sheetRest ?: DEFAULT_SET_REST_SECONDS
            else -> sheetRest ?: return null
        }
        return last to planned
    }
}

/**
 * The active session joined with its workout from the plan. null when no session
 * is open, or when the workout is no longer in the cached plan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun activeWorkoutFlow(
    sessionRepository: ActiveSessionRepository,
    planRepository: WorkoutPlanRepository,
    logRepository: SessionLogRepository,
): Flow<ActiveWorkout?> = sessionRepository.activeSession.flatMapLatest { session ->
    if (session == null) {
        flowOf(null)
    } else {
        combine(
            planRepository.observeWorkout(session.workoutId),
            planRepository.observeWeekOfWorkout(session.workoutId),
            logRepository.observe(session),
        ) { workout, week, log ->
            if (workout != null && week != null) ActiveWorkout(session, workout, week, log) else null
        }
    }
}

/**
 * The rest the user picked for the exercise of the last set, when it's theirs to pick:
 * after a warm-up or a set of unlimited sets. null otherwise, and until one was saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun chosenRestFlow(
    activeWorkout: Flow<ActiveWorkout?>,
    warmUpRests: WarmUpRestRepository,
    setRests: SetRestRepository,
): Flow<Int?> = activeWorkout
    .map { active ->
        val last = active?.log?.lastSet ?: return@map null
        val exercise = active.progress.exerciseOf(last) ?: return@map null
        when {
            last.warmUp -> true to exercise.name
            exercise.isUnlimited -> false to exercise.name
            else -> null
        }
    }
    .distinctUntilChanged()
    .flatMapLatest { chosen ->
        when {
            chosen == null -> flowOf(null)
            chosen.first -> warmUpRests.observe(chosen.second)
            else -> setRests.observe(chosen.second)
        }
    }

/** Time since [startedAt], emitted every second. */
fun elapsedSince(startedAt: Instant, clock: Clock): Flow<Duration> = flow {
    while (true) {
        val elapsed = Duration.between(startedAt, clock.instant())
        emit(elapsed)
        // Wake up on the next whole second so the clock never skips a digit.
        delay(1_000 - elapsed.toMillis().mod(1_000L))
    }
}

/** "12:04", or "1:02:31" from an hour on. */
fun formatElapsed(elapsed: Duration): String {
    val seconds = elapsed.seconds.coerceAtLeast(0)
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
