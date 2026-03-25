package com.github.irdcat.workout.data.google

import android.accounts.Account
import android.content.Context
import com.github.irdcat.workout.R
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSheetsDataSource(private val context: Context) {

    suspend fun buildSheetsService(account: Account): Sheets = withContext(Dispatchers.IO) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(SheetsScopes.SPREADSHEETS)
        )
        credential.selectedAccount = account
        Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential)
            .setApplicationName(context.getStringResource(R.string.app_name))
            .build()!!
    }

    suspend fun readSheetData(sheetsService: Sheets, speadsheetId: String, range: String): List<List<Any>>
        = withContext(Dispatchers.IO) {
            sheetsService.spreadsheets().values()
                .get(speadsheetId, range)
                .execute()
                .values
                .drop(2)[0] as List<List<Any>>
        }

    suspend fun readSheetsFromSpreadsheet(sheetsService: Sheets, spreadsheetId: String): List<Sheet>
        = withContext(Dispatchers.IO) {
            sheetsService.spreadsheets()
                .get(spreadsheetId)
                .execute()
                .sheets
        }

    suspend fun updateCells(sheetsService: Sheets, spreadsheetId: String, references: Map<String, String>)
        = withContext(Dispatchers.IO) {
            val result = sheetsService.spreadsheets().values()
                .batchUpdate(
                    spreadsheetId,
                    BatchUpdateValuesRequest()
                        .setValueInputOption("RAW")
                        .setIncludeValuesInResponse(false)
                        .setData(
                            references.map {
                                ValueRange()
                                    .setRange(it.key)
                                    .setValues(listOf(listOf(it.value)))
                            }
                        ))
                .execute()
            if (result.totalUpdatedCells != references.size) {
                throw IllegalStateException("Requested update of ${references.size} cells, but only ${result.totalUpdatedCells} were updated")
            }
    }
}