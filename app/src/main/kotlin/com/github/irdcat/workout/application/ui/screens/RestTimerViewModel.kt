package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.NextSet
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.SessionProgress
import com.github.irdcat.workout.domain.session.RestChanges
import com.github.irdcat.workout.domain.session.RestClocks
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.RestAlarm
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.WorkoutItem
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

sealed interface RestTimerNavigation {
    /** Rest is over (skipped, ended or left), or there's nothing to time; back to where it started. */
    data object Done : RestTimerNavigation

    /** The last set was undone; return to logging it again. */
    data object SetUndone : RestTimerNavigation

    /** The rest after a warm-up was skipped: on to the working sets. */
    data object StartWorkingSets : RestTimerNavigation

    /** Off to Home while the rest keeps running. */
    data object Home : RestTimerNavigation
}

/** Lengths offered to restart a working set's rest with; the sheet's own joins them. */
private val RestartPresets = listOf(60, 90, 120, 150, 180)

/** Lengths offered for a warm-up's rest; the saved one joins them. */
private val WarmUpPresets = listOf(30, 45, 60, 90, 120)

/** What the screen shows plus what's needed to act on it. */
private data class RestView(
    val ui: RestTimerUiState,
    val active: ActiveWorkout,
    /** The set whose rest is being timed. */
    val lastSet: LoggedSet,
    val exerciseName: String,
    /** null while paused: there's no end to alert at. */
    val endsAt: Instant?,
    val alert: RestEndAlert,
)

