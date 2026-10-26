package com.github.irdcat.workout.infrastructure.google

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "google-account")

private val EmailKey = stringPreferencesKey("email")

/** Remembers which Google account is signed in. */
@Singleton
class AccountDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun emailFlow(): Flow<String?> = context.accountDataStore.data.map { it[EmailKey] }

    suspend fun setEmail(email: String?) {
        context.accountDataStore.edit { preferences ->
            if (email == null) preferences.remove(EmailKey) else preferences[EmailKey] = email
        }
    }
}
