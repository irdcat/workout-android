package com.github.irdcat.workout.infrastructure.google

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

    suspend fun readSheetData(
        sheet: Sheet,
        startCell: String,
        endCell: String
    ): List<SheetRow> = withContext(Dispatchers.IO) {
        val range = "${quoted(sheet.name)}!$startCell:$endCell"
        val values = sheets.spreadsheets().values()
            .get(savedSpreadsheetId(), range)
            .execute()
            .getValues()
            .orEmpty()
        // The position, not indexOf: identical rows (e.g. empty ones) must keep their own index.
        values.mapIndexed { index, row ->
            SheetRow(
                index = index,
                sheetName = sheet.name,
                values = row.map { it.toString() }
            )
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

    private suspend fun savedSpreadsheetId(): String {
        val config = sheetsConfigDataStore.sheetsConfigFlow().first()
        return requireNotNull(config.spreadsheetId) { "No spreadsheet link saved: '${config.sheetUrl}'" }
    }
}
