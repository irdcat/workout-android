package com.github.irdcat.workout.infrastructure.session

import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.SessionLogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    abstract fun bindActiveSessionRepository(impl: DataStoreActiveSessionRepository): ActiveSessionRepository

    @Binds
    abstract fun bindSessionLogRepository(impl: RoomSessionLogRepository): SessionLogRepository
}
