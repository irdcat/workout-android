package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.AbandonSession
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.SessionProgress
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.StopwatchPauses
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import com.github.irdcat.workout.domain.session.LastTime as LastTimeData

sealed interface SessionNavigation {
    /** A set was logged and has a rest time. */
    data object RestStarted : SessionNavigation

    /** Open the result editor for an exercise. */
    data class EditResult(val exerciseId: String) : SessionNavigation

    /** Review an exercise's result; saving it finishes the exercise. */
    data class ReviewResult(val exerciseId: String) : SessionNavigation

    /** No session is open any more (ended elsewhere, or none to begin with); leave the screen. */
    data object SessionEnded : SessionNavigation
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: ActiveSessionRepository,
    planRepository: WorkoutPlanRepository,
    private val logRepository: SessionLogRepository,
    private val clock: Clock,
    settingsRepository: SettingsRepository,
    findLastTime: FindLastTime,
    private val stopwatchPauses: StopwatchPauses,
    private val abandonSession: AbandonSession,
    private val setRests: SetRestRepository,
) : ViewModel() {

    /** The user's edits to the entry, kept per set so the ticking clock doesn't reset them. */
    private val entryEdits = MutableStateFlow<Pair<String, SetEntry>?>(null)

    /** Exercises ended with "Finish exercise" whose results are still to be reviewed, in order. */
    private var reviewQueue: List<String> = emptyList()

    /** The previous time this workout was on the plan; looked up once per session. */
    private val lastTime: Flow<LastTimeData?> = sessionRepository.activeSession
        .map { it?.workoutId }
        .distinctUntilChanged()
        .mapLatest { workoutId -> workoutId?.let { findLastTime(it) } }

    private val activeWorkout = activeWorkoutFlow(sessionRepository, planRepository, logRepository)

    /** The rest last picked for the exercise up next, when its rest is the user's to pick. */
    private val savedSetRest: Flow<Int?> = activeWorkout
        .map { active -> active?.progress?.next?.exercise?.takeIf { it.picksRest }?.name }
        .distinctUntilChanged()
        .flatMapLatest { name -> name?.let(setRests::observe) ?: flowOf(null) }

    private val view: StateFlow<SessionView?> = combine(
        activeWorkout.flatMapLatest { active ->
            if (active == null) flowOf(null) else elapsedSince(active.session.startedAt, clock).map { active to it }
        },
        entryEdits,
        settingsRepository.settings,
        combine(lastTime, savedSetRest) { lastTime, savedRest -> lastTime to savedRest },
        stopwatchPauses.pauses,
    ) { activeWithClock, edits, settings, (lastTime, savedRest), pauses ->
        activeWithClock?.let { (active, elapsed) ->
            sessionView(active, elapsed, edits, settings, lastTime, savedRest, pauses, clock)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<Loadable<SessionUiState>> = view
        .map { it?.let { view -> Loadable.Ready(view.ui) } ?: Loadable.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    private val _navigation = Channel<SessionNavigation>(Channel.BUFFERED)
    val navigation: Flow<SessionNavigation> = _navigation.receiveAsFlow()

    init {
        // Watch the session itself, not its workout: the workout can be briefly missing while the
        // cache reloads, but a session that ended (on another screen, or by a sync finding its
        // workout gone) won't come back, so the screen has nothing left to show.
        viewModelScope.launch {
            sessionRepository.activeSession.first { it == null }
            _navigation.send(SessionNavigation.SessionEnded)
        }
        viewModelScope.launch {
            // Every set of an exercise logged: its result comes up for review straight away.
            // Watches the plan and log only, not the ticking screen state.
            activeWorkout.map { it?.progress?.toReview?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { _navigation.send(SessionNavigation.ReviewResult(it)) }
        }
    }

    /** A reviewed result was saved, which finished its exercise; on to the next one ended early, if any. */
    fun onResultReviewed(exerciseId: String) {
        reviewQueue = reviewQueue - exerciseId
        reviewQueue.firstOrNull()?.let { viewModelScope.launch { _navigation.send(SessionNavigation.ReviewResult(it)) } }
    }

    /** Handles every [SessionEvent] except navigation, which the route handles. */
    fun onEvent(event: SessionEvent) {
        entryEdit(event)?.let { edit ->
            updateEntry(edit)
            return
        }
        when (event) {
            SessionEvent.ToggleStopwatch -> view.value?.let { current ->
                // Only once it runs, from the first logged set.
                val exercise = current.next?.exercise
                if (exercise != null && (current.ui.exercise as? SessionExercise.OpenSets)?.stopwatch != null) {
                    stopwatchPauses.toggle(current.active.session, exercise.id, clock.instant())
                }
            }
            SessionEvent.LogSet -> logSet()
            SessionEvent.OpenResult -> view.value?.next?.let { next -> navigate(SessionNavigation.EditResult(next.exercise.id)) }
            SessionEvent.ReviewResult -> (view.value?.ui?.exercise as? SessionExercise.Review)?.let { review ->
                navigate(SessionNavigation.ReviewResult(review.exerciseId))
            }
            SessionEvent.FinishExercise -> finishExercise()
            // Ending the session sends SessionEnded, which leaves the screen.
            SessionEvent.AbandonWorkout -> viewModelScope.launch { abandonSession() }
            // Entry edits are handled above; navigation by the route.
            else -> Unit
        }
    }

    private fun navigate(to: SessionNavigation) {
        viewModelScope.launch { _navigation.send(to) }
    }

    private fun logSet() {
        val current = view.value ?: return
        val next = current.next ?: return
        val entry = current.ui.entry ?: return
        val weight = entry.weight.toWeightKg() ?: return
        val reps = entry.reps.toIntOrNull() ?: return
        // Only exercises without a rest in the sheet (or unlimited sets) pick it; null keeps the sheet's.
        val chosenRest = entry.rest?.let { parseRestTime(it) ?: return }
        val set = LoggedSet(
            exerciseId = next.exercise.id,
            setGroupId = next.group.id,
            weightKg = weight.takeIf { it > 0 },
            reps = reps,
            rpe = entry.rpe.toDoubleOrNull(),
            warmUp = false,
            loggedAt = clock.instant(),
        )
        // No rest after the exercise's last set: its result is reviewed next.
        val active = current.active
        val after = SessionProgress(active.workout, active.log.copy(sets = active.log.sets + set))
        val hasRest = chosenRest?.let { it > 0 } ?: (next.group.restSeconds != null)
        val rests = hasRest && after.next?.itemIndex == next.itemIndex
        viewModelScope.launch {
            // The rest timer reads it from here; it's also where the next set's rest starts from.
            chosenRest?.let { setRests.save(next.exercise.name, it) }
            logRepository.logSet(active.session, set)
            if (rests && current.autoStartRestTimer) _navigation.send(SessionNavigation.RestStarted)
        }
    }

    /**
     * Ends the current exercise early; for a superset, all of its members. Each one with sets
     * logged has its result reviewed first, which finishes it; the others finish right away.
     */
    private fun finishExercise() {
        val current = view.value ?: return
        val next = current.next ?: return
        val progress = current.active.progress
        val (withSets, withoutSets) = current.active.workout.items[next.itemIndex].exercises
            .filterNot { progress.isComplete(it) }
            .partition { progress.loggedSets(it).isNotEmpty() }
        reviewQueue = withSets.map { it.id }
        viewModelScope.launch {
            if (withoutSets.isNotEmpty()) logRepository.finishExercises(current.active.session, withoutSets.map { it.id })
            reviewQueue.firstOrNull()?.let { _navigation.send(SessionNavigation.ReviewResult(it)) }
        }
    }

    private fun updateEntry(transform: (SetEntry) -> SetEntry) {
        val current = view.value ?: return
        val entry = current.ui.entry ?: return
        entryEdits.update { current.entryKey to transform(entry) }
    }
}
