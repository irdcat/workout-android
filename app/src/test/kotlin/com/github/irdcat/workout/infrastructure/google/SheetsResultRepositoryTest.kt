package com.github.irdcat.workout.infrastructure.google

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.results.WriteResultsOutcome
import com.github.irdcat.workout.domain.results.resultTarget
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.infrastructure.cache.SheetEntity
import com.github.irdcat.workout.infrastructure.cache.WorkoutDatabase
import com.github.irdcat.workout.infrastructure.cache.toDomain
import com.github.irdcat.workout.testing.fakeSheetsRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Results in a real in-memory database, written through a fake Sheets API, on the test-data plan. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SheetsResultRepositoryTest {

    private lateinit var database: WorkoutDatabase
    private val sheetsConfig = SheetsConfigDataStore(ApplicationProvider.getApplicationContext())

    /** The ranges and values of every cell written, in order. */
    private val writes = mutableListOf<Pair<String, String>>()
    private var requests = 0

    private fun repository(status: Int = 200) = SheetsResultRepository(
        database.resultDao(),
        database.planDao(),
        fakeSheetsRepository(sheetsConfig, emptyList(), status) { request ->
            requests++
            val body = request.contentAsString.takeIf { it.isNotEmpty() } ?: return@fakeSheetsRepository
            for (data in JsonParser.parseString(body).asJsonObject.getAsJsonArray("data")) {
                val cell = data.asJsonObject
                writes += cell.get("range").asString to cell.getAsJsonArray("values")[0].asJsonArray[0].asString
            }
        },
    )

    private fun planRepository(results: SheetsResultRepository) =
        SheetsWorkoutPlanRepository(fakeSheetsRepository(sheetsConfig, emptyList()), sheetsConfig, database.planDao(), results)

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.planDao().replaceAll(SheetFixtures.parsed().mapIndexed { index, (name, content) -> SheetEntity(name, index, 1_000) to content })
        sheetsConfig.updateSheetsConfig("https://docs.google.com/spreadsheets/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit", emptySet())
    }

    @After
    fun closeDatabase() = database.close()

    private suspend fun t2(): Workout = database.planDao().workout("CZERWIEC2026/1/T2")!!.toDomain()

    private suspend fun rdlResult(text: String, savedAt: Long = 1): SavedResult {
        val workout = t2()
        val rdl = workout.exercises.first { it.name == "RDL" }
        return SavedResult(workout.resultTarget(rdl, rdl.setGroups.last()), text, Instant.ofEpochMilli(savedAt))
    }

    @Test
    fun writesToTheWynikCellOfTheRowFromTheLatestSync() = runTest {
        val results = repository()
        results.save(rdlResult("RPE 9, 92.5×5"))
        val row = t2().exercises.first { it.name == "RDL" }.setGroups.last().sheetRow

        assertEquals(WriteResultsOutcome.Written(1), results.writeSaved())

        assertEquals(listOf("'CZERWIEC'!H$row" to "RPE 9, 92.5×5"), writes)
        assertTrue(results.observeSaved().first().isEmpty())
        // The cached plan has it straight away, before the next sync reads it back.
        assertEquals("RPE 9, 92.5×5", t2().exercises.first { it.name == "RDL" }.setGroups.last().result)
    }

    @Test
    fun theSavedResultShowsInThePlanUntilItIsWritten() = runTest {
        val results = repository()
        results.save(rdlResult("RPE 9"))

        val shown = planRepository(results).observeWorkout("CZERWIEC2026/1/T2").first()!!
        assertEquals("RPE 9", shown.exercises.first { it.name == "RDL" }.setGroups.last().result)
    }

    @Test
    fun anExerciseThatWasRenamedDoesNotTakeTheResult() = runTest {
        val results = repository()
        val saved = rdlResult("RPE 9")
        results.save(saved.copy(target = saved.target.copy(exerciseName = "Something else")))

        val shown = planRepository(results).observeWorkout("CZERWIEC2026/1/T2").first()!!
        assertEquals(t2(), shown)
        // Nor is it written anywhere: it waits for its exercise.
        assertEquals(WriteResultsOutcome.NothingToWrite, results.writeSaved())
        assertEquals(0, requests)
        assertEquals(1, results.observeSaved().first().size)
    }

    @Test
    fun aFailedWriteKeepsTheResultForTheNextTry() = runTest {
        val results = repository(status = 500)
        results.save(rdlResult("RPE 9"))
        val before = t2()

        assertEquals(WriteResultsOutcome.Failed::class, results.writeSaved()::class)

        assertEquals(1, results.observeSaved().first().size)
        assertEquals(before, t2())
    }

    @Test
    fun savingTheSameCellAgainReplacesTheResult() = runTest {
        val results = repository()
        results.save(rdlResult("RPE 8", savedAt = 1))
        results.save(rdlResult("RPE 9", savedAt = 2))

        assertEquals(listOf("RPE 9"), results.observeSaved().first().map { it.text })
    }
}
