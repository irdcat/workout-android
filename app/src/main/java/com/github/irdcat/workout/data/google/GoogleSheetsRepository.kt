package com.github.irdcat.workout.data.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.github.irdcat.workout.R
import com.github.irdcat.workout.data.model.SheetMetadataDto
import com.github.irdcat.workout.data.model.SheetRowDto
import com.github.irdcat.workout.domain.AuthState
import com.github.irdcat.workout.domain.DataLoadState
import com.github.irdcat.workout.domain.SheetsRepository
import com.github.irdcat.workout.domain.usecase.WorkoutsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoogleSheetsRepository(
    private val googleSignInDataSource: GoogleSignInDataSource,
    private val sheetsDataSource: GoogleSheetsDataSource,
    context: Context,
): SheetsRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    private val _dataState = MutableStateFlow<DataLoadState>(DataLoadState.Loading)

    private val spreadsheetId = context.getStringResource(R.string.spreadsheet_id)
    private val range = context.getStringResource(R.string.spreadsheet_range)

    private val parser = WorkoutsParser()

    override fun getAuthState(): Flow<AuthState> = _authState.asStateFlow()
    override suspend fun getSheetData(): Flow<DataLoadState> = _dataState.asStateFlow()

    override suspend fun checkExistingAccount() {
        _authState.value = AuthState.Authenticating
        val account = googleSignInDataSource.getLastSignedInAccount()
        if (account != null) {
            _authState.value = AuthState.Authenticated
            loadSheetData(account.account!!)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun signIn(data: Intent) {
        _authState.value = AuthState.Authenticating
        try {
            val account = googleSignInDataSource.handleSignResult(data)
            _authState.value = AuthState.Authenticated
            loadSheetData(account.account!!)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
        }
    }

    private suspend fun loadSheetData(account: Account) {
        _dataState.value = DataLoadState.Loading
        try {
            val sheetsService = sheetsDataSource.buildSheetsService(account)
            val sheetNamesDescending =
                sheetsDataSource.readSheetsFromSpreadsheet(sheetsService, spreadsheetId)
                    .asSequence()
                    .map { SheetMetadataDto(it.properties.index, it.properties.title) }
                    .sortedBy { it.index }
                    .drop(2)
                    .sortedByDescending { it.index }
                    .map { it.name }
                    .toList()

            val parsed = sheetNamesDescending
                .map {
                    it to sheetsDataSource.readSheetData(
                        sheetsService,
                        spreadsheetId,
                        "${it}!${range}"
                    )
                }
                .map {
                    it.second.mapIndexed { index, list ->
                        SheetRowDto(
                            index + 1,
                            it.first,
                            list.map(Any::toString)
                        )
                    }
                }.flatMap {
                    parser.parse(it)
                }.sortedByDescending { it.endDate }

            _dataState.value = DataLoadState.Success(parsed)
        } catch (e: Exception) {
            _dataState.value = DataLoadState.Error(e.message ?: "Failed to load data")
        }
    }

    override fun getSignInIntent(): Intent = googleSignInDataSource.getSignInIntent()

    override suspend fun updateSheet(references: Map<String, String>) {
        val account = googleSignInDataSource.getLastSignedInAccount()!!
        val sheetsService = sheetsDataSource.buildSheetsService(account.account!!)
        sheetsDataSource.updateCells(sheetsService, spreadsheetId, references)
    }
}