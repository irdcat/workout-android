package com.github.irdcat.workout.infrastructure.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Data only the phone has: no foreign keys to the plan tables, which a sync
// replaces wholesale. A session is identified by its workout and start time.

@Entity(
    tableName = "logged_sets",
    indices = [Index("workoutId", "sessionStartedAtMillis")],
)
data class LoggedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: String,
    val sessionStartedAtMillis: Long,
    val exerciseId: String,
    val setGroupId: String,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double?,
    val warmUp: Boolean,
    val loggedAtMillis: Long,
)

@Entity(
    tableName = "finished_exercises",
    primaryKeys = ["workoutId", "sessionStartedAtMillis", "exerciseId"],
)
data class FinishedExerciseEntity(
    val workoutId: String,
    val sessionStartedAtMillis: Long,
    val exerciseId: String,
)

/** A rest as it really went: from its set being logged to [endedAtMillis]. */
@Entity(
    tableName = "rests",
    indices = [Index("workoutId", "sessionStartedAtMillis")],
)
data class RestEntity(
    /** The [LoggedSetEntity.id] the rest followed. */
    @PrimaryKey val loggedSetId: Long,
    val workoutId: String,
    val sessionStartedAtMillis: Long,
    val endedAtMillis: Long,
)

/** A session that was finished with "Save results". */
@Entity(
    tableName = "session_records",
    primaryKeys = ["workoutId", "sessionStartedAtMillis"],
)
data class SessionRecordEntity(
    val workoutId: String,
    val sessionStartedAtMillis: Long,
    val finishedAtMillis: Long,
)

@Dao
abstract class SessionLogDao {

    @Query(
        "SELECT * FROM logged_sets WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt " +
            "ORDER BY loggedAtMillis, id"
    )
    abstract fun observeSets(workoutId: String, startedAt: Long): Flow<List<LoggedSetEntity>>

    @Query("SELECT exerciseId FROM finished_exercises WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt")
    abstract fun observeFinished(workoutId: String, startedAt: Long): Flow<List<String>>

    @Query("SELECT * FROM rests WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt")
    abstract fun observeRests(workoutId: String, startedAt: Long): Flow<List<RestEntity>>

    @Query("SELECT * FROM logged_sets WHERE warmUp = 0 ORDER BY loggedAtMillis, id")
    abstract fun observeWorkingSets(): Flow<List<LoggedSetEntity>>

    /** Start time of the latest session of [workoutId] with a logged set. */
    @Query("SELECT MAX(sessionStartedAtMillis) FROM logged_sets WHERE workoutId = :workoutId")
    abstract suspend fun latestSessionStart(workoutId: String): Long?

    @Query("SELECT finishedAtMillis FROM session_records WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt")
    abstract suspend fun finishedAt(workoutId: String, startedAt: Long): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRest(rest: RestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRecord(record: SessionRecordEntity)

    @Query("SELECT * FROM logged_sets WHERE id = :id")
    abstract suspend fun set(id: Long): LoggedSetEntity?

    @Insert
    abstract suspend fun insert(set: LoggedSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFinished(exercises: List<FinishedExerciseEntity>)

    /** Removes everything logged in the session. */
    @Transaction
    open suspend fun deleteSession(workoutId: String, startedAt: Long) {
        deleteSets(workoutId, startedAt)
        deleteFinished(workoutId, startedAt)
        deleteRests(workoutId, startedAt)
        deleteRecord(workoutId, startedAt)
    }

    @Query("DELETE FROM logged_sets WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt")
    protected abstract suspend fun deleteSets(workoutId: String, startedAt: Long)

    @Query("DELETE FROM finished_exercises WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt")
    protected abstract suspend fun deleteFinished(workoutId: String, startedAt: Long)

    @Query("DELETE FROM rests WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt")
    protected abstract suspend fun deleteRests(workoutId: String, startedAt: Long)

    @Query("DELETE FROM session_records WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt")
    protected abstract suspend fun deleteRecord(workoutId: String, startedAt: Long)

    /** Removes and returns the most recent set of the session, with its rest. */
    @Transaction
    open suspend fun deleteLast(workoutId: String, startedAt: Long): LoggedSetEntity? =
        last(workoutId, startedAt)?.also {
            delete(it)
            deleteRest(it.id)
        }

    @Query("DELETE FROM rests WHERE loggedSetId = :setId")
    protected abstract suspend fun deleteRest(setId: Long)

    @Query(
        "SELECT * FROM logged_sets WHERE workoutId = :workoutId AND sessionStartedAtMillis = :startedAt " +
            "ORDER BY loggedAtMillis DESC, id DESC LIMIT 1"
    )
    protected abstract suspend fun last(workoutId: String, startedAt: Long): LoggedSetEntity?

    @Delete
    protected abstract suspend fun delete(set: LoggedSetEntity)
}
