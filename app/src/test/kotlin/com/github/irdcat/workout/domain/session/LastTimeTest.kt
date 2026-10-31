package com.github.irdcat.workout.domain.session

import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LastTimeTest {

    private val t1May =
        workout("W/0/T1", "T1", single(exercise("may-squat", "1", "Przysiad  ", group(result = "RPE 8"), group(result = "ok"))))
    private val t2May = workout("W/0/T2", "T2", single(exercise("may-rdl", "1", "RDL", group())))
    private val t1June = workout("W/2/T1", "T1", single(exercise("june-squat", "2", "przysiad", group())))
    private val plan = months(
        week("w-may", "2026-05-25", t1May, t2May),
        // Weeks without T1 are passed over.
        week("w-early-june", "2026-06-01", workout("W/1/L1", "L1")),
        week("w-rest", "2026-06-08"),
        week("w-june", "2026-06-15", t1June),
    )

    @Test
    fun theLatestEarlierWeekWithTheSameWorkout() {
        assertEquals("W/0/T1", plan.previousOccurrenceOf("W/2/T1")?.second?.id)
        assertNull(plan.previousOccurrenceOf("W/0/T1"))
        assertNull(plan.previousOccurrenceOf("unknown"))
    }

    @Test
    fun exercisesMatchByNameNotNumber() {
        val (week, workout) = plan.previousOccurrenceOf("W/2/T1")!!
        val lastTime = LastTime(week, workout, session = null)
        val june = t1June.exercises.single()

        assertEquals("may-squat", lastTime.exerciseLike(june)?.id)
        assertEquals("RPE 8 · ok", lastTime.resultLike(june))
        // The sheet has no sets: only sessions logged in the app do.
        assertEquals(emptyList<LoggedSet>(), lastTime.setsLike(june))
    }

    @Test
    fun setsLoggedThenLeaveOutWarmUps() {
        val (week, workout) = plan.previousOccurrenceOf("W/2/T1")!!
        val then = ActiveSession("W/0/T1", Instant.parse("2026-05-27T17:00:00Z"))
        fun set(warmUp: Boolean, kg: Double) = LoggedSet(1, "may-squat", "g", kg, 5, null, warmUp, then.startedAt)
        val log = SessionLog(listOf(set(true, 40.0), set(false, 90.0)))

        val lastTime = LastTime(week, workout, PastSession(then, log, finishedAt = null))

        assertEquals(listOf(90.0), lastTime.setsLike(t1June.exercises.single()).map { it.weightKg })
    }
}
