package com.github.irdcat.workout.testing

import com.github.irdcat.workout.infrastructure.google.SheetsConfigDataStore
import com.github.irdcat.workout.infrastructure.google.SheetsRepository
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.sheets.v4.Sheets

/**
 * A Sheets API client that answers every request with the tabs in [sheetNames], or
 * with HTTP [status] when it isn't 200. [onRequest] sees each request, e.g. to read
 * what was written.
 */
fun fakeSheets(
    sheetNames: List<String>,
    status: Int = 200,
    onRequest: (MockLowLevelHttpRequest) -> Unit = {},
): Sheets {
    val tabs = sheetNames.mapIndexed { index, name -> """{"properties":{"index":$index,"title":"$name"}}""" }
    val body = """{"sheets":[${tabs.joinToString(",")}]}"""
    // A fresh response per request: a response's body can only be read once.
    val transport = object : MockHttpTransport() {
        override fun buildRequest(method: String, url: String) = object : MockLowLevelHttpRequest(url) {
            override fun execute(): MockLowLevelHttpResponse {
                onRequest(this)
                return MockLowLevelHttpResponse()
                    .setStatusCode(status)
                    .setContentType("application/json")
                    .setContent(if (status == 200) body else """{"error":{"code":$status,"message":"failed"}}""")
            }
        }
    }
    return Sheets.Builder(transport, GsonFactory.getDefaultInstance(), null)
        .setApplicationName("Workout test")
        .build()
}

/** A [SheetsRepository] whose Sheets API answers every request with the tabs in [sheetNames]. */
fun fakeSheetsRepository(
    configDataStore: SheetsConfigDataStore,
    sheetNames: List<String>,
    status: Int = 200,
    onRequest: (MockLowLevelHttpRequest) -> Unit = {},
): SheetsRepository = SheetsRepository(configDataStore, fakeSheets(sheetNames, status, onRequest))
