package com.github.irdcat.workout.domain.session

import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.settings.RestAlarm
import javax.inject.Inject

/**
 * Ends the active session without saving anything: its logged sets and the results
 * saved during it are deleted, and a running rest's alarm is cancelled. Results
 * already written to the sheet (by a sync during the session) stay there.
 */
class AbandonSession @Inject constructor(
    private val sessionRepository: ActiveSessionRepository,
    private val logRepository: SessionLogRepository,
    private val resultRepository: ResultRepository,
    private val restAlarm: RestAlarm,
) {

    suspend operator fun invoke() {
        val session = sessionRepository.getActiveSession() ?: return
        resultRepository.discardSavedSince(session.workoutId, session.startedAt)
        logRepository.discardSession(session)
        restAlarm.cancel()
        // Last: ending it takes the user off the session's screens.
        sessionRepository.end()
    }
}
