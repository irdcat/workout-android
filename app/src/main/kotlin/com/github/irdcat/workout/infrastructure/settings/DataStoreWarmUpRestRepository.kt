package com.github.irdcat.workout.infrastructure.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.session.warmUpRestKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.warmUpRestDataStore: DataStore<Preferences> by preferencesDataStore(name = "warm_up_rests")

/** One key per exercise name, in its own DataStore file. */
@Singleton
class DataStoreWarmUpRestRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WarmUpRestRepository {

    override fun observe(exerciseName: String): Flow<Int?> = context.warmUpRestDataStore.data
        .map { it[keyOf(exerciseName)] }
        .distinctUntilChanged()

    override suspend fun save(exerciseName: String, seconds: Int) {
        context.warmUpRestDataStore.edit { it[keyOf(exerciseName)] = seconds.coerceAtLeast(0) }
    }

    private fun keyOf(exerciseName: String) = intPreferencesKey(warmUpRestKey(exerciseName))
}
