package com.github.irdcat.workout.infrastructure.google

import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.results.ResultTarget
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.results.WriteResultsOutcome
import com.github.irdcat.workout.domain.results.matches
import com.github.irdcat.workout.infrastructure.cache.PlanDao
import com.github.irdcat.workout.infrastructure.cache.ResultDao
import com.github.irdcat.workout.infrastructure.cache.SavedResultEntity
import com.github.irdcat.workout.infrastructure.cache.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** WYNIK is column H. */
private const val RESULT_COLUMN = "H"

/** Results saved in Room and written to the WYNIK column of the saved spreadsheet. */
@Singleton
class SheetsResultRepository @Inject constructor(
    private val resultDao: ResultDao,
    private val planDao: PlanDao,
    private val sheetsRepository: SheetsRepository,
) : ResultRepository {

    private val writeLock = Mutex()

    override fun observeSaved(): Flow<List<SavedResult>> =
        resultDao.observeAll().map { all -> all.map { it.toDomain() } }.distinctUntilChanged()

    override suspend fun save(result: SavedResult) {
        resultDao.upsert(result.toEntity())
    }

    override suspend fun discardSavedSince(workoutId: String, since: Instant) {
        // Under the lock so a write in progress can't put them in the sheet afterwards.
        writeLock.withLock { resultDao.deleteSavedSince(workoutId, since.toEpochMilli()) }
    }

    override suspend fun writeSaved(): WriteResultsOutcome = writeLock.withLock {
        val saved = resultDao.all()
        // Each result's row as of the latest sync; one whose exercise can't be found waits.
        val cells = saved.mapNotNull { entity ->
            val workout = planDao.workout(entity.workoutId)?.toDomain() ?: return@mapNotNull null
            val target = entity.toDomain().target
            val exercise = workout.exercises.firstOrNull { target.matches(workout, it) } ?: return@mapNotNull null
            val group = exercise.setGroups[target.groupIndex]
            // Set group IDs are "<sheet name>!<row>" (see SheetPlanParser).
            val sheetName = group.id.substringBeforeLast('!')
            Triple(entity, group.id, CellWrite(sheetName, RESULT_COLUMN, group.sheetRow, entity.text.trim()))
        }
        if (cells.isEmpty()) return WriteResultsOutcome.NothingToWrite
        try {
            sheetsRepository.writeCells(cells.map { it.third })
        } catch (e: IOException) {
            // Offline, an HTTP error or lost access.
            return WriteResultsOutcome.Failed(e)
        } catch (e: IllegalArgumentException) {
            // No spreadsheet saved, or a cell that isn't what it says (NumberFormatException).
            return WriteResultsOutcome.Failed(e)
        }
        resultDao.markWritten(cells.map { (entity, setGroupId) -> entity.copy(text = entity.text.trim()) to setGroupId })
        WriteResultsOutcome.Written(cells.size)
    }

    private fun SavedResultEntity.toDomain() = SavedResult(
        target = ResultTarget(workoutId, exerciseNumber, groupIndex, exerciseName),
        text = text,
        savedAt = Instant.ofEpochMilli(savedAtMillis),
    )

    private fun SavedResult.toEntity() = SavedResultEntity(
        workoutId = target.workoutId,
        exerciseNumber = target.exerciseNumber,
        groupIndex = target.groupIndex,
        exerciseName = target.exerciseName,
        text = text,
        savedAtMillis = savedAt.toEpochMilli(),
    )
}
