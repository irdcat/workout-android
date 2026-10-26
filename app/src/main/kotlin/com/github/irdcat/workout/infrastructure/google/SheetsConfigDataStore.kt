package com.github.irdcat.workout.infrastructure.google

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SheetsConfig(
    val sheetUrl: String = "",
    val sheetBlacklist: Set<String> = emptySet()
) {
    fun isConfigured() = sheetUrl.isNotEmpty()

    val spreadsheetId: String? get() = spreadsheetIdOf(sheetUrl)
}

private val SpreadsheetPath = Regex("""/spreadsheets/d/([A-Za-z0-9_-]+)""")
private val BareSpreadsheetId = Regex("""^[A-Za-z0-9_-]{20,}$""")

/**
 * The spreadsheet ID in a link such as `https://docs.google.com/spreadsheets/d/<id>/edit`,
 * with or without the scheme; a bare ID is accepted too. null when there is none.
 */
fun spreadsheetIdOf(sheetUrl: String): String? {
    val text = sheetUrl.trim()
    SpreadsheetPath.find(text)?.let { return it.groupValues[1] }
    return text.takeIf { BareSpreadsheetId.matches(it) }
}

@Singleton
class SheetsConfigDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun sheetsConfigFlow(): Flow<SheetsConfig> = context.sheetsConfigDataStore.data

    suspend fun updateSheetsConfig(
        sheetUrl: String,
        sheetBlacklist: Set<String>
    ) {
        context.sheetsConfigDataStore.updateData {
            SheetsConfig(sheetUrl, sheetBlacklist)
        }
    }
}

private val Context.sheetsConfigDataStore: DataStore<SheetsConfig> by dataStore(
    fileName = "sheets-config.json",
    serializer = SheetsConfigSerializer,
    scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
)

private object SheetsConfigSerializer : Serializer<SheetsConfig> {
    override val defaultValue: SheetsConfig = SheetsConfig()

    override suspend fun readFrom(input: InputStream): SheetsConfig =
        try {
            Json.decodeFromString(SheetsConfig.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unable to read Sheets configuration", e)
        }

    override suspend fun writeTo(
        t: SheetsConfig,
        output: OutputStream
    ) {
        withContext(Dispatchers.IO) {
            output.write(Json.encodeToString(SheetsConfig.serializer(), t).encodeToByteArray())
        }
    }
}
