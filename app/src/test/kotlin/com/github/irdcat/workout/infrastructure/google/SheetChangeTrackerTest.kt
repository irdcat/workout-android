package com.github.irdcat.workout.infrastructure.google

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.sheets.v4.Sheets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Telling sheet edits from the app's own, through a fake Drive endpoint. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SheetChangeTrackerTest {

    private val sheetsConfig = SheetsConfigDataStore(ApplicationProvider.getApplicationContext())
    private val versions = SheetVersionStore(ApplicationProvider.getApplicationContext())

    /** Drive's modified time of the file; null leaves it out of the response. */
    private var modifiedTime: Instant? = Instant.parse("2026-06-01T10:00:00Z")

    /** Drive answers with this status; anything but 200 fails the request. */
    private var driveStatus = 200
    private val driveUrls = mutableListOf<String>()

    private val tracker: SheetChangeTracker by lazy {
        val transport = object : MockHttpTransport() {
            override fun buildRequest(method: String, url: String) = object : MockLowLevelHttpRequest(url) {
                override fun execute(): MockLowLevelHttpResponse {
                    val response = MockLowLevelHttpResponse().setContentType("application/json")
                    if ("/drive/v3/files/" !in url) return response.setContent("{}")
                    driveUrls += url
                    if (driveStatus != 200) {
                        return response.setStatusCode(driveStatus).setContent("""{"error":{"code":$driveStatus,"message":"failed"}}""")
                    }
                    return response.setContent(modifiedTime?.let { """{"modifiedTime":"$it"}""" } ?: "{}")
                }
            }
        }
        val sheets = Sheets.Builder(transport, GsonFactory.getDefaultInstance(), null).setApplicationName("Workout test").build()
        SheetChangeTracker(SheetsRepository(sheetsConfig, sheets), versions)
    }

    @Before
    fun setUp() = runTest {
        sheetsConfig.updateSheetsConfig("https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit", emptySet())
        versions.set(null)
    }

    private suspend fun synced() = tracker.reading { }

    @Test
    fun asksDriveAboutTheSavedSpreadsheet() = runTest {
        synced()

        assertTrue(driveUrls.single().contains("/drive/v3/files/1AbCdEfGhIjKlMnOpQrStUvWxYz?"))
        assertTrue(driveUrls.single().contains("fields=modifiedTime"))
    }

    @Test
    fun unchangedAfterASync() = runTest {
        synced()

        assertEquals(false, tracker.changedSinceSync())
    }

    @Test
    fun anEditAfterTheSyncIsAChange() = runTest {
        synced()
        modifiedTime = Instant.parse("2026-06-01T10:05:00Z")

        assertEquals(true, tracker.changedSinceSync())
    }

    @Test
    fun anEditWhileReadingIsAChange() = runTest {
        tracker.reading { modifiedTime = Instant.parse("2026-06-01T10:00:01Z") }

        assertEquals(true, tracker.changedSinceSync())
    }

    @Test
    fun theAppsOwnWriteIsNotAChange() = runTest {
        synced()

        tracker.writing { modifiedTime = Instant.parse("2026-06-01T10:05:00Z") }

        assertEquals(false, tracker.changedSinceSync())
    }

    @Test
    fun aWriteDoesNotHideAnEarlierEdit() = runTest {
        synced()
        modifiedTime = Instant.parse("2026-06-01T10:05:00Z")

        tracker.writing { modifiedTime = Instant.parse("2026-06-01T10:06:00Z") }

        assertEquals(true, tracker.changedSinceSync())
    }

    @Test
    fun aFailedReadKeepsThePreviousVersion() = runTest {
        synced()
        modifiedTime = Instant.parse("2026-06-01T10:05:00Z")

        runCatching { tracker.reading { error("read failed") } }

        assertEquals(true, tracker.changedSinceSync())
    }

    @Test
    fun unknownBeforeTheFirstSync() = runTest {
        assertNull(tracker.changedSinceSync())
    }

    @Test
    fun unknownWhenDriveCantBeAsked() = runTest {
        synced()
        driveStatus = 403

        assertNull(tracker.changedSinceSync())
    }

    @Test
    fun unknownWithoutAModifiedTime() = runTest {
        modifiedTime = null
        synced()

        assertNull(tracker.changedSinceSync())
    }

    @Test
    fun unknownAfterLinkingAnotherSheet() = runTest {
        synced()
        sheetsConfig.updateSheetsConfig("https://docs.google.com/spreadsheets/d/9ZyXwVuTsRqPoNmLkJiHgFeDcBa/edit", emptySet())

        assertNull(tracker.changedSinceSync())
    }

    @Test
    fun forgottenAfterClearing() = runTest {
        synced()
        tracker.forget()

        assertNull(tracker.changedSinceSync())
    }
}
