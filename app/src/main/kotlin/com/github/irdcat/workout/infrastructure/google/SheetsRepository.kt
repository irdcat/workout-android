package com.github.irdcat.workout.infrastructure.google

import com.google.api.client.http.GenericUrl
import com.google.api.client.json.GenericJson
import com.google.api.client.util.DateTime
import com.google.api.client.util.Key
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A tab of the spreadsheet. */
data class Sheet(
    val index: Int,
    val name: String
)

/** Text for one cell, e.g. [range] "'CZERWIEC'!H42". */
data class CellWrite(val range: String, val text: String) {
    constructor(sheetName: String, column: String, row: Int, text: String) : this("${quoted(sheetName)}!$column$row", text)
}

/** A tab name for a range, quoted (doubling any quotes in it) so names with spaces or symbols work. */
fun quoted(sheetName: String): String = "'${sheetName.replace("'", "''")}'"

data class SheetRow(
    /** 0-based position from the start of the requested range. */
    val index: Int,
    val sheetName: String,
    val values: List<String>
)

/** A version of the saved spreadsheet: when Drive says the file last changed. */
data class SheetVersion(val spreadsheetId: String, val modifiedAt: Instant)

/** Drive's metadata endpoint for a file; the spreadsheet ID is its Drive file ID. */
private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files/"

/** The part of a Drive file resource read here. */
class DriveFile : GenericJson() {
    @Key
    var modifiedTime: DateTime? = null
}

/** Raw access to the configured spreadsheet. Blocking calls run on [Dispatchers.IO]. */
@Singleton
class SheetsRepository @Inject constructor(
    private val sheetsConfigDataStore: SheetsConfigDataStore,
    private val sheets: Sheets
) {

    /**
     * Tabs of the spreadsheet at [sheetUrl], or of the saved one by default.
     * Passing a link lets Setup check it before it is saved.
     */
    suspend fun listSheets(sheetUrl: String? = null): List<Sheet> = withContext(Dispatchers.IO) {
        val spreadsheetId = if (sheetUrl != null) {
            requireNotNull(spreadsheetIdOf(sheetUrl)) { "Not a spreadsheet link: '$sheetUrl'" }
        } else {
            savedSpreadsheetId()
        }
        sheets.spreadsheets().get(spreadsheetId).execute()
            .sheets
            .map { Sheet(it.properties.index, it.properties.title) }
    }

    /**
     * Rows of the same range in each of [tabs], in their order, read in one request
     * (batchGet) so a sync costs one round-trip and one quota unit, however many tabs.
     */
    suspend fun readSheetsData(
        tabs: List<Sheet>,
        startCell: String,
        endCell: String
    ): List<List<SheetRow>> = withContext(Dispatchers.IO) {
        if (tabs.isEmpty()) return@withContext emptyList()
        val valueRanges = sheets.spreadsheets().values()
            .batchGet(savedSpreadsheetId())
            .setRanges(tabs.map { "${quoted(it.name)}!$startCell:$endCell" })
            .execute()
            .valueRanges
            .orEmpty()
        // Value ranges come back in the order they were asked for.
        if (valueRanges.size != tabs.size) {
            throw IOException("Asked for ${tabs.size} ranges, got ${valueRanges.size}")
        }
        tabs.zip(valueRanges) { sheet, valueRange ->
            // The position, not indexOf: identical rows (e.g. empty ones) must keep their own index.
            valueRange.getValues().orEmpty().mapIndexed { index, row ->
                SheetRow(
                    index = index,
                    sheetName = sheet.name,
                    values = row.map { it.toString() }
                )
            }
        }
    }

    /**
     * Writes [cells] to the saved spreadsheet in one request, each as plain text
     * ("RAW": "8" stays text, nothing is read as a formula).
     */
    suspend fun writeCells(cells: List<CellWrite>) = withContext(Dispatchers.IO) {
        if (cells.isEmpty()) return@withContext
        val request = BatchUpdateValuesRequest()
            .setValueInputOption("RAW")
            .setData(cells.map { ValueRange().setRange(it.range).setValues(listOf(listOf(it.text))) })
        sheets.spreadsheets().values().batchUpdate(savedSpreadsheetId(), request).execute()
    }

    /**
     * When the saved spreadsheet last changed, by anyone, as Drive reports it. Asked
     * through the Sheets client so the same account and Drive scope are used. null
     * when Drive leaves the time out.
     */
    suspend fun currentVersion(): SheetVersion? = withContext(Dispatchers.IO) {
        val spreadsheetId = savedSpreadsheetId()
        val url = GenericUrl(DRIVE_FILES_URL + spreadsheetId).apply {
            set("fields", "modifiedTime")
            set("supportsAllDrives", true)
        }
        val file = sheets.requestFactory.buildGetRequest(url)
            .setParser(sheets.jsonFactory.createJsonObjectParser())
            .execute()
            .parseAs(DriveFile::class.java)
        file.modifiedTime?.let { SheetVersion(spreadsheetId, Instant.ofEpochMilli(it.value)) }
    }

    private suspend fun savedSpreadsheetId(): String {
        val config = sheetsConfigDataStore.sheetsConfigFlow().first()
        return requireNotNull(config.spreadsheetId) { "No spreadsheet link saved: '${config.sheetUrl}'" }
    }
}
