package com.github.irdcat.workout.infrastructure.cache

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A result entered in the app and not in the sheet yet. Keyed like
 * [com.github.irdcat.workout.domain.results.ResultTarget], not by row, and kept
 * apart from the plan tables so syncing or clearing the plan never loses it.
 */
@Entity(
    tableName = "saved_results",
    primaryKeys = ["workoutId", "exerciseNumber", "groupIndex"],
)
data class SavedResultEntity(
    val workoutId: String,
    val exerciseNumber: String,
    val groupIndex: Int,
    val exerciseName: String,
    val text: String,
    val savedAtMillis: Long,
)

@Dao
abstract class ResultDao {

    @Query("SELECT * FROM saved_results ORDER BY savedAtMillis")
    abstract fun observeAll(): Flow<List<SavedResultEntity>>

    @Query("SELECT * FROM saved_results ORDER BY savedAtMillis")
    abstract suspend fun all(): List<SavedResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(result: SavedResultEntity)

    @Query("DELETE FROM saved_results WHERE workoutId = :workoutId AND savedAtMillis >= :sinceMillis")
    abstract suspend fun deleteSavedSince(workoutId: String, sinceMillis: Long)

    /**
     * Once [written] are in the sheet: the cached plan gets their text (so it shows
     * before the next sync) and they stop being saved here, in one go.
     */
    @Transaction
    open suspend fun markWritten(written: List<Pair<SavedResultEntity, String>>) {
        for ((result, setGroupId) in written) {
            updateCachedResult(setGroupId, result.text)
            // Only if it wasn't saved again while being written.
            deleteIfUnchanged(result.workoutId, result.exerciseNumber, result.groupIndex, result.savedAtMillis)
        }
    }

    @Query("UPDATE set_groups SET result = :text WHERE id = :setGroupId")
    protected abstract suspend fun updateCachedResult(setGroupId: String, text: String)

    @Query(
        "DELETE FROM saved_results WHERE workoutId = :workoutId AND exerciseNumber = :exerciseNumber " +
            "AND groupIndex = :groupIndex AND savedAtMillis = :savedAtMillis"
    )
    protected abstract suspend fun deleteIfUnchanged(workoutId: String, exerciseNumber: String, groupIndex: Int, savedAtMillis: Long)
}
