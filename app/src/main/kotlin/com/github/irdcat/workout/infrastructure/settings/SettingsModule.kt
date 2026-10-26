package com.github.irdcat.workout.infrastructure.settings

import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.settings.RestAlarm
import com.github.irdcat.workout.domain.settings.RestEndNotifier
import com.github.irdcat.workout.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindRestEndNotifier(impl: AndroidRestEndNotifier): RestEndNotifier

    @Binds
    abstract fun bindRestAlarm(impl: AndroidRestAlarm): RestAlarm

    @Binds
    abstract fun bindWarmUpRestRepository(impl: DataStoreWarmUpRestRepository): WarmUpRestRepository

    @Binds
    abstract fun bindSetRestRepository(impl: DataStoreSetRestRepository): SetRestRepository
}
