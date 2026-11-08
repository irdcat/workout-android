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
import com.github.irdcat.workout.infrastructure.cache.SheetContent
import com.github.irdcat.workout.infrastructure.cache.SheetEntity
import com.github.irdcat.workout.infrastructure.cache.toDomain
import com.github.irdcat.workout.infrastructure.cache.toMonths
import com.github.irdcat.workout.infrastructure.coroutines.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** How long the shared plan outlives its last reader, e.g. across a switch between tabs. */
private const val SHARE_TIMEOUT_MILLIS = 5_000L

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
    private val changeTracker: SheetChangeTracker,
    @param:ApplicationScope private val scope: CoroutineScope,
) : WorkoutPlanRepository {

    private val syncLock = Mutex()

    /**
     * Shared by every screen and lookup reading at the same time, so the plan is queried and mapped
     * once for all of them; dropped once nobody reads it, so a later read starts from the database.
     */
    private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARE_TIMEOUT_MILLIS, replayExpirationMillis = 0)

    /** Saved results, by the workout they belong to. */
    private val saved: Flow<Map<String, List<SavedResult>>> = resultRepository.observeSaved()
        .map { all -> all.groupBy { it.target.workoutId } }
        .shareIn(scope, sharing, replay = 1)

    /** The rows mapped to months only when the plan changes; a saved result only lays itself over them. */
    private val months: Flow<List<TrainingMonth>> =
        combine(planDao.observeWeeks().map { it.toMonths() }, saved) { months, saved ->
            if (saved.isEmpty()) months else months.map { month -> month.copy(weeks = month.weeks.map { it.withSavedResults(saved) }) }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .shareIn(scope, sharing, replay = 1)

    override fun observeMonths(): Flow<List<TrainingMonth>> = months

    override fun observeWeek(weekId: String): Flow<TrainingWeek?> =
        combine(planDao.observeWeek(weekId), saved) { week, saved -> week?.toDomain()?.withSavedResults(saved) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeWorkout(workoutId: String): Flow<Workout?> =
        combine(planDao.observeWorkout(workoutId), saved) { workout, saved ->
            workout?.toDomain()?.let { domain -> saved[workoutId]?.let(domain::withSavedResults) ?: domain }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeWeekOfWorkout(workoutId: String): Flow<TrainingWeek?> =
        combine(planDao.observeWeekOfWorkout(workoutId), saved) { week, saved -> week?.toDomain()?.withSavedResults(saved) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** Only the workouts with saved results are rebuilt. */
    private fun TrainingWeek.withSavedResults(saved: Map<String, List<SavedResult>>): TrainingWeek =
        if (workouts.none { it.id in saved }) {
            this
        } else {
            copy(workouts = workouts.map { workout -> saved[workout.id]?.let(workout::withSavedResults) ?: workout })
        }

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
            val read: suspend () -> List<Pair<SheetEntity, SheetContent>> = {
                val tabs = sheetsRepository.listSheets().filterNot { it.name in config.sheetBlacklist }
                val rows = sheetsRepository.readSheetsData(tabs, startCell = "A1", endCell = "H")
                withContext(Dispatchers.Default) {
                    tabs.zip(rows) { sheet, sheetRows ->
                        SheetEntity(sheet.name, sheet.index, syncedAt) to SheetPlanParser.parse(sheet.name, sheetRows)
                    }
                }
            }
            // Read every tab before touching the cache, so a failure leaves it intact. With a cache to
            // keep, a sheet Drive shows unchanged since it was filled isn't read at all. (Linking
            // another sheet or changing the blacklist clears the cache, and the synced version with it.)
            val sheets = if (planDao.sheetCount() > 0) changeTracker.readingIfChanged(read) else changeTracker.reading(read)
            if (sheets == null) {
                planDao.markAllSynced(syncedAt)
                return SyncResult.Success(sheetCount = planDao.sheetCount(), weekCount = planDao.weekCount())
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

    override suspend fun isSheetChangedSinceSync(): Boolean? = changeTracker.changedSinceSync()

    override suspend fun clearCache() {
        syncLock.withLock {
            planDao.deleteAll()
            changeTracker.forget()
        }
    }
}
