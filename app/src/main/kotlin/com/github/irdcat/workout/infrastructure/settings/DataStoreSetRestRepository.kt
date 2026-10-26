package com.github.irdcat.workout.infrastructure.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.warmUpRestKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.setRestDataStore: DataStore<Preferences> by preferencesDataStore(name = "set_rests")

/** One key per exercise name, in its own DataStore file; names are matched as for warm-up rests. */
@Singleton
class DataStoreSetRestRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SetRestRepository {

    override fun observe(exerciseName: String): Flow<Int?> = context.setRestDataStore.data
        .map { it[keyOf(exerciseName)] }
        .distinctUntilChanged()

    override suspend fun save(exerciseName: String, seconds: Int) {
        context.setRestDataStore.edit { it[keyOf(exerciseName)] = seconds.coerceAtLeast(0) }
    }

    private fun keyOf(exerciseName: String) = intPreferencesKey(warmUpRestKey(exerciseName))
}
