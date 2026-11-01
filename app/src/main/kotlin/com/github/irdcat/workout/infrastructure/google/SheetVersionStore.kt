package com.github.irdcat.workout.infrastructure.google

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sheetVersionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sheet-version")

private val SpreadsheetIdKey = stringPreferencesKey("spreadsheetId")
private val ModifiedAtKey = longPreferencesKey("modifiedAtMillis")

/** Remembers the version of the sheet the cache was synced from, across restarts. */
@Singleton
class SheetVersionStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun get(): SheetVersion? {
        val preferences = context.sheetVersionDataStore.data.first()
        val id = preferences[SpreadsheetIdKey] ?: return null
        val modifiedAt = preferences[ModifiedAtKey] ?: return null
        return SheetVersion(id, Instant.ofEpochMilli(modifiedAt))
    }

    suspend fun set(version: SheetVersion?) {
        context.sheetVersionDataStore.edit { preferences ->
            if (version == null) {
                preferences.remove(SpreadsheetIdKey)
                preferences.remove(ModifiedAtKey)
            } else {
                preferences[SpreadsheetIdKey] = version.spreadsheetId
                preferences[ModifiedAtKey] = version.modifiedAt.toEpochMilli()
            }
        }
    }
}
