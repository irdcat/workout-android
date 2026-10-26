package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.domain.session.AbandonSession
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.StopwatchPauses
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.testing.FakeActiveSessionRepository
import com.github.irdcat.workout.testing.FakeResultRepository
import com.github.irdcat.workout.testing.FakeSessionLogRepository
import com.github.irdcat.workout.testing.FakeSetRestRepository
import com.github.irdcat.workout.testing.FakeSettingsRepository
import com.github.irdcat.workout.testing.FakeWorkoutPlanRepository
import com.github.irdcat.workout.testing.MainDispatcherRule
import com.github.irdcat.workout.testing.MutableClock
import com.github.irdcat.workout.testing.RecordingRestAlarm
import com.github.irdcat.workout.testing.exercise
import com.github.irdcat.workout.testing.fixedClock
import com.github.irdcat.workout.testing.group
import com.github.irdcat.workout.testing.months
import com.github.irdcat.workout.testing.single
import com.github.irdcat.workout.testing.superset
import com.github.irdcat.workout.testing.week
import com.github.irdcat.workout.testing.workout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val clock = fixedClock("2026-06-03")
    private val startedAt = clock.instant().minus(Duration.ofMinutes(12).plusSeconds(4))

    private val rdl = exercise(
        "rdl", "2", "RDL",
        group(reps = Reps.Count(6), load = Load.Weight(60.0)),
        group(reps = Reps.Count(6), load = Load.Weight(75.0)),
        group(sets = 2, reps = Reps.Count(5), load = Load.Weight(92.5), tempo = "3010"),
    )
    private val planned = workout(
        "W/1/T2", "T2",
        single(exercise("lever", "1", "Front Lever", group(result = "10 s"))),
        single(rdl),
        single(exercise("pull", "3", "Podciąganie", group(sets = 3))),
    )

    private val settings = FakeSettingsRepository()

    private lateinit var sessions: FakeActiveSessionRepository
    private val logs = FakeSessionLogRepository()
    private val results = FakeResultRepository()
    private val setRests = FakeSetRestRepository()

    /** [earlier] weeks come before this one, for "last time". */
    private fun TestScope.session(
        workout: Workout,
        active: Boolean = true,
        earlier: List<TrainingWeek> = emptyList(),
        clock: Clock = this@SessionViewModelTest.clock,
    ): SessionViewModel {
        val plan = FakeWorkoutPlanRepository(months(*earlier.toTypedArray(), week("w1", "2026-06-01", workout)))
        sessions = FakeActiveSessionRepository(clock, ActiveSession(workout.id, startedAt).takeIf { active })
        val viewModel = SessionViewModel(
            sessions, plan, logs, clock, settings, FindLastTime(plan, logs), StopwatchPauses(),
            AbandonSession(sessions, logs, results, RecordingRestAlarm()),
            setRests,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private fun SessionViewModel.ready(): SessionUiState = (state.value as Loadable.Ready).value

    @Test
    fun headerAndPlannedSets() = runTest(main.dispatcher) {
        val state = session(planned).ready()

        assertEquals("T2", state.workoutName)
        assertEquals("1 – 7 Jun", state.weekRange)
        assertEquals("12:04", state.elapsed)
        assertEquals(listOf(ProgressSegment.Done, ProgressSegment.Current, ProgressSegment.Pending), state.progress)

        val exercise = state.exercise as SessionExercise.Planned
        assertEquals(2, exercise.position)
        assertEquals(3, exercise.exerciseCount)
        assertEquals("RDL", exercise.name)
        assertEquals(Tempo("3010", appliesTo = "last group"), exercise.tempo)
        assertEquals(150, exercise.restSeconds)
        assertEquals(1, exercise.set)
        assertEquals(4, exercise.setCount)
        assertEquals("6 reps × 60 kg", exercise.target)
        assertEquals(
            listOf(
                SetRow("Set 2", "75 kg × 6", note = "after this"),
                SetRow("Set 3", "92.5 kg × 5"),
                SetRow("Set 4", "92.5 kg × 5"),
            ),
            exercise.upcoming,
        )

        assertEquals("60", state.entry!!.weight)
        assertEquals("6", state.entry!!.reps)
    }

    @Test
    fun entryEditsSurviveTheClockTicking() = runTest(main.dispatcher) {
        val viewModel = session(planned)

        viewModel.onEvent(SessionEvent.AdjustWeight(+1))
        assertEquals("62.5", viewModel.ready().entry!!.weight)
        viewModel.onEvent(SessionEvent.WeightStepSelected("5"))
        viewModel.onEvent(SessionEvent.AdjustWeight(-1))
        assertEquals("57.5", viewModel.ready().entry!!.weight)
        viewModel.onEvent(SessionEvent.WeightChanged("12,5kg"))
        assertEquals("12.5", viewModel.ready().entry!!.weight)
        viewModel.onEvent(SessionEvent.RepsChanged("1a2"))
        viewModel.onEvent(SessionEvent.AdjustReps(+1))
        viewModel.onEvent(SessionEvent.RpeChanged("8,5"))

        testScheduler.advanceTimeBy(3_000)
        testScheduler.runCurrent()

        val entry = viewModel.ready().entry!!
        assertEquals("12.5", entry.weight)
        assertEquals("13", entry.reps)
        assertEquals("8.5", entry.rpe)
    }

    @Test
    fun logSetStartsTheRestAndResultOpensTheEditor() = runTest(main.dispatcher) {
        val viewModel = session(planned)

        viewModel.onEvent(SessionEvent.AdjustWeight(+1))
        viewModel.onEvent(SessionEvent.LogSet)
        assertEquals(SessionNavigation.RestStarted, viewModel.navigation.first())
        // The entry moves on to the next set's target.
        assertEquals("75", viewModel.ready().entry!!.weight)

        viewModel.onEvent(SessionEvent.OpenResult)
        assertEquals(SessionNavigation.EditResult("rdl"), viewModel.navigation.first())
    }

    @Test
    fun unevenSuperset() = runTest(main.dispatcher) {
        val workout = workout(
            "W/1/T3", "T3",
            superset(
                1,
                exercise("a", "1a", "Biceps młotkowo", group(sets = 3, reps = Reps.Range(10, 12), load = Load.Weight(15.0), rest = 45)),
                exercise("b", "1b", "Reverse Flies", group(sets = 2, reps = Reps.Count(10), load = Load.Implements(2, 6.0), rest = 45)),
                exercise("c", "1c", "Wycieraczki", group(sets = 2, reps = Reps.Count(8), load = Load.Rpe(8.5), rest = 45)),
            ),
        )
        val state = session(workout).ready()
        val superset = state.exercise as SessionExercise.Superset

        assertEquals(1, superset.index)
        assertEquals(listOf(listOf("1a", "1b", "1c"), listOf("1a", "1b", "1c"), listOf("1a")), superset.rounds.map { round -> round.map { it.tag } })
        assertEquals(ProgressSegment.Current, superset.rounds[0][0].status)
        assertEquals(SupersetMember("1a", "Biceps młotkowo", round = 1, target = "10–12 reps × 15 kg", restSeconds = 45), superset.current)
        assertEquals(SetRow("R1", "6 kg × 10", tag = "1b"), superset.next)
        assertEquals("15", state.entry!!.weight)
    }

    @Test
    fun openSets() = runTest(main.dispatcher) {
        val workout = workout(
            "W/1/T3", "T3",
            single(exercise("pyramid", "1", "Przysiad piramidka", group(sets = null, load = Load.Weight(40.0), rest = null, tempo = "2010"))),
        )
        val exercise = session(workout).ready().exercise as SessionExercise.OpenSets

        assertEquals("5 reps", exercise.repsPerSet)
        assertEquals("40 kg", exercise.startWeight)
        assertEquals(Tempo("2010"), exercise.tempo)
        assertNull(exercise.restSeconds)
    }

    // ---- Unlimited sets ----------------------------------------------------------

    private val stretch = exercise(
        "stretch", "1", "Rozciąganie",
        group(sets = null, reps = Reps.Count(10), load = Load.Bodyweight, rest = null).copy(sets = SetCount.Unlimited),
    )
    private val unlimited = workout("W/1/T4", "T4", single(stretch))

    @Test
    fun unlimitedSetsHaveNoTimeLimitAndPickTheirRest() = runTest(main.dispatcher) {
        val viewModel = session(unlimited)
        val state = viewModel.ready()
        val exercise = state.exercise as SessionExercise.OpenSets

        assertTrue(exercise.unlimited)
        assertEquals(1, exercise.set)
        // No rest in the sheet: it starts from the default.
        assertEquals("1:30", state.entry!!.rest)

        viewModel.onEvent(SessionEvent.AdjustRest(-1))
        assertEquals("1:15", viewModel.ready().entry!!.rest)
        viewModel.onEvent(SessionEvent.LogSet)

        assertEquals(SessionNavigation.RestStarted, viewModel.navigation.first())
        assertEquals(75, setRests.rests.value["rozciąganie"])
        // Still going: unlimited sets end only with "Finish exercise", and no stopwatch runs.
        val next = viewModel.ready()
        assertEquals(2, (next.exercise as SessionExercise.OpenSets).set)
        assertNull((next.exercise as SessionExercise.OpenSets).stopwatch)
        assertEquals("1:15", next.entry!!.rest)
    }

    @Test
    fun anUnlimitedSetStartsFromTheSheetsRestUntilOneIsPicked() = runTest(main.dispatcher) {
        val withRest = exercise("stretch", "1", "Rozciąganie", stretch.setGroups.single().copy(restSeconds = 45))
        val viewModel = session(workout("W/1/T4", "T4", single(withRest)))
        assertEquals("0:45", viewModel.ready().entry!!.rest)

        setRests.save("Rozciąganie", 120)
        assertEquals("2:00", viewModel.ready().entry!!.rest)
    }

    @Test
    fun noRestPickedMeansNoRestTimer() = runTest(main.dispatcher) {
        val viewModel = session(unlimited)
        viewModel.onEvent(SessionEvent.RestChanged("0"))
        viewModel.onEvent(SessionEvent.LogSet)
        testScheduler.runCurrent()

        assertEquals(1, logs.observe(sessions.session.value!!).first().sets.size)
        assertEquals(0, setRests.rests.value["rozciąganie"])
    }

    @Test
    fun plannedSetsDontPickTheirRest() = runTest(main.dispatcher) {
        assertNull(session(planned).ready().entry!!.rest)
    }

    @Test
    fun leavesWhenThereIsNoSession() = runTest(main.dispatcher) {
        val viewModel = session(planned, active = false)

        assertEquals(Loadable.Loading, viewModel.state.value)
        assertEquals(SessionNavigation.SessionEnded, viewModel.navigation.first())
    }

    @Test
    fun leavesWhenTheSessionEndsElsewhere() = runTest(main.dispatcher) {
        val viewModel = session(planned)
        assertEquals("T2", viewModel.ready().workoutName)

        sessions.end()

        assertEquals(SessionNavigation.SessionEnded, viewModel.navigation.first())
    }

    @Test
    fun abandoningDeletesTheLogAndLeaves() = runTest(main.dispatcher) {
        val viewModel = session(planned)
        val session = sessions.session.value!!
        logs.logSet(session, LoggedSet(exerciseId = rdl.id, setGroupId = rdl.setGroups[0].id, weightKg = 60.0, reps = 6, rpe = null, warmUp = false, loggedAt = startedAt))

        viewModel.onEvent(SessionEvent.AbandonWorkout)

        assertEquals(SessionNavigation.SessionEnded, viewModel.navigation.first())
        assertNull(sessions.session.value)
        assertEquals(emptyList<LoggedSet>(), logs.observe(session).first().sets)
    }

    @Test
    fun followsTheSettings() = runTest(main.dispatcher) {
        settings.state.value = AppSettings(
            defaultWeightStepKg = 5.0,
            measureWorkoutTime = false,
            autoStartRestTimer = false,
        )
        val viewModel = session(planned)

        assertEquals("5", viewModel.ready().entry!!.weightStep)
        assertNull(viewModel.ready().elapsed)

        viewModel.onEvent(SessionEvent.LogSet)
        viewModel.onEvent(SessionEvent.OpenResult)
        // No rest timer: the next event is the editor, not RestStarted.
        assertEquals(SessionNavigation.EditResult("rdl"), viewModel.navigation.first())
    }

    @Test
    fun loggingMovesThroughTheSets() = runTest(main.dispatcher) {
        val viewModel = session(planned)

        viewModel.onEvent(SessionEvent.RpeChanged("8"))
        viewModel.onEvent(SessionEvent.LogSet)

        val logged = logs.logs.value.values.single().sets.single()
        assertEquals("rdl", logged.exerciseId)
        assertEquals(60.0, logged.weightKg)
        assertEquals(6, logged.reps)
        assertEquals(8.0, logged.rpe)

        val exercise = viewModel.ready().exercise as SessionExercise.Planned
        assertEquals(listOf(SetRow("Set 1", "60 kg × 6", note = "RPE 8")), exercise.done)
        assertEquals(2, exercise.set)
        assertEquals("6 reps × 75 kg", exercise.target)
        assertEquals(listOf("Set 3", "Set 4"), exercise.upcoming.map { it.label })
        // A new set starts from the default RPE again, not the last one entered.
        assertEquals("8", viewModel.ready().entry!!.rpe)
    }

    @Test
    fun finishExerciseMovesOn() = runTest(main.dispatcher) {
        val viewModel = session(planned)

        viewModel.onEvent(SessionEvent.FinishExercise)

        val state = viewModel.ready()
        assertEquals("Podciąganie", (state.exercise as SessionExercise.Planned).name)
        assertEquals(listOf(ProgressSegment.Done, ProgressSegment.Done, ProgressSegment.Current), state.progress)
    }

    @Test
    fun loggingASupersetMemberMovesToTheNext() = runTest(main.dispatcher) {
        val workout = workout(
            "W/1/T3", "T3",
            superset(
                1,
                exercise("a", "1a", "Biceps", group(sets = 2, load = Load.Weight(15.0), rest = 45)),
                exercise("b", "1b", "Reverse Flies", group(sets = 2, load = Load.Weight(6.0), rest = 45)),
            ),
        )
        val viewModel = session(workout)

        viewModel.onEvent(SessionEvent.LogSet)

        val superset = viewModel.ready().exercise as SessionExercise.Superset
        assertEquals("1b", superset.current.tag)
        assertEquals(listOf(SetRow("R1", "15 kg × 5", note = "RPE 8", tag = "1a")), superset.done)
        assertEquals(
            listOf(ProgressSegment.Done, ProgressSegment.Current),
            superset.rounds.first().map { it.status },
        )
        assertEquals(SetRow("R2", "15 kg × 5", tag = "1a"), superset.next)
    }

    // ---- Last time -----------------------------------------------------------

    /** T2 the week before, trained in the app on 27 May with 62.5 kg × 6, result "RPE 8" in the sheet. */
    private val rdlBefore = exercise("rdl-before", "2", "RDL", group(load = Load.Weight(60.0), result = "RPE 8"))
    private val weekBefore = week("w0", "2026-05-25", workout("W/0/T2", "T2", single(rdlBefore)))
    private val sessionBefore = ActiveSession("W/0/T2", Instant.parse("2026-05-27T17:00:00Z"))

    private suspend fun logBefore(weight: Double?) = logs.logSet(
        sessionBefore,
        LoggedSet(exerciseId = "rdl-before", setGroupId = rdlBefore.setGroups[0].id, weightKg = weight, reps = 6, rpe = null, warmUp = false, loggedAt = sessionBefore.startedAt),
    )

    @Test
    fun lastTimeShowsWhatTheAppLoggedThenWithTheSheetsResult() = runTest(main.dispatcher) {
        logBefore(62.5)

        val lastTime = session(planned, earlier = listOf(weekBefore)).ready().entry!!.lastTime

        assertEquals(LastTime(date = "27 May", summary = "62.5 kg × 6", note = "RPE 8"), lastTime)
    }

    @Test
    fun withoutAnAppSessionOnlyTheSheetsResultIsShown() = runTest(main.dispatcher) {
        // The sheet keeps no dates or sets, so they're left out rather than guessed.
        val lastTime = session(planned, earlier = listOf(weekBefore)).ready().entry!!.lastTime

        assertEquals(LastTime(date = null, summary = null, note = "RPE 8"), lastTime)
    }

    @Test
    fun nothingEarlierMeansNoLastTime() = runTest(main.dispatcher) {
        assertNull(session(planned).ready().entry!!.lastTime)
    }

    @Test
    fun anRpeTargetTakesLastTimesWeight() = runTest(main.dispatcher) {
        logBefore(100.0)
        val byRpe = workout("W/1/T2", "T2", single(exercise("rdl", "2", "RDL", group(load = Load.Rpe(8.0)))))

        assertEquals("100", session(byRpe, earlier = listOf(weekBefore)).ready().entry!!.weight)

        settings.update { it.copy(prefillWeightFromLastTime = false) }
        assertEquals("0", session(byRpe, earlier = listOf(weekBefore)).ready().entry!!.weight)
    }

    // ---- Open-sets stopwatch ---------------------------------------------------

    @Test
    fun theStopwatchPausesAndShowsTheSheetsTimeLimit() = runTest(main.dispatcher) {
        val clock = MutableClock(this@SessionViewModelTest.clock.instant())
        val pyramid = exercise("pyramid", "1", "Przysiad piramidka - Czas: 10:00 od pierwszej serii", group(sets = null, load = Load.Weight(40.0), rest = null))
        val viewModel = session(workout("W/1/T3", "T3", single(pyramid)), clock = clock)
        fun passSeconds(seconds: Long) {
            clock.advance(seconds)
            testScheduler.advanceTimeBy(seconds * 1_000)
            testScheduler.runCurrent()
        }
        fun stopwatch() = (viewModel.ready().exercise as SessionExercise.OpenSets).stopwatch

        assertNull(stopwatch())
        viewModel.onEvent(SessionEvent.LogSet)
        passSeconds(30)
        assertEquals(Stopwatch(elapsed = "0:30", limit = "10:00", running = true), stopwatch())

        viewModel.onEvent(SessionEvent.ToggleStopwatch)
        passSeconds(20)
        assertEquals(Stopwatch(elapsed = "0:30", limit = "10:00", running = false), stopwatch())

        viewModel.onEvent(SessionEvent.ToggleStopwatch)
        passSeconds(10)
        assertEquals("0:40", stopwatch()?.elapsed)
        viewModel.viewModelScope.cancel()
    }

    private suspend fun logAll(session: ActiveSession, exercise: Exercise) {
        exercise.setGroups.forEach { group ->
            repeat((group.sets as SetCount.Fixed).count) {
                logs.logSet(session, LoggedSet(exerciseId = exercise.id, setGroupId = group.id, weightKg = 60.0, reps = 6, rpe = null, warmUp = false, loggedAt = startedAt))
            }
        }
    }

    @Test
    fun onceEveryExerciseIsFinishedThereIsNothingMoreToLog() = runTest(main.dispatcher) {
        val session = ActiveSession(planned.id, startedAt)
        logAll(session, rdl)
        logAll(session, planned.exercises.last())
        logs.finishExercises(session, listOf("rdl", "pull"))
        val viewModel = session(planned)

        val state = viewModel.ready()
        assertEquals(SessionExercise.Complete, state.exercise)
        assertNull(state.entry)
        assertEquals(List(3) { ProgressSegment.Done }, state.progress)

        // No "another set" of the last exercise.
        viewModel.onEvent(SessionEvent.LogSet)
        testScheduler.runCurrent()
        assertEquals(7, logs.logs.value.getValue(session).sets.size)
    }

    @Test
    fun theLastSetOpensTheResultForReviewInsteadOfARest() = runTest(main.dispatcher) {
        val session = ActiveSession(planned.id, startedAt)
        rdl.setGroups.take(2).forEach { group ->
            logs.logSet(session, LoggedSet(exerciseId = "rdl", setGroupId = group.id, weightKg = 60.0, reps = 6, rpe = null, warmUp = false, loggedAt = startedAt))
        }
        logs.logSet(session, LoggedSet(exerciseId = "rdl", setGroupId = rdl.setGroups[2].id, weightKg = 92.5, reps = 5, rpe = null, warmUp = false, loggedAt = startedAt))
        val viewModel = session(planned)

        viewModel.onEvent(SessionEvent.LogSet)

        assertEquals(SessionNavigation.ReviewResult("rdl"), viewModel.navigation.first())
        val review = viewModel.ready().exercise as SessionExercise.Review
        assertEquals("RDL", review.name)
        assertEquals(4, review.done.size)
        assertEquals(listOf(ProgressSegment.Done, ProgressSegment.Current, ProgressSegment.Pending), viewModel.ready().progress)

        // Dismissed: it can be opened again.
        viewModel.onEvent(SessionEvent.ReviewResult)
        assertEquals(SessionNavigation.ReviewResult("rdl"), viewModel.navigation.first())

        // Saving the review (in the editor) finishes the exercise: on to the next one.
        logs.finishExercises(session, listOf("rdl"))
        assertEquals("Podciąganie", (viewModel.ready().exercise as SessionExercise.Planned).name)
    }

    @Test
    fun finishingEarlyReviewsTheResultFirst() = runTest(main.dispatcher) {
        val session = ActiveSession(planned.id, startedAt)
        logs.logSet(session, LoggedSet(exerciseId = "rdl", setGroupId = rdl.setGroups[0].id, weightKg = 60.0, reps = 6, rpe = null, warmUp = false, loggedAt = startedAt))
        val viewModel = session(planned)

        viewModel.onEvent(SessionEvent.FinishExercise)

        assertEquals(SessionNavigation.ReviewResult("rdl"), viewModel.navigation.first())
        // Not finished until the review is saved.
        assertEquals("RDL", (viewModel.ready().exercise as SessionExercise.Planned).name)
    }

    @Test
    fun finishingASupersetEarlyReviewsEachMemberWithSets() = runTest(main.dispatcher) {
        val workout = workout(
            "W/1/T3", "T3",
            superset(
                1,
                exercise("a", "1a", "Biceps", group(sets = 2, load = Load.Weight(15.0), rest = 45)),
                exercise("b", "1b", "Reverse Flies", group(sets = 2, load = Load.Weight(6.0), rest = 45)),
                exercise("c", "1c", "Wycieraczki", group(sets = 2, load = Load.Rpe(8.5), rest = 45)),
            ),
        )
        val viewModel = session(workout)
        viewModel.onEvent(SessionEvent.LogSet)
        viewModel.onEvent(SessionEvent.LogSet)
        assertEquals(SessionNavigation.RestStarted, viewModel.navigation.first())
        assertEquals(SessionNavigation.RestStarted, viewModel.navigation.first())

        viewModel.onEvent(SessionEvent.FinishExercise)

        // c has no sets: nothing to review, it's finished at once.
        assertEquals(SessionNavigation.ReviewResult("a"), viewModel.navigation.first())
        assertEquals(setOf("c"), logs.logs.value.values.single().finishedExerciseIds)
        viewModel.onResultReviewed("a")
        assertEquals(SessionNavigation.ReviewResult("b"), viewModel.navigation.first())
    }

    @Test
    fun warmUpsAreShownButDontMoveTheSetOn() = runTest(main.dispatcher) {
        val session = ActiveSession(planned.id, startedAt)
        repeat(2) {
            logs.logSet(session, LoggedSet(exerciseId = "rdl", setGroupId = rdl.setGroups[0].id, weightKg = 20.0, reps = 10, rpe = null, warmUp = true, loggedAt = startedAt))
        }

        val state = session(planned).ready()

        assertEquals(2, state.warmUpSets)
        val exercise = state.exercise as SessionExercise.Planned
        assertEquals(1, exercise.set)
        assertTrue(exercise.done.isEmpty())
    }

    @Test
    fun repsArePrefilledFromEveryKindOfTarget() = runTest(main.dispatcher) {
        fun prefilled(reps: Reps): String {
            val workout = workout("W/1/R", "R", single(exercise("x", "1", "X", group(reps = reps))))
            val session = SessionViewModel(
                FakeActiveSessionRepository(clock, ActiveSession(workout.id, startedAt)),
                FakeWorkoutPlanRepository(months(week("w1", "2026-06-01", workout))),
                logs, clock, settings, FindLastTime(FakeWorkoutPlanRepository(months()), logs), StopwatchPauses(),
                AbandonSession(FakeActiveSessionRepository(clock), logs, results, RecordingRestAlarm()),
                setRests,
            )
            backgroundScope.launch { session.state.collect {} }
            testScheduler.runCurrent()
            return session.ready().entry!!.reps
        }

        assertEquals("8", prefilled(Reps.UpTo(8)))
        assertEquals("10", prefilled(Reps.PerSide(10)))
        assertEquals("20", prefilled(Reps.Steps(20)))
        assertEquals("4", prefilled(Reps.Lengths(4)))
        assertEquals("30", prefilled(Reps.Duration(30, 45)))
        assertEquals("", prefilled(Reps.Other("max")))
    }

    @Test
    fun withoutAWeightTheEntryStartsAtZeroAndLogsBodyweight() = runTest(main.dispatcher) {
        val workout = workout("W/1/BW", "BW", single(exercise("dips", "1", "Dips", group(sets = 3, reps = Reps.Count(8), load = Load.Bodyweight))))
        val viewModel = session(workout)
        assertEquals("0", viewModel.ready().entry!!.weight)

        viewModel.onEvent(SessionEvent.LogSet)
        viewModel.onEvent(SessionEvent.WeightChanged(""))
        viewModel.onEvent(SessionEvent.LogSet)
        testScheduler.runCurrent()

        val sets = logs.logs.value.values.single().sets
        assertEquals(listOf(null, null), sets.map { it.weightKg })
        assertEquals(listOf("8 reps", "8 reps"), (viewModel.ready().exercise as SessionExercise.Planned).done.map { it.value })
    }

    @Test
    fun aWeightAddedToABodyweightExerciseIsKept() = runTest(main.dispatcher) {
        val workout = workout("W/1/BW", "BW", single(exercise("dips", "1", "Dips", group(sets = 3, reps = Reps.Count(8), load = Load.Bodyweight))))
        val viewModel = session(workout)

        viewModel.onEvent(SessionEvent.AdjustWeight(+1))
        viewModel.onEvent(SessionEvent.LogSet)
        testScheduler.runCurrent()

        assertEquals(2.5, logs.logs.value.values.single().sets.single().weightKg)
    }

    @Test
    fun rpeStartsAtEightAndStepsWithinOneToTen() = runTest(main.dispatcher) {
        val viewModel = session(planned)
        fun rpe() = viewModel.ready().entry!!.rpe

        assertEquals("8", rpe())
        viewModel.onEvent(SessionEvent.AdjustRpe(+1))
        assertEquals("8.5", rpe())
        // An emptied field starts from 8 again.
        viewModel.onEvent(SessionEvent.RpeChanged(""))
        viewModel.onEvent(SessionEvent.AdjustRpe(+1))
        assertEquals("8", rpe())
        viewModel.onEvent(SessionEvent.AdjustRpe(+1))
        assertEquals("8.5", rpe())
        viewModel.onEvent(SessionEvent.RpeStepSelected("1"))
        repeat(3) { viewModel.onEvent(SessionEvent.AdjustRpe(+1)) }
        assertEquals("10", rpe())
        viewModel.onEvent(SessionEvent.RpeChanged("1.5"))
        viewModel.onEvent(SessionEvent.AdjustRpe(-1))
        assertEquals("1", rpe())
        assertEquals("1", viewModel.ready().entry!!.rpeStep)

        viewModel.onEvent(SessionEvent.RpeChanged(""))
        viewModel.onEvent(SessionEvent.LogSet)
        testScheduler.runCurrent()
        assertNull(logs.logs.value.values.single().sets.single().rpe)
    }

    @Test
    fun rpeTargetsAreNotedInTheNextSets() = runTest(main.dispatcher) {
        val workout = workout(
            "W/1/R", "R",
            single(exercise("z", "1", "Żurawie", group(sets = 3, reps = Reps.Count(8), load = Load.Rpe(8.5)))),
        )
        val exercise = session(workout).ready().exercise as SessionExercise.Planned

        assertEquals(
            listOf(SetRow("Set 2", "8 reps", note = "RPE 8.5"), SetRow("Set 3", "8 reps", note = "RPE 8.5")),
            exercise.upcoming,
        )
    }

    @Test
    fun aHoldIsLoggedInSeconds() = runTest(main.dispatcher) {
        val plank = exercise("plank", "1", "Deska", group(sets = 3, reps = Reps.Duration(30, 45), load = Load.Bodyweight))
        val viewModel = session(workout("W/1/P", "P", single(plank)))

        val entry = viewModel.ready().entry!!
        assertEquals("Seconds", entry.repsLabel)
        assertEquals("30", entry.reps)
        viewModel.onEvent(SessionEvent.AdjustReps(+1))
        assertEquals("35", viewModel.ready().entry!!.reps)

        viewModel.onEvent(SessionEvent.LogSet)
        testScheduler.runCurrent()

        assertEquals(35, logs.logs.value.values.single().sets.single().reps)
        val exercise = viewModel.ready().exercise as SessionExercise.Planned
        assertEquals(SetRow("Set 1", "35 s", note = "RPE 8"), exercise.done.single())
        assertEquals("30–45 s", exercise.target)
    }

    @Test
    fun lengthsAndStepsNameTheField() = runTest(main.dispatcher) {
        val swim = exercise("swim", "1", "Pływanie", group(sets = 2, reps = Reps.Lengths(4), load = Load.Bodyweight))
        assertEquals("Lengths", session(workout("W/1/S", "S", single(swim))).ready().entry!!.repsLabel)
    }

    @Test
    fun anRpeTargetFromTheSheetIsTheStartingRpe() = runTest(main.dispatcher) {
        val workout = workout(
            "W/1/R", "R",
            single(exercise("z", "1", "Żurawie", group(sets = 3, reps = Reps.Count(8), load = Load.Rpe(8.5)))),
        )
        assertEquals("8.5", session(workout).ready().entry!!.rpe)
    }
}
