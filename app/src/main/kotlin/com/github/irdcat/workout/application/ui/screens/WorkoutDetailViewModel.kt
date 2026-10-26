package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.StartSessionResult
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Key of the workout ID in the navigation arguments. A type-safe route such as
 * `data class WorkoutDetail(val workoutId: String)` stores it under this name.
 */
const val WORKOUT_ID_ARG = "workoutId"

sealed interface WorkoutDetailNavigation {
    data object SessionStarted : WorkoutDetailNavigation
}

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    planRepository: WorkoutPlanRepository,
    private val sessionRepository: ActiveSessionRepository,
    logRepository: SessionLogRepository,
    private val clock: Clock,
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG]) {
        "WorkoutDetail needs a \"$WORKOUT_ID_ARG\" navigation argument"
    }

    val state: StateFlow<Loadable<WorkoutDetailUiState>> = combine(
        planRepository.observeWorkout(workoutId),
        planRepository.observeWeekOfWorkout(workoutId),
        activeWorkoutFlow(sessionRepository, planRepository, logRepository),
    ) { workout, week, active ->
        // Stays loading if the workout disappears, e.g. while the cache is re-synced.
        if (workout == null || week == null) Loadable.Loading else Loadable.Ready(detailState(workout, week, active))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    private val _navigation = Channel<WorkoutDetailNavigation>(Channel.BUFFERED)
    val navigation: Flow<WorkoutDetailNavigation> = _navigation.receiveAsFlow()

    fun onStartWorkout() {
        viewModelScope.launch {
            // Start is only offered while no other session is open.
            if (sessionRepository.start(workoutId) is StartSessionResult.Started) {
                _navigation.send(WorkoutDetailNavigation.SessionStarted)
            }
        }
    }

    private fun detailState(workout: Workout, week: TrainingWeek, active: ActiveWorkout?): WorkoutDetailUiState {
        val isActive = active?.workout?.id == workout.id
        val footer = when {
            isActive -> WorkoutDetailFooter.Resume
            workout.isCompleted -> WorkoutDetailFooter.Completed(on = null)
            else -> when (week.timing(LocalDate.now(clock))) {
                WeekTiming.Current ->
                    if (active == null) WorkoutDetailFooter.Start
                    else WorkoutDetailFooter.ViewOnly("Finish ${active.workout.code} to start")
                WeekTiming.Past -> WorkoutDetailFooter.ViewOnly("Past week · view only")
                WeekTiming.Upcoming -> WorkoutDetailFooter.ViewOnly("Upcoming · view only")
            }
        }
        val items = workout.toDetailItems()
        return WorkoutDetailUiState(
            weekRange = week.range,
            workoutName = workout.code,
            status = if (isActive) WorkoutStatus.InProgress else workout.uiStatus(),
            items = if (isActive) items.withSessionProgress(active) else items,
            footer = footer,
        )
    }

    /** Done, current ("set 4 of 5") and pending as the session sees them, not just the sheet. */
    private fun List<WorkoutItem>.withSessionProgress(active: ActiveWorkout): List<WorkoutItem> {
        val progress = active.progress
        val next = progress.next
        val exercises = active.workout.exercises.associateBy { it.id }
        fun ExerciseDetail.mark(): ExerciseDetail {
            val exercise = exercises[id] ?: return this
            return copy(
                progress = when {
                    next?.exercise?.id == id -> ExerciseProgress.Current(set = next.setNumber, setCount = next.plannedCount)
                    progress.toReview?.id == id -> ExerciseProgress.Current(review = true)
                    progress.isComplete(exercise) -> ExerciseProgress.Done
                    else -> ExerciseProgress.Pending
                },
            )
        }
        return map { item ->
            when (item) {
                is WorkoutItem.Single -> item.copy(exercise = item.exercise.mark())
                is WorkoutItem.Superset -> item.copy(exercises = item.exercises.map { it.mark() })
            }
        }
    }
}
