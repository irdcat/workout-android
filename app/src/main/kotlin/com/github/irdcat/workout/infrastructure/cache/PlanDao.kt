package com.github.irdcat.workout.infrastructure.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanDao {

    @Transaction
    @Query("SELECT * FROM weeks ORDER BY startEpochDay")
    abstract fun observeWeeks(): Flow<List<WeekWithWorkouts>>

    @Transaction
    @Query("SELECT * FROM weeks WHERE id = :weekId")
    abstract fun observeWeek(weekId: String): Flow<WeekWithWorkouts?>

    @Transaction
    @Query("SELECT weeks.* FROM weeks JOIN workouts ON workouts.weekId = weeks.id WHERE workouts.id = :workoutId")
    abstract fun observeWeekOfWorkout(workoutId: String): Flow<WeekWithWorkouts?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    abstract fun observeWorkout(workoutId: String): Flow<WorkoutWithExercises?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    abstract suspend fun workout(workoutId: String): WorkoutWithExercises?

    @Query("SELECT MAX(syncedAtMillis) FROM sheets")
    abstract fun observeLastSyncedAt(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM sheets")
    abstract fun observeSheetCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM weeks")
    abstract fun observeWeekCount(): Flow<Int>

    /**
     * Replaces the cache with [sheets] in one transaction: tabs not listed are
     * removed, and each listed tab is deleted and re-inserted.
     */
    @Transaction
    open suspend fun replaceAll(sheets: List<Pair<SheetEntity, SheetContent>>) {
        deleteSheetsExcept(sheets.map { it.first.name })
        for ((sheet, content) in sheets) {
            deleteSheet(sheet.name)
            insertSheet(sheet)
            insertWeeks(content.weeks)
            insertWorkouts(content.workouts)
            insertExercises(content.exercises)
            insertSetGroups(content.setGroups)
        }
    }

    @Query("DELETE FROM sheets")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM sheets WHERE name = :name")
    protected abstract suspend fun deleteSheet(name: String)

    @Query("DELETE FROM sheets WHERE name NOT IN (:names)")
    protected abstract suspend fun deleteSheetsExcept(names: List<String>)

    @Insert
    protected abstract suspend fun insertSheet(sheet: SheetEntity)

    @Insert
    protected abstract suspend fun insertWeeks(weeks: List<WeekEntity>)

    @Insert
    protected abstract suspend fun insertWorkouts(workouts: List<WorkoutEntity>)

    @Insert
    protected abstract suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Insert
    protected abstract suspend fun insertSetGroups(groups: List<SetGroupEntity>)
}