/**
 * Times the rest after the last logged set: it ends at the set's log time plus
 * its rest (and any time spent paused), so it keeps running correctly while the app
 * is in the background. The alert is an alarm for that moment, so it goes off even
 * if the app is closed. A working set's rest comes from the sheet; a warm-up's is
 * the user's own, saved per exercise. Changes to it live in [RestClocks], so it
 * carries on while the user is elsewhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RestTimerViewModel @Inject constructor(
    sessionRepository: ActiveSessionRepository,
    planRepository: WorkoutPlanRepository,
    private val logRepository: SessionLogRepository,
    settingsRepository: SettingsRepository,
    private val warmUpRests: WarmUpRestRepository,
    setRests: SetRestRepository,
    private val restClocks: RestClocks,
    private val restAlarm: RestAlarm,
    private val clock: Clock,
) : ViewModel() {

    /** "Use for all warm-ups": a warm-up rest changed here is saved for the exercise. */
    private val useForAllWarmUps = MutableStateFlow(true)

    private val activeWorkout = activeWorkoutFlow(sessionRepository, planRepository, logRepository)

    /** The saved rest for the exercise of the last set, when that was a warm-up or an unlimited set. */
    private val chosenRest: Flow<Int?> = chosenRestFlow(activeWorkout, warmUpRests, setRests)

    /** null when there's no set with a rest to time. */
    private val view: StateFlow<RestView?> = combine(
        activeWorkout.flatMapLatest { active ->
            if (active == null) flowOf(null) else elapsedSince(active.session.startedAt, clock).map { active to it }
        },
        restClocks.changes,
        settingsRepository.settings,
        chosenRest,
        useForAllWarmUps,
    ) { activeWithClock, changes, settings, chosenRest, useForAll ->
        activeWithClock?.let { (active, elapsed) -> restView(active, elapsed, changes, settings, chosenRest, useForAll) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<Loadable<RestTimerUiState>> = view
        .map { it?.let { view -> Loadable.Ready(view.ui) } ?: Loadable.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    private val _navigation = Channel<RestTimerNavigation>(Channel.BUFFERED)
    val navigation: Flow<RestTimerNavigation> = _navigation.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Once per rest, and again whenever a change or the setting moves its end. A rest
            // that's paused or already over (the screen reopened late) doesn't alert.
            view.filterNotNull()
                .map { Triple(it.lastSet.id, it.endsAt, it.alert) }
                .distinctUntilChanged()
                .collect { (_, endsAt, alert) ->
                    if (endsAt != null && endsAt.isAfter(clock.instant())) restAlarm.schedule(endsAt, alert) else restAlarm.cancel()
                }
        }
        viewModelScope.launch {
            // Opened without a set to rest after (e.g. it was undone elsewhere): nothing to show.
            if (activeWorkout.first()?.currentRest(chosenRestSeconds = null) == null) {
                _navigation.send(RestTimerNavigation.Done)
            }
        }
    }

    fun onEvent(event: RestTimerEvent) {
        when (event) {
            is RestTimerEvent.Adjust -> change { it.copy(adjustSeconds = it.adjustSeconds + event.seconds) }
            RestTimerEvent.TogglePause -> change { it.copy(pause = it.pause.toggled(clock.instant())) }
            is RestTimerEvent.Restart -> change { RestChanges(lengthSeconds = event.seconds, restartedAt = clock.instant()) }
            is RestTimerEvent.SetLength -> setLength(event.seconds)
            is RestTimerEvent.UseForAllWarmUps -> useForAll(event.enabled)
            RestTimerEvent.Skip -> endRest(
                if (view.value?.lastSet?.warmUp == true) RestTimerNavigation.StartWorkingSets else RestTimerNavigation.Done
            )
            RestTimerEvent.Back, RestTimerEvent.AnotherWarmUp -> endRest(RestTimerNavigation.Done)
            // The rest isn't over: its alarm stays set, and Home shows it.
            RestTimerEvent.Home -> viewModelScope.launch { _navigation.send(RestTimerNavigation.Home) }
            RestTimerEvent.Undo -> undo()
        }
    }

    private fun change(transform: (RestChanges) -> RestChanges) {
        val rest = view.value ?: return
        restClocks.update(rest.lastSet.id, transform)
    }

    /** A new length for this rest, keeping the time already rested. */
    private fun setLength(seconds: Int) {
        val rest = view.value ?: return
        if (rest.lastSet.warmUp && useForAllWarmUps.value) {
            // The saved time is what a warm-up's rest is: change that instead.
            change { it.copy(lengthSeconds = null, adjustSeconds = 0) }
            viewModelScope.launch { warmUpRests.save(rest.exerciseName, seconds) }
        } else {
            change { it.copy(lengthSeconds = seconds, adjustSeconds = 0) }
        }
    }

    private fun useForAll(enabled: Boolean) {
        useForAllWarmUps.value = enabled
        val rest = view.value ?: return
        if (enabled && rest.lastSet.warmUp) setLength(rest.ui.totalSeconds)
    }

    /** Ends the rest now, by skipping, starting the next set or going back: that's its real length. */
    private fun endRest(then: RestTimerNavigation) {
        val rest = view.value
        restAlarm.cancel()
        viewModelScope.launch {
            rest?.let { logRepository.recordRest(it.active.session, it.lastSet.id, clock.instant()) }
            _navigation.send(then)
        }
    }

    private fun undo() {
        val rest = view.value ?: return
        restAlarm.cancel()
        viewModelScope.launch {
            logRepository.undoLastSet(rest.active.session)
            _navigation.send(RestTimerNavigation.SetUndone)
        }
    }

    // ---- Mapping ---------------------------------------------------------

    private fun restView(
        active: ActiveWorkout,
        elapsed: Duration,
        allChanges: Map<Long, RestChanges>,
        settings: AppSettings,
        chosenRest: Int?,
        useForAll: Boolean,
    ): RestView? {
        val (last, planned) = active.currentRest(chosenRest) ?: return null
        val progress = active.progress
        val exercise = progress.exerciseOf(last) ?: return null
        // An unlimited set's rest was picked by the user, so it isn't the sheet's.
        val sheetRest = planned.takeIf { !last.warmUp && !exercise.isUnlimited }
        val changes = allChanges[last.id] ?: RestChanges()
        val total = changes.totalSeconds(planned)
        val remaining = changes.remainingSeconds(planned, last.loggedAt, clock.instant())
        val endsAt = changes.endsAt(planned, last.loggedAt)
        return RestView(
            ui = RestTimerUiState(
                workoutName = active.workout.code,
                elapsed = if (settings.measureWorkoutTime) formatElapsed(elapsed) else null,
                remainingSeconds = remaining,
                totalSeconds = total,
                paused = changes.pause.isPaused,
                fromSheet = sheetRest != null && total == sheetRest,
                presets = if (last.warmUp) {
                    presets(WarmUpPresets + planned, total, sheet = null)
                } else {
                    presets(RestartPresets + planned, total, sheet = sheetRest)
                },
                lastSet = LoggedSetInfo(label = setLabel(progress, last), summary = last.summaryText(exercise.repsUnitOf(last.setGroupId))),
                upNext = progress.next?.let { upNext(progress, it) },
                warmUp = if (last.warmUp) WarmUpRest(exerciseName = exercise.name, useForAll = useForAll) else null,
            ),
            active = active,
            lastSet = last,
            exerciseName = exercise.name,
            endsAt = endsAt,
            alert = settings.restEndAlert,
        )
    }

    private fun presets(seconds: List<Int>, total: Int, sheet: Int?): List<RestPreset> =
        seconds.distinct().sorted().map { RestPreset(seconds = it, selected = it == total, fromSheet = it == sheet) }

    /** "Set 3", "Warm-up 2", or "5b · round 2" in a superset. */
    private fun setLabel(progress: SessionProgress, set: LoggedSet): String {
        val number = progress.setNumberOf(set)
        if (set.warmUp) return "Warm-up $number"
        val exercise = progress.exerciseOf(set) ?: return "Set $number"
        return positionLabel(progress, exercise.id, number)
    }

    private fun upNext(progress: SessionProgress, next: NextSet): UpNext {
        val group = next.group
        val inSuperset = next.round != null
        return UpNext(
            exerciseName = next.exercise.name,
            label = positionLabel(progress, next.exercise.id, next.round ?: next.setNumber),
            targets = listOfNotNull(group.reps.repsText(), group.load.takeIf { it != Load.Bodyweight }?.label()),
            tempo = group.tempo?.let { Tempo(it.phases) },
            setCount = next.plannedCount?.takeIf { !inSuperset && next.setNumber <= it },
            target = group.targetText(),
        )
    }

    private fun positionLabel(progress: SessionProgress, exerciseId: String, number: Int): String {
        val exercise = progress.workout.exercises.first { it.id == exerciseId }
        val (index, item) = progress.itemOf(exercise) ?: return "Set $number"
        return if (item is WorkoutItem.Superset) {
            "${supersetMemberTag(index + 1, item.exercises.indexOf(exercise))} · round $number"
        } else {
            "Set $number"
        }
    }
}
