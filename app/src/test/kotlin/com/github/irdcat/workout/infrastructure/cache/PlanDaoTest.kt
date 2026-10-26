package com.github.irdcat.workout.infrastructure.cache

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.infrastructure.google.SheetFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The real DAO against an in-memory database, filled with the parsed test-data sheets. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlanDaoTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var dao: PlanDao

    private val parsed = SheetFixtures.parsed()

    private fun sheets(syncedAt: Long = 1_000, names: List<String> = parsed.map { it.first }) =
        parsed.filter { it.first in names }.mapIndexed { index, (name, content) ->
            SheetEntity(name, index, syncedAt) to content
        }

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.planDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun storedPlanReadsBackTheSameAsTheParsedSheets() = runTest {
        dao.replaceAll(sheets())

        // Room returns @Relation lists in any order; the mapping must still match the parser's.
        assertEquals(SheetFixtures.months(), dao.observeWeeks().first().toMonths())
    }

    @Test
    fun workoutsAndTheirWeeksByWorkoutId() = runTest {
        dao.replaceAll(sheets())

        val workout = dao.observeWorkout("CZERWIEC2026/1/T2").first()!!.toDomain()
        assertEquals("T2", workout.code)
        assertEquals("RDL", workout.exercises[1].name)

        val week = dao.observeWeekOfWorkout("LIPIEC2026/1/T1").first()!!.toDomain()
        assertEquals("LIPIEC", week.sheetName)
        assertEquals(LocalDate.of(2026, 6, 29), week.start)

        assertNull(dao.observeWorkout("NOPE2026/1/T1").first())
    }

    @Test
    fun resyncReplacesTabsAndDropsTheOnesNoLongerSynced() = runTest {
        dao.replaceAll(sheets(syncedAt = 1_000))
        assertEquals(8, dao.observeSheetCount().first())
        assertEquals(30, dao.observeWeekCount().first())

        // Same tabs again: no duplicate keys, counts unchanged.
        dao.replaceAll(sheets(syncedAt = 2_000))
        assertEquals(30, dao.observeWeekCount().first())
        assertEquals(2_000L, dao.observeLastSyncedAt().first())

        // Blacklisting all but June removes the other tabs and, by cascade, their weeks and rows.
        dao.replaceAll(sheets(syncedAt = 3_000, names = listOf("CZERWIEC")))
        assertEquals(1, dao.observeSheetCount().first())
        assertEquals(4, dao.observeWeekCount().first())
        assertTrue(dao.observeWeeks().first().all { it.week.sheetName == "CZERWIEC" })
        assertNull(dao.observeWorkout("LIPIEC2026/1/T1").first())
    }

    @Test
    fun deleteAllEmptiesTheCache() = runTest {
        dao.replaceAll(sheets())
        dao.deleteAll()

        assertTrue(dao.observeWeeks().first().isEmpty())
        assertEquals(0, dao.observeSheetCount().first())
        assertNull(dao.observeLastSyncedAt().first())
    }
}
