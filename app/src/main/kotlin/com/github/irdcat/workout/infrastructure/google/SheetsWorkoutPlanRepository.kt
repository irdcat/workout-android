package com.github.irdcat.workout.infrastructure.google

import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.results.withSavedResults
import com.github.irdcat.workout.domain.workout.SyncInfo
import com.github.irdcat.workout.domain.workout.SyncResult
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.infrastructure.cache.PlanDao
import com.github.irdcat.workout.infrastructure.cache.SheetEntity
import com.github.irdcat.workout.infrastructure.cache.toDomain
import com.github.irdcat.workout.infrastructure.cache.toMonths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the plan from the Room cache and refreshes it from Google Sheets. Results
 * saved in the app but not in the sheet yet are shown in place of the sheet's text.
 */
@Singleton
class SheetsWorkoutPlanRepository @Inject constructor(
    private val sheetsRepository: SheetsRepository,
    private val sheetsConfigDataStore: SheetsConfigDataStore,
    private val planDao: PlanDao,
    private val resultRepository: ResultRepository,
) : WorkoutPlanRepository {

    private val syncLock = Mutex()

    private val saved: Flow<List<SavedResult>> get() = resultRepository.observeSaved()

    override fun observeMonths(): Flow<List<TrainingMonth>> =
        combine(planDao.observeWeeks(), saved) { weeks, saved ->
            weeks.toMonths().map { month -> month.copy(weeks = month.weeks.map { it.withSavedResults(saved) }) }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeWeek(weekId: String): Flow<TrainingWeek?> =
        combine(planDao.observeWeek(weekId), saved) { week, saved -> week?.toDomain()?.withSavedResults(saved) }
            .distinctUntilChanged()

    override fun observeWorkout(workoutId: String): Flow<Workout?> =
        combine(planDao.observeWorkout(workoutId), saved) { workout, saved -> workout?.toDomain()?.withSavedResults(saved) }
            .distinctUntilChanged()

    override fun observeWeekOfWorkout(workoutId: String): Flow<TrainingWeek?> =
        combine(planDao.observeWeekOfWorkout(workoutId), saved) { week, saved -> week?.toDomain()?.withSavedResults(saved) }
            .distinctUntilChanged()

    private fun TrainingWeek.withSavedResults(saved: List<SavedResult>): TrainingWeek =
        if (saved.isEmpty()) this else copy(workouts = workouts.map { it.withSavedResults(saved) })

    override fun observeSyncInfo(): Flow<SyncInfo> = combine(
        planDao.observeLastSyncedAt(),
        planDao.observeSheetCount(),
        planDao.observeWeekCount(),
    ) { lastSyncedAt, sheets, weeks ->
        SyncInfo(lastSyncedAt?.let(Instant::ofEpochMilli), sheets, weeks)
    }.distinctUntilChanged()

    override suspend fun sync(): SyncResult = syncLock.withLock {
        val config = sheetsConfigDataStore.sheetsConfigFlow().first()
        if (!config.isConfigured()) return SyncResult.NotConfigured
        try {
            val syncedAt = System.currentTimeMillis()
            // Read every tab before touching the cache, so a failure leaves it intact.
            val sheets = sheetsRepository.listSheets()
                .filterNot { it.name in config.sheetBlacklist }
                .map { sheet ->
                    val rows = sheetsRepository.readSheetData(sheet, startCell = "A1", endCell = "H")
                    SheetEntity(sheet.name, sheet.index, syncedAt) to SheetPlanParser.parse(sheet.name, rows)
                }
            planDao.replaceAll(sheets)
            SyncResult.Success(sheetCount = sheets.size, weekCount = sheets.sumOf { it.second.weeks.size })
        } catch (e: IOException) {
            // Offline, an HTTP error or lost access.
            SyncResult.Failed(e)
        } catch (e: IllegalArgumentException) {
            // No spreadsheet saved, or a cell that isn't what it says (NumberFormatException).
            SyncResult.Failed(e)
        }
    }

    override suspend fun clearCache() {
        syncLock.withLock { planDao.deleteAll() }
    }
}
