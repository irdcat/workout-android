package com.github.irdcat.workout.domain.workout

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The training plan read from the spreadsheet. Reads come from the offline
 * cache; [sync] refreshes it from the sheet.
 */
interface WorkoutPlanRepository {

    /** All cached months, oldest first, each with its weeks in date order. */
    fun observeMonths(): Flow<List<TrainingMonth>>

    fun observeWeek(weekId: String): Flow<TrainingWeek?>

    fun observeWorkout(workoutId: String): Flow<Workout?>

    /** The week a workout belongs to, e.g. for its date range. */
    fun observeWeekOfWorkout(workoutId: String): Flow<TrainingWeek?>

    fun observeSyncInfo(): Flow<SyncInfo>

    /** Re-reads every sheet that isn't blacklisted and replaces the cache with it. */
    suspend fun sync(): SyncResult

    /** Deletes the cached plan. */
    suspend fun clearCache()
}

data class SyncInfo(
    /** Time of the last successful sync, or null if the cache is empty. */
    val lastSyncedAt: Instant?,
    val sheetCount: Int,
    val weekCount: Int,
)

sealed interface SyncResult {
    data class Success(val sheetCount: Int, val weekCount: Int) : SyncResult

    /** No spreadsheet link saved yet. */
    data object NotConfigured : SyncResult

    /** The sheet couldn't be read; the cache was left as it was. */
    data class Failed(val cause: Throwable) : SyncResult
}
