package com.github.irdcat.workout.domain.workout

import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.results.WriteResultsOutcome
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Refreshes the plan from the sheet, ends the active session if its workout is no
 * longer there (deleted, renamed, or moved to another week), then writes results
 * saved in the app to the sheet when "Write results to the sheet" is on.
 *
 * Only a successful sync is trusted: while the cache is empty (just cleared) or
 * a sync failed, the workout may only be missing locally, so the session stays,
 * and results wait, since their rows are looked up in what was just read.
 */
class SyncWorkoutPlan @Inject constructor(
    private val planRepository: WorkoutPlanRepository,
    private val sessionRepository: ActiveSessionRepository,
    private val resultRepository: ResultRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(): Outcome {
        val result = planRepository.sync()
        if (result !is SyncResult.Success) return Outcome(result, endedSession = null, written = WriteResultsOutcome.NothingToWrite)

        val session = sessionRepository.getActiveSession()
        val orphaned = session != null && planRepository.observeWorkout(session.workoutId).first() == null
        // Its logged sets stay stored: logs are kept per session, not removed with it.
        if (orphaned) sessionRepository.end()

        val written = if (settingsRepository.getSettings().writeResultsToSheet) {
            resultRepository.writeSaved()
        } else {
            WriteResultsOutcome.NothingToWrite
        }
        return Outcome(result, endedSession = session.takeIf { orphaned }, written = written)
    }

    data class Outcome(
        val result: SyncResult,
        /** The session that was ended because its workout left the sheet, if any. */
        val endedSession: ActiveSession?,
        val written: WriteResultsOutcome,
    )
}
