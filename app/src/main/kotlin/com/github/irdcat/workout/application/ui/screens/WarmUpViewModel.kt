package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.AbandonSession
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.DEFAULT_WARM_UP_REST_SECONDS
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.Exercise
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
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import com.github.irdcat.workout.domain.session.LastTime as LastTimeData

sealed interface WarmUpNavigation {
    /** A warm-up was logged with a rest after it. */
    data object RestStarted : WarmUpNavigation

    /** No session is open any more; leave the screen. */
    data object SessionEnded : WarmUpNavigation
}

private val LastTimeDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** What the screen shows plus what's needed to act on it. */
private data class WarmUpView(
    val ui: WarmUpUiState,
    val active: ActiveWorkout,
    val exercise: Exercise,
    /** The planned group warm-ups are logged against: the next working set's. */
    val setGroupId: String,
    /** Keys the user's entry edits: they belong to one warm-up and reset for the next. */
    val entryKey: String,
    val autoStartRestTimer: Boolean,
)

/**
 * Warm-up sets for the exercise up next. They're logged like any set but marked as
 * warm-ups, so they don't count towards the plan and never reach the sheet. The rest
 * after them is the user's own: it starts from the one they used last for the exercise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WarmUpViewModel @Inject constructor(
    sessionRepository: ActiveSessionRepository,
    planRepository: WorkoutPlanRepository,
    private val logRepository: SessionLogRepository,
    private val warmUpRests: WarmUpRestRepository,
    private val clock: Clock,
    settingsRepository: SettingsRepository,
    findLastTime: FindLastTime,
    private val abandonSession: AbandonSession,
) : ViewModel() {

    private val entryEdits = MutableStateFlow<Pair<String, WarmUpEntry>?>(null)

    private val activeWorkout = activeWorkoutFlow(sessionRepository, planRepository, logRepository)

    private val lastTime: Flow<LastTimeData?> = sessionRepository.activeSession
        .map { it?.workoutId }
        .distinctUntilChanged()
        .mapLatest { workoutId -> workoutId?.let { findLastTime(it) } }

    /** The warm-up rest saved for the exercise up next. */
    private val savedRest: Flow<Int?> = activeWorkout
        .map { it?.let(::exerciseOf)?.name }
        .distinctUntilChanged()
        .flatMapLatest { name -> name?.let(warmUpRests::observe) ?: flowOf(null) }

    private val view: StateFlow<WarmUpView?> = combine(
        activeWorkout.flatMapLatest { active ->
            if (active == null) flowOf(null) else elapsedSince(active.session.startedAt, clock).map { active to it }
        },
        entryEdits,
        settingsRepository.settings,
        lastTime,
        savedRest,
    ) { activeWithClock, edits, settings, lastTime, savedRest ->
        activeWithClock?.let { (active, elapsed) -> warmUpView(active, elapsed, edits, settings, lastTime, savedRest) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<Loadable<WarmUpUiState>> = view
        .map { it?.let { view -> Loadable.Ready(view.ui) } ?: Loadable.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    private val _navigation = Channel<WarmUpNavigation>(Channel.BUFFERED)
    val navigation: Flow<WarmUpNavigation> = _navigation.receiveAsFlow()

    init {
        viewModelScope.launch {
            sessionRepository.activeSession.first { it == null }
            _navigation.send(WarmUpNavigation.SessionEnded)
        }
    }

    /** Handles every [WarmUpEvent] except navigation, which the route handles. */
    fun onEvent(event: WarmUpEvent) {
        when (event) {
            is WarmUpEvent.WeightChanged -> updateEntry { it.copy(weight = event.value.filterDecimal()) }
            is WarmUpEvent.AdjustWeight -> updateEntry { it.copy(weight = stepWeight(it.weight, it.weightStep, event.direction)) }
            is WarmUpEvent.WeightStepSelected -> updateEntry { it.copy(weightStep = event.step) }
            is WarmUpEvent.RepsChanged -> updateEntry { it.copy(reps = event.value.filter(Char::isDigit)) }
            is WarmUpEvent.AdjustReps -> updateEntry {
                val step = view.value?.exercise?.repsUnit?.step ?: 1
                val reps = (it.reps.toIntOrNull() ?: 0) + event.direction * step
                it.copy(reps = reps.coerceAtLeast(0).toString())
            }
            is WarmUpEvent.RestChanged -> updateEntry { it.copy(rest = event.value.filterRestTime()) }
            is WarmUpEvent.AdjustRest -> updateEntry {
                val seconds = (parseRestTime(it.rest) ?: 0) + 15 * event.direction
                it.copy(rest = formatRestTime(seconds.coerceAtLeast(0)))
            }
            WarmUpEvent.LogWarmUp -> logWarmUp()
            // Ending the session sends SessionEnded, which leaves the screen.
            WarmUpEvent.AbandonWorkout -> viewModelScope.launch { abandonSession() }
            WarmUpEvent.Back, WarmUpEvent.FinishWorkout, WarmUpEvent.StartWorkingSets -> Unit // Navigation.
        }
    }

    private fun logWarmUp() {
        val current = view.value ?: return
        val entry = current.ui.entry
        val weight = entry.weight.toWeightKg() ?: return
        val reps = entry.reps.toIntOrNull() ?: return
        val rest = parseRestTime(entry.rest) ?: return
        val set = LoggedSet(
            exerciseId = current.exercise.id,
            setGroupId = current.setGroupId,
            weightKg = weight.takeIf { it > 0 },
            reps = reps,
            rpe = null,
            warmUp = true,
            loggedAt = clock.instant(),
        )
        viewModelScope.launch {
            // The rest timer reads it from here; it's also where the next warm-up starts from.
            warmUpRests.save(current.exercise.name, rest)
            logRepository.logSet(current.active.session, set)
            if (rest > 0 && current.autoStartRestTimer) _navigation.send(WarmUpNavigation.RestStarted)
        }
    }

    private fun updateEntry(transform: (WarmUpEntry) -> WarmUpEntry) {
        val current = view.value ?: return
        entryEdits.update { current.entryKey to transform(current.ui.entry) }
    }

    // ---- Mapping ---------------------------------------------------------

    /** Warm-ups are for the exercise up next; once the plan is done, its last exercise. */
    private fun exerciseOf(active: ActiveWorkout): Exercise =
        active.currentExercise ?: active.workout.items.last().exercises.last()

    private fun warmUpView(
        active: ActiveWorkout,
        elapsed: Duration,
        edits: Pair<String, WarmUpEntry>?,
        settings: AppSettings,
        lastTime: LastTimeData?,
        savedRest: Int?,
    ): WarmUpView {
        val progress = active.progress
        val exercise = exerciseOf(active)
        val done = progress.warmUpSets(exercise)
        val number = done.size + 1
        val then = lastTime?.warmUpsLike(exercise).orEmpty()
        // Start from the same warm-up last time, else repeat the one just done.
        val template = then.getOrNull(number - 1) ?: done.lastOrNull()
        val entryKey = "${exercise.id}#warm-up#$number"
        val entry = edits?.takeIf { it.first == entryKey }?.second ?: WarmUpEntry(
            weight = template?.weightKg?.kg() ?: "0",
            weightStep = settings.defaultWeightStepKg.kg(),
            reps = template?.reps?.toString().orEmpty(),
            rest = formatRestTime(savedRest ?: DEFAULT_WARM_UP_REST_SECONDS),
        )
        val setGroupId = progress.next?.takeIf { it.exercise.id == exercise.id }?.group?.id
            ?: progress.plannedSets(exercise).last().id
        return WarmUpView(
            ui = WarmUpUiState(
                workoutName = active.workout.code,
                weekRange = active.week.range,
                elapsed = if (settings.measureWorkoutTime) formatElapsed(elapsed) else null,
                position = active.workout.exercises.indexOf(exercise) + 1,
                exerciseCount = active.workout.exercises.size,
                exerciseName = exercise.name,
                plannedSets = progress.plannedCount(exercise),
                done = done.map { it.chipLabel(exercise.repsUnitOf(it.setGroupId)) },
                number = number,
                entry = entry,
                repsLabel = exercise.repsUnit.fieldLabel,
                lastTime = then.takeIf { it.isNotEmpty() }?.let { sets ->
                    LastTime(
                        date = lastTime?.session?.session?.startedAt?.atZone(clock.zone)?.format(LastTimeDate),
                        summary = sets.joinToString(", ") { it.chipLabel(exercise.repsUnit) },
                        note = null,
                    )
                },
            ),
            active = active,
            exercise = exercise,
            setGroupId = setGroupId,
            entryKey = entryKey,
            autoStartRestTimer = settings.autoStartRestTimer,
        )
    }
}

/** Digits and one ':' while typing a time. */
internal fun String.filterRestTime(): String {
    var seenColon = false
    return filter { c ->
        when {
            c.isDigit() -> true
            c == ':' && !seenColon -> true.also { seenColon = true }
            else -> false
        }
    }
}
