package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.results.resultTarget
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LastTime
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.domain.workout.WorkoutItem
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.infrastructure.coroutines.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

sealed interface WorkoutSummaryNavigation {
    data object Saved : WorkoutSummaryNavigation
}

private val SummaryDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    private val sessionRepository: ActiveSessionRepository,
    private val planRepository: WorkoutPlanRepository,
    private val logRepository: SessionLogRepository,
    private val clock: Clock,
    private val settingsRepository: SettingsRepository,
    findLastTime: FindLastTime,
    private val resultRepository: ResultRepository,
    private val syncWorkoutPlan: SyncWorkoutPlan,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    /** When the summary opened; the session's duration counts up to here. */
    private val endedAt: Instant = clock.instant()

    private val lastTime: Flow<LastTime?> = sessionRepository.activeSession
        .map { it?.workoutId }
        .distinctUntilChanged()
        .mapLatest { workoutId -> workoutId?.let { findLastTime(it) } }

    // Results come from the plan, so edits made in the result editor show up here.
    val state: StateFlow<Loadable<WorkoutSummaryUiState>> = combine(
        activeWorkoutFlow(sessionRepository, planRepository, logRepository),
        settingsRepository.settings,
        lastTime,
    ) { active, settings, lastTime ->
        active?.let { Loadable.Ready(summaryState(it, settings, lastTime)) } ?: Loadable.Loading
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    private val _navigation = Channel<WorkoutSummaryNavigation>(Channel.BUFFERED)
    val navigation: Flow<WorkoutSummaryNavigation> = _navigation.receiveAsFlow()

    /**
     * Saves a result built from the logged sets for every exercise that has none, ends
     * the session, then writes to the sheet in the background (offline, the next sync does).
     */
    fun onSave() {
        viewModelScope.launch {
            val active = activeWorkoutFlow(sessionRepository, planRepository, logRepository).first()
            if (active != null) {
                for (exercise in active.workout.exercises) {
                    if (exercise.isDone) continue
                    val text = loggedResult(active, exercise) ?: continue
                    // The last row: the working or back-off sets, not the ramp-up.
                    val target = active.workout.resultTarget(exercise, exercise.setGroups.last())
                    resultRepository.save(SavedResult(target, text, clock.instant()))
                }
                logRepository.finishSession(active.session, endedAt)
            }
            sessionRepository.end()
            if (settingsRepository.getSettings().writeResultsToSheet) applicationScope.launch { syncWorkoutPlan() }
            _navigation.send(WorkoutSummaryNavigation.Saved)
        }
    }

    private fun summaryState(active: ActiveWorkout, settings: AppSettings, lastTime: LastTime?): WorkoutSummaryUiState {
        val zone = clock.zone
        val start = active.session.startedAt.atZone(zone)
        val workout = active.workout
        val exercises = workout.exercises
        val results = workout.items.mapIndexed { position, item ->
            item.exercises.mapIndexed { member, exercise ->
                val tag = if (item is WorkoutItem.Superset) "${position + 1}${'a' + member} " else ""
                val setsTotal = exercise.setCount ?: exercise.setGroups.size
                SummaryResult(
                    exerciseId = exercise.id,
                    label = tag + exercise.name,
                    result = exercise.setGroups.mapNotNull { it.result }.joinToString(" · ").ifEmpty { null }
                        ?: loggedResult(active, exercise),
                    setsDone = setsDone(active, exercise, setsTotal),
                    setsTotal = setsTotal,
                )
            }
        }.flatten()

        val total = Duration.between(active.session.startedAt, endedAt).coerceAtLeast(Duration.ZERO)
        // Only rests ended in the rest timer are known; the rest of the session counts as working.
        // With none timed the split is unknown, and zeros hide it.
        val timed = active.log.rests.isNotEmpty()
        val resting = active.log.restingTime.coerceAtMost(total)
        return WorkoutSummaryUiState(
            workoutName = workout.code,
            dateLine = "${start.format(SummaryDate)} · ${formatTime(active.session.startedAt, zone)} – ${formatTime(endedAt, zone)}",
            totalSeconds = total.seconds.toInt().takeIf { settings.measureWorkoutTime },
            workingSeconds = if (timed) (total - resting).seconds.toInt() else 0,
            restingSeconds = if (timed) resting.seconds.toInt() else 0,
            // Known only when last time was trained and finished in the app.
            previousSeconds = lastTime?.session?.duration?.seconds?.toInt(),
            avgRestSeconds = active.log.averageRest?.seconds?.toInt(),
            exercisesDone = exercises.count { it.isDone || active.progress.loggedSets(it).isNotEmpty() },
            exercisesTotal = exercises.size,
            setsDone = results.sumOf { it.setsDone },
            setsTotal = results.sumOf { it.setsTotal },
            results = results,
            sheetName = active.week.sheetName,
            writesToSheet = settings.writeResultsToSheet,
        )
    }

    /**
     * The exercise's logged working sets as a result, e.g. "60×6, 75×6, 90×5, RPE 9", with
     * the last RPE logged unless it's the last row's target; null when none.
     */
    private fun loggedResult(active: ActiveWorkout, exercise: Exercise): String? {
        val sets = active.progress.loggedSets(exercise).ifEmpty { return null }
        return resultText(
            text = sets.joinToString(", ") { it.chipLabel(exercise.repsUnitOf(it.setGroupId)) },
            rpe = sets.lastOrNull { it.rpe != null }?.rpe?.kg().orEmpty(),
            target = exercise.setGroups.last().rpeTarget(),
        )
    }

    /**
     * Sets logged in this session; for an exercise not logged in the app, all of
     * them when the sheet already has its result.
     */
    private fun setsDone(active: ActiveWorkout, exercise: Exercise, setsTotal: Int): Int {
        val logged = active.progress.loggedSets(exercise).size
        return when {
            logged > 0 -> logged
            exercise.isDone -> setsTotal
            else -> 0
        }
    }
}

private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

private fun Duration.coerceAtMost(max: Duration): Duration = if (this > max) max else this
