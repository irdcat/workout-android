package com.github.irdcat.workout.domain.session

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The workout being trained right now. At most one is active at a time. */
data class ActiveSession(
    /** [com.github.irdcat.workout.domain.workout.Workout.id], e.g. "CZERWIEC2026/1/T1". */
    val workoutId: String,
    /** When the session was started; the session clock counts from here. */
    val startedAt: Instant,
)

/** Keeps track of the active session; it survives the app being closed. */
interface ActiveSessionRepository {

    /** The active session, or null when none is open. */
    val activeSession: Flow<ActiveSession?>

    /** Current value of [activeSession]. */
    suspend fun getActiveSession(): ActiveSession?

    /**
     * Opens a session for [workoutId]. Starting the workout that is already
     * active returns it unchanged; another active workout must be ended first.
     */
    suspend fun start(workoutId: String): StartSessionResult

    /** Closes the active session, if any (finished or abandoned). */
    suspend fun end()
}

sealed interface StartSessionResult {
    data class Started(val session: ActiveSession) : StartSessionResult

    /** A different workout is still active. */
    data class AnotherActive(val active: ActiveSession) : StartSessionResult
}
