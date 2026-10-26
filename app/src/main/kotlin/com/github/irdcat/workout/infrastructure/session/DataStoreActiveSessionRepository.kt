package com.github.irdcat.workout.infrastructure.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.StartSessionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Kept out of the Room plan cache on purpose: re-syncing or clearing the
// cache must not end a session that is in progress.
private val Context.activeSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "active-session")

private val WorkoutIdKey = stringPreferencesKey("workout_id")
private val StartedAtKey = longPreferencesKey("started_at_millis")

@Singleton
class DataStoreActiveSessionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ActiveSessionRepository {

    override val activeSession: Flow<ActiveSession?> = context.activeSessionDataStore.data
        .map { it.toSession() }
        .distinctUntilChanged()

    override suspend fun getActiveSession(): ActiveSession? = activeSession.first()

    override suspend fun start(workoutId: String): StartSessionResult {
        var result: StartSessionResult? = null
        // One edit, so two quick taps can't open two sessions.
        context.activeSessionDataStore.edit { preferences ->
            val current = preferences.toSession()
            result = when {
                current == null -> {
                    val session = ActiveSession(workoutId, clock.instant())
                    preferences[WorkoutIdKey] = session.workoutId
                    preferences[StartedAtKey] = session.startedAt.toEpochMilli()
                    StartSessionResult.Started(session)
                }
                current.workoutId == workoutId -> StartSessionResult.Started(current)
                else -> StartSessionResult.AnotherActive(current)
            }
        }
        return checkNotNull(result)
    }

    override suspend fun end() {
        context.activeSessionDataStore.edit { it.clear() }
    }

    private fun Preferences.toSession(): ActiveSession? {
        val workoutId = this[WorkoutIdKey] ?: return null
        val startedAt = this[StartedAtKey] ?: return null
        return ActiveSession(workoutId, Instant.ofEpochMilli(startedAt))
    }
}
