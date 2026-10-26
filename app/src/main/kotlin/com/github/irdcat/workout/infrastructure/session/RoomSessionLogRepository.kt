package com.github.irdcat.workout.infrastructure.session

import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.PastSession
import com.github.irdcat.workout.domain.session.SessionLog
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.infrastructure.cache.FinishedExerciseEntity
import com.github.irdcat.workout.infrastructure.cache.LoggedSetEntity
import com.github.irdcat.workout.infrastructure.cache.RestEntity
import com.github.irdcat.workout.infrastructure.cache.SessionLogDao
import com.github.irdcat.workout.infrastructure.cache.SessionRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSessionLogRepository @Inject constructor(
    private val dao: SessionLogDao,
) : SessionLogRepository {

    override fun observe(session: ActiveSession): Flow<SessionLog> = combine(
        dao.observeSets(session.workoutId, session.startedAtMillis),
        dao.observeFinished(session.workoutId, session.startedAtMillis),
        dao.observeRests(session.workoutId, session.startedAtMillis),
    ) { sets, finished, rests ->
        val loggedAt = sets.associate { it.id to it.loggedAtMillis }
        SessionLog(
            sets = sets.map { it.toDomain() },
            finishedExerciseIds = finished.toSet(),
            rests = rests.mapNotNull { rest ->
                val start = loggedAt[rest.loggedSetId] ?: return@mapNotNull null
                rest.loggedSetId to Duration.ofMillis((rest.endedAtMillis - start).coerceAtLeast(0))
            }.toMap(),
        )
    }.distinctUntilChanged()

    override suspend fun logSet(session: ActiveSession, set: LoggedSet) {
        dao.insert(
            LoggedSetEntity(
                workoutId = session.workoutId,
                sessionStartedAtMillis = session.startedAtMillis,
                exerciseId = set.exerciseId,
                setGroupId = set.setGroupId,
                weightKg = set.weightKg,
                reps = set.reps,
                rpe = set.rpe,
                warmUp = set.warmUp,
                loggedAtMillis = set.loggedAt.toEpochMilli(),
            )
        )
    }

    override suspend fun undoLastSet(session: ActiveSession): LoggedSet? =
        dao.deleteLast(session.workoutId, session.startedAtMillis)?.toDomain()

    override suspend fun finishExercises(session: ActiveSession, exerciseIds: Collection<String>) {
        dao.insertFinished(exerciseIds.map { FinishedExerciseEntity(session.workoutId, session.startedAtMillis, it) })
    }

    override suspend fun recordRest(session: ActiveSession, setId: Long, endedAt: Instant) {
        // Gone if it was undone meanwhile.
        if (dao.set(setId) == null) return
        dao.insertRest(RestEntity(setId, session.workoutId, session.startedAtMillis, endedAt.toEpochMilli()))
    }

    override suspend fun discardSession(session: ActiveSession) {
        dao.deleteSession(session.workoutId, session.startedAtMillis)
    }

    override suspend fun finishSession(session: ActiveSession, finishedAt: Instant) {
        dao.insertRecord(SessionRecordEntity(session.workoutId, session.startedAtMillis, finishedAt.toEpochMilli()))
    }

    override suspend fun latestSessionOf(workoutId: String): PastSession? {
        val startedAt = dao.latestSessionStart(workoutId) ?: return null
        val session = ActiveSession(workoutId, Instant.ofEpochMilli(startedAt))
        return PastSession(
            session = session,
            log = observe(session).first(),
            finishedAt = dao.finishedAt(workoutId, startedAt)?.let(Instant::ofEpochMilli),
        )
    }

    override fun observeAllSets(): Flow<List<LoggedSet>> =
        dao.observeWorkingSets().map { sets -> sets.map { it.toDomain() } }.distinctUntilChanged()

    private val ActiveSession.startedAtMillis: Long get() = startedAt.toEpochMilli()

    private fun LoggedSetEntity.toDomain() = LoggedSet(
        id = id,
        exerciseId = exerciseId,
        setGroupId = setGroupId,
        weightKg = weightKg,
        reps = reps,
        rpe = rpe,
        warmUp = warmUp,
        loggedAt = Instant.ofEpochMilli(loggedAtMillis),
    )
}
