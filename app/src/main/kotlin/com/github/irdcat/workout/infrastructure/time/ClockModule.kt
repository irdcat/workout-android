package com.github.irdcat.workout.infrastructure.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** The one source of "now", so tests can pin the date and time. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
