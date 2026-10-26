package com.github.irdcat.workout.infrastructure.cache

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
    version = 1,
    exportSchema = true,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao

    abstract fun sessionLogDao(): SessionLogDao

    abstract fun resultDao(): ResultDao
}
