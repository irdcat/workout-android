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

    @Query("SELECT COUNT(*) FROM sheets")
    abstract suspend fun sheetCount(): Int

    @Query("SELECT COUNT(*) FROM weeks")
    abstract suspend fun weekCount(): Int

    /** Records a sync that found nothing to change: only [observeLastSyncedAt] moves. */
    @Query("UPDATE sheets SET syncedAtMillis = :syncedAtMillis")
    abstract suspend fun markAllSynced(syncedAtMillis: Long)

    /**
     * Replaces the cache with [sheets] in one transaction: tabs not listed are
     * removed, and each listed tab whose content changed is deleted and re-inserted.
     * A tab that reads the same as cached only has its sync time updated, so the
     * plan's observers aren't woken by a sync that changed nothing.
     */
    @Transaction
    open suspend fun replaceAll(sheets: List<Pair<SheetEntity, SheetContent>>) {
        deleteSheetsExcept(sheets.map { it.first.name })
        for ((sheet, content) in sheets) {
            if (sheet(sheet.name)?.sheetIndex == sheet.sheetIndex && cachedContent(sheet.name) == content.sortedById()) {
                markSynced(sheet.name, sheet.syncedAtMillis)
                continue
            }
            deleteSheet(sheet.name)
            insertSheet(sheet)
            insertWeeks(content.weeks)
            insertWorkouts(content.workouts)
            insertExercises(content.exercises)
            insertSetGroups(content.setGroups)
        }
    }

    private suspend fun cachedContent(sheetName: String) = SheetContent(
        weeks = weeksOf(sheetName),
        workouts = workoutsOf(sheetName),
        exercises = exercisesOf(sheetName),
        setGroups = setGroupsOf(sheetName),
    ).sortedById()

    private fun SheetContent.sortedById() = SheetContent(
        weeks = weeks.sortedBy { it.id },
        workouts = workouts.sortedBy { it.id },
        exercises = exercises.sortedBy { it.id },
        setGroups = setGroups.sortedBy { it.id },
    )

    @Query("SELECT * FROM sheets WHERE name = :name")
    protected abstract suspend fun sheet(name: String): SheetEntity?

    @Query("UPDATE sheets SET syncedAtMillis = :syncedAtMillis WHERE name = :name")
    protected abstract suspend fun markSynced(name: String, syncedAtMillis: Long)

    @Query("SELECT * FROM weeks WHERE sheetName = :sheetName")
    protected abstract suspend fun weeksOf(sheetName: String): List<WeekEntity>

    @Query("SELECT workouts.* FROM workouts JOIN weeks ON weeks.id = workouts.weekId WHERE weeks.sheetName = :sheetName")
    protected abstract suspend fun workoutsOf(sheetName: String): List<WorkoutEntity>

    @Query(
        "SELECT exercises.* FROM exercises JOIN workouts ON workouts.id = exercises.workoutId " +
            "JOIN weeks ON weeks.id = workouts.weekId WHERE weeks.sheetName = :sheetName"
    )
    protected abstract suspend fun exercisesOf(sheetName: String): List<ExerciseEntity>

    @Query(
        "SELECT set_groups.* FROM set_groups JOIN exercises ON exercises.id = set_groups.exerciseId " +
            "JOIN workouts ON workouts.id = exercises.workoutId " +
            "JOIN weeks ON weeks.id = workouts.weekId WHERE weeks.sheetName = :sheetName"
    )
    protected abstract suspend fun setGroupsOf(sheetName: String): List<SetGroupEntity>

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
