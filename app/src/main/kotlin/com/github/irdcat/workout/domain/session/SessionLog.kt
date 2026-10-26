package com.github.irdcat.workout.domain.session

import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/** One set as performed. */
data class LoggedSet(
    /** 0 until stored. */
    val id: Long = 0,
    val exerciseId: String,
    /** The planned set group it was logged against. */
    val setGroupId: String,
    /** null for bodyweight or when no weight was entered. */
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double?,
    val warmUp: Boolean,
    val loggedAt: Instant,
)

/** Everything logged in one session, oldest first. */
data class SessionLog(
    val sets: List<LoggedSet> = emptyList(),
    /** Exercises ended early with "Finish exercise". */
    val finishedExerciseIds: Set<String> = emptySet(),
    /** How long each rest really took, by the [LoggedSet.id] it followed; only rests ended in the app. */
    val rests: Map<Long, Duration> = emptyMap(),
) {
    fun setsOf(exerciseId: String): List<LoggedSet> = sets.filter { it.exerciseId == exerciseId }

    val lastSet: LoggedSet? get() = sets.lastOrNull()

    val restingTime: Duration get() = rests.values.fold(Duration.ZERO, Duration::plus)

    /** null until a rest has been timed. */
    val averageRest: Duration? get() = if (rests.isEmpty()) null else restingTime.dividedBy(rests.size.toLong())
}

/** An earlier session of a workout, as far as the app saw it. */
data class PastSession(
    val session: ActiveSession,
    val log: SessionLog,
    /** When "Save results" ended it; null if it ended another way or is still open. */
    val finishedAt: Instant?,
) {
    /** Start to finish; null without a finish time. */
    val duration: Duration? get() = finishedAt?.let { Duration.between(session.startedAt, it) }
}

/**
 * Sets logged per session. A session is its workout plus its start time, so
 * restarting the same workout later starts an empty log. Logs outlive the
 * session: they stay stored after it ends.
 */
interface SessionLogRepository {

    fun observe(session: ActiveSession): Flow<SessionLog>

    suspend fun logSet(session: ActiveSession, set: LoggedSet)

    /** Removes and returns the most recent set, or null when nothing is logged. */
    suspend fun undoLastSet(session: ActiveSession): LoggedSet?

    /** Marks exercises as done even if planned sets are left. */
    suspend fun finishExercises(session: ActiveSession, exerciseIds: Collection<String>)

    /**
     * Records that the rest after set [setId] ended at [endedAt]. Only the first end
     * counts; undoing the set removes its rest too.
     */
    suspend fun recordRest(session: ActiveSession, setId: Long, endedAt: Instant)

    /** Deletes everything logged in [session]: sets, rests, finished exercises and its record. */
    suspend fun discardSession(session: ActiveSession)

    /** Records when the session was finished, for "last time" durations. */
    suspend fun finishSession(session: ActiveSession, finishedAt: Instant)

    /** The latest session of [workoutId] with anything logged, or null when the app has none. */
    suspend fun latestSessionOf(workoutId: String): PastSession?

    /** Every working set logged on this device, in every session; warm-ups left out. */
    fun observeAllSets(): Flow<List<LoggedSet>>
}
