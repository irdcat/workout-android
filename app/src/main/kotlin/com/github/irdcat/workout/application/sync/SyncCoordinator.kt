package com.github.irdcat.workout.application.sync

import com.github.irdcat.workout.domain.workout.SyncResult
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.infrastructure.coroutines.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs syncs for the whole app, outside any screen, so one keeps going while the
 * user moves around and every sync button can show that it's running.
 *
 * One sync at a time: asking while one runs joins it instead of starting another,
 * except [syncAfterChanges], which runs another once it's done.
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val syncWorkoutPlan: SyncWorkoutPlan,
    private val planRepository: WorkoutPlanRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val lock = Any()
    private var running: Deferred<SyncWorkoutPlan.Outcome>? = null

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastResult = MutableStateFlow<SyncResult?>(null)

    /** How the last finished sync went; null before the first one in this process. */
    val lastResult: StateFlow<SyncResult?> = _lastResult.asStateFlow()

    /** Reads the sheet, then writes saved results (see [SyncWorkoutPlan]). */
    fun sync(): Deferred<SyncWorkoutPlan.Outcome> = start { syncWorkoutPlan() }

    /**
     * For results just saved: a sync already running may be past writing them, so
     * this one runs after it rather than joining it.
     */
    fun syncAfterChanges(): Deferred<SyncWorkoutPlan.Outcome> = start(joinRunning = false) { syncWorkoutPlan() }

    /** Drops the cached plan and reads it again; joins a sync already running instead. */
    fun clearAndSync(): Deferred<SyncWorkoutPlan.Outcome> = start {
        planRepository.clearCache()
        syncWorkoutPlan()
    }

    private fun start(
        joinRunning: Boolean = true,
        block: suspend () -> SyncWorkoutPlan.Outcome,
    ): Deferred<SyncWorkoutPlan.Outcome> = synchronized(lock) {
        val previous = running?.takeIf { it.isActive }
        if (previous != null && joinRunning) return previous
        val job = scope.async(start = CoroutineStart.LAZY) {
            previous?.join()
            block().also { _lastResult.value = it.result }
        }
        running = job
        _isSyncing.value = true
        job.invokeOnCompletion {
            synchronized(lock) {
                if (running === job) {
                    running = null
                    _isSyncing.value = false
                }
            }
        }
        job.start()
        job
    }
}
