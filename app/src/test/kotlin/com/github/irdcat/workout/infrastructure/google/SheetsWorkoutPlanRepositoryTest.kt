package com.github.irdcat.workout.infrastructure.google

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.results.resultTarget
import com.github.irdcat.workout.domain.workout.SyncResult
import com.github.irdcat.workout.infrastructure.cache.WorkoutDatabase
import com.github.irdcat.workout.testing.FakeResultRepository
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.sheets.v4.Sheets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URLDecoder
import java.time.Instant

/** Syncing the test-data spreadsheet through a fake Sheets API into a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SheetsWorkoutPlanRepositoryTest {

    private lateinit var database: WorkoutDatabase
    private val sheetsConfig = SheetsConfigDataStore(ApplicationProvider.getApplicationContext())
    private val results = FakeResultRepository()

    private val tabs = SheetFixtures.sheets().map { it.first }
    private val sheetFiles: Map<String, File> = SheetFixtures.files().toMap()

    /** Tabs whose values were requested, in order. */
    private val read = mutableListOf<String>()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sheetsConfig.updateSheetsConfig("https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit", emptySet())
    }

    @After
    fun closeDatabase() = database.close()

    /** Answers the tab list and each tab's values from test-data; any other [status] fails every request. */
    private fun sheets(status: Int = 200): Sheets {
        val transport = object : MockHttpTransport() {
            override fun buildRequest(method: String, url: String) = object : MockLowLevelHttpRequest(url) {
                override fun execute(): MockLowLevelHttpResponse {
                    val response = MockLowLevelHttpResponse().setContentType("application/json")
                    if (status != 200) {
                        return response.setStatusCode(status).setContent("""{"error":{"code":$status,"message":"failed"}}""")
                    }
                    val decoded = URLDecoder.decode(url, "UTF-8")
                    val body = if ("/values/" in decoded) {
                        val tab = decoded.substringAfter("/values/'").substringBefore("'!")
                        read += tab
                        sheetFiles.getValue(tab).readText()
                    } else {
                        val sheets = tabs.mapIndexed { index, name -> """{"properties":{"index":$index,"title":"$name"}}""" }
                        """{"sheets":[${sheets.joinToString(",")}]}"""
                    }
                    return response.setContent(body)
                }
            }
        }
        return Sheets.Builder(transport, GsonFactory.getDefaultInstance(), null).setApplicationName("Workout test").build()
    }

    private fun repository(status: Int = 200) = SheetsWorkoutPlanRepository(
        SheetsRepository(sheetsConfig, sheets(status)),
        sheetsConfig,
        database.planDao(),
        results,
    )

    @Test
    fun syncReadsEveryTabIntoTheCache() = runTest {
        val repository = repository()

        val result = repository.sync()

        val expectedWeeks = SheetFixtures.parsed().sumOf { it.second.weeks.size }
        assertEquals(SyncResult.Success(sheetCount = tabs.size, weekCount = expectedWeeks), result)
        assertEquals(tabs, read)
        val months = repository.observeMonths().first()
        assertEquals(expectedWeeks, months.sumOf { it.weeks.size })
        val info = repository.observeSyncInfo().first()
        assertEquals(tabs.size, info.sheetCount)
        assertEquals(expectedWeeks, info.weekCount)
        assertNotNull(info.lastSyncedAt)
    }

    @Test
    fun blacklistedTabsAreNotRead() = runTest {
        sheetsConfig.updateSheetsConfig("https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit", setOf("LUTY", "MARZEC"))

        val result = repository().sync()

        assertEquals(tabs.size - 2, (result as SyncResult.Success).sheetCount)
        assertEquals(tabs - setOf("LUTY", "MARZEC"), read)
    }

    @Test
    fun withoutALinkThereIsNothingToSync() = runTest {
        sheetsConfig.updateSheetsConfig("", emptySet())

        assertEquals(SyncResult.NotConfigured, repository().sync())
        assertTrue(read.isEmpty())
    }

    @Test
    fun aFailedSyncLeavesTheCacheAsItWas() = runTest {
        repository().sync()
        val before = repository().observeMonths().first()

        val result = repository(status = 500).sync()

        assertTrue(result is SyncResult.Failed)
        assertEquals(before, repository().observeMonths().first())
    }

    @Test
    fun resultsSavedInTheAppShowInPlaceOfTheSheets() = runTest {
        val repository = repository()
        repository.sync()
        val workout = repository.observeWorkout("CZERWIEC2026/1/T2").first()!!
        val rdl = workout.exercises.first { it.name == "RDL" }
        val group = rdl.setGroups.last()

        results.save(SavedResult(workout.resultTarget(rdl, group), "RPE 9, 92.5×5", Instant.EPOCH))

        val shown = repository.observeWorkout(workout.id).first()!!
        assertEquals("RPE 9, 92.5×5", shown.exercises.first { it.name == "RDL" }.setGroups.last().result)
        val week = repository.observeWeekOfWorkout(workout.id).first()!!
        assertEquals(shown, week.workouts.first { it.id == workout.id })
        assertEquals(week, repository.observeWeek(week.id).first())
        val inMonths = repository.observeMonths().first().flatMap { it.weeks }.first { it.id == week.id }
        assertEquals(week, inMonths)
    }

    @Test
    fun clearingTheCacheForgetsThePlan() = runTest {
        val repository = repository()
        repository.sync()

        repository.clearCache()

        assertTrue(repository.observeMonths().first().isEmpty())
        assertNull(repository.observeSyncInfo().first().lastSyncedAt)
    }
}
