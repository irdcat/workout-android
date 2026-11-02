package com.github.irdcat.workout.infrastructure.cache

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SheetEntity::class,
        WeekEntity::class,
        WorkoutEntity::class,
        ExerciseEntity::class,
        SetGroupEntity::class,
        LoggedSetEntity::class,
        FinishedExerciseEntity::class,
        RestEntity::class,
        SessionRecordEntity::class,
        SavedResultEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 2: finished_exercises.reviewed, for exercises ended early awaiting review.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao

    abstract fun sessionLogDao(): SessionLogDao

    abstract fun resultDao(): ResultDao
}
