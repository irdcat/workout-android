package com.github.irdcat.workout.testing

import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.settings.RestAlarm
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.settings.RestEndNotifier
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.infrastructure.google.GOOGLE_SERVER_CLIENT_ID
import com.github.irdcat.workout.infrastructure.google.GoogleModule
import com.github.irdcat.workout.infrastructure.session.SessionModule
import com.github.irdcat.workout.infrastructure.settings.SettingsModule
import com.github.irdcat.workout.infrastructure.time.ClockModule
import com.google.api.services.sheets.v4.Sheets
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Clock
import javax.inject.Named
import javax.inject.Singleton

/** Where the clock starts in Hilt tests: Wednesday 3 June 2026, 18:00 UTC. */
val TestToday: Clock = fixedClock("2026-06-03")

/**
 * Fakes in place of Google (sign-in, plan, results), the stored session and the settings (warm-up rests too). Tests inject the fakes
 * to set things up and to check what the app did; Room and the spreadsheet-link
 * DataStore stay real (Robolectric gives them a fresh app directory).
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [GoogleModule::class, SessionModule::class, SettingsModule::class, ClockModule::class],
)
abstract class TestAppModule {

    @Binds
    abstract fun bindAuthRepository(fake: FakeAuthRepository): AuthRepository

    @Binds
    abstract fun bindWorkoutPlanRepository(fake: FakeWorkoutPlanRepository): WorkoutPlanRepository

    @Binds
    abstract fun bindActiveSessionRepository(fake: FakeActiveSessionRepository): ActiveSessionRepository

    @Binds
    abstract fun bindSessionLogRepository(fake: FakeSessionLogRepository): SessionLogRepository

    @Binds
    abstract fun bindSettingsRepository(fake: FakeSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindRestEndNotifier(fake: RecordingRestEndNotifier): RestEndNotifier

    @Binds
    abstract fun bindRestAlarm(fake: RecordingRestAlarm): RestAlarm

    @Binds
    abstract fun bindResultRepository(fake: FakeResultRepository): ResultRepository

    @Binds
    abstract fun bindWarmUpRestRepository(fake: FakeWarmUpRestRepository): WarmUpRestRepository

    @Binds
    abstract fun bindSetRestRepository(fake: FakeSetRestRepository): SetRestRepository

    /** Tests inject the [MutableClock] to let time pass, e.g. for a rest to run out. */
    @Binds
    abstract fun bindClock(clock: MutableClock): Clock

    companion object {

        @Provides
        @Singleton
        fun provideClock(): MutableClock = MutableClock(TestToday.instant())

        @Provides
        @Singleton
        fun provideAuthRepository(): FakeAuthRepository = FakeAuthRepository()

        @Provides
        @Singleton
        fun provideWorkoutPlanRepository(): FakeWorkoutPlanRepository = FakeWorkoutPlanRepository()

        @Provides
        @Singleton
        fun provideActiveSessionRepository(clock: Clock): FakeActiveSessionRepository = FakeActiveSessionRepository(clock)

        @Provides
        @Singleton
        fun provideSessionLogRepository(): FakeSessionLogRepository = FakeSessionLogRepository()

        @Provides
        @Singleton
        fun provideSettingsRepository(): FakeSettingsRepository = FakeSettingsRepository()

        @Provides
        @Singleton
        fun provideRestEndNotifier(): RecordingRestEndNotifier = RecordingRestEndNotifier()

        @Provides
        @Singleton
        fun provideRestAlarm(): RecordingRestAlarm = RecordingRestAlarm()

        @Provides
        @Singleton
        fun provideResultRepository(): FakeResultRepository = FakeResultRepository()

        @Provides
        @Singleton
        fun provideWarmUpRestRepository(): FakeWarmUpRestRepository = FakeWarmUpRestRepository()

        @Provides
        @Singleton
        fun provideSetRestRepository(): FakeSetRestRepository = FakeSetRestRepository()

        @Provides
        @Named(GOOGLE_SERVER_CLIENT_ID)
        fun provideServerClientId(): String = "test-client-id"

        @Provides
        @Singleton
        fun provideSheets(): Sheets = fakeSheets(listOf("CZERWIEC"))
    }
}

/** Remembers each rest-end alert instead of vibrating. */
class RecordingRestEndNotifier : RestEndNotifier {
    val alerts = mutableListOf<RestEndAlert>()
    override fun notify(alert: RestEndAlert) {
        alerts += alert
    }
}
