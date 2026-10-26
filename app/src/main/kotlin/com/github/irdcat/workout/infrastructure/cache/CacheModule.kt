package com.github.irdcat.workout.infrastructure.cache

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideWorkoutDatabase(
        @ApplicationContext context: Context,
    ): WorkoutDatabase = Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout.db")
        // Holds logged sets that exist only on the phone: a schema change after version 1
        // needs a migration, never a destructive fallback.
        .build()

    @Provides
    fun providePlanDao(database: WorkoutDatabase): PlanDao = database.planDao()

    @Provides
    fun provideSessionLogDao(database: WorkoutDatabase): SessionLogDao = database.sessionLogDao()

    @Provides
    fun provideResultDao(database: WorkoutDatabase): ResultDao = database.resultDao()
}
