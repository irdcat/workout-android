package com.github.irdcat.workout.infrastructure.session

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.PastSession
import com.github.irdcat.workout.infrastructure.cache.WorkoutDatabase
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
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoomSessionLogRepositoryTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomSessionLogRepository

    private val session = ActiveSession("CZERWIEC2026/1/T2", Instant.parse("2026-06-03T17:42:00Z"))

    private fun set(exerciseId: String, second: Long, weight: Double? = 60.0) = LoggedSet(
        exerciseId = exerciseId,
        setGroupId = "$exerciseId-g1",
        weightKg = weight,
        reps = 5,
        rpe = 8.5,
        warmUp = second == 0L,
        loggedAt = session.startedAt.plusSeconds(second),
    )

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomSessionLogRepository(database.sessionLogDao())
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun logsSetsInOrderWithEverythingEntered() = runTest {
        repository.logSet(session, set("rdl", 0))
        repository.logSet(session, set("rdl", 200, weight = null))

        val sets = repository.observe(session).first().sets
        assertEquals(listOf(true, false), sets.map { it.warmUp })
        assertEquals(listOf(60.0, null), sets.map { it.weightKg })
        assertEquals(session.startedAt.plusSeconds(200), sets.last().loggedAt)
        assertEquals(8.5, sets.last().rpe)
        assertTrue(sets.all { it.id > 0 })
    }

    @Test
    fun restartingTheSameWorkoutStartsAnEmptyLog() = runTest {
        repository.logSet(session, set("rdl", 0))
        val restarted = session.copy(startedAt = session.startedAt.plusSeconds(3_600))

        assertTrue(repository.observe(restarted).first().sets.isEmpty())
        assertEquals(1, repository.observe(session).first().sets.size)
    }

    @Test
    fun undoRemovesOnlyTheMostRecentSet() = runTest {
        repository.logSet(session, set("rdl", 0))
        repository.logSet(session, set("pull", 300))

        assertEquals("pull", repository.undoLastSet(session)?.exerciseId)
        assertEquals(listOf("rdl"), repository.observe(session).first().sets.map { it.exerciseId })
        assertEquals("rdl", repository.undoLastSet(session)?.exerciseId)
        assertNull(repository.undoLastSet(session))
    }

    @Test
    fun finishingExercisesIsRememberedOnce() = runTest {
        repository.finishExercises(session, listOf("a", "b"))
        repository.finishExercises(session, listOf("b"))

        assertEquals(setOf("a", "b"), repository.observe(session).first().finishedExerciseIds)
    }

    // ---- Rests and finished sessions ---------------------------------------------

    @Test
    fun aRestRunsFromItsSetUntilItEnds() = runTest {
        repository.logSet(session, set("rdl", 0))
        val first = repository.observe(session).first().sets.single()

        repository.recordRest(session, first.id, first.loggedAt.plusSeconds(95))
        // Only the first end counts.
        repository.recordRest(session, first.id, first.loggedAt.plusSeconds(300))

        assertEquals(mapOf(first.id to Duration.ofSeconds(95)), repository.observe(session).first().rests)
    }

    @Test
    fun undoingASetRemovesItsRest() = runTest {
        repository.logSet(session, set("rdl", 0))
        val first = repository.observe(session).first().sets.single()
        repository.recordRest(session, first.id, first.loggedAt.plusSeconds(95))

        repository.undoLastSet(session)
        // And a rest for a set that's gone isn't recorded.
        repository.recordRest(session, first.id, first.loggedAt.plusSeconds(100))

        assertTrue(repository.observe(session).first().rests.isEmpty())
    }

    @Test
    fun theLatestSessionOfAWorkoutWithItsFinishTime() = runTest {
        assertNull(repository.latestSessionOf(session.workoutId))

        repository.logSet(session, set("rdl", 0))
        val later = session.copy(startedAt = session.startedAt.plusSeconds(86_400))
        repository.logSet(later, set("rdl", 86_400))
        assertEquals(
            PastSession(later, repository.observe(later).first(), finishedAt = null),
            repository.latestSessionOf(session.workoutId),
        )

        repository.finishSession(later, later.startedAt.plusSeconds(3_600))
        assertEquals(Duration.ofHours(1), repository.latestSessionOf(session.workoutId)?.duration)
        assertNull(repository.latestSessionOf("another workout"))
    }
}
