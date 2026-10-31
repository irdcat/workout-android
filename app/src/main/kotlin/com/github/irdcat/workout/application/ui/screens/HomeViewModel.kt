package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.RestChanges
import com.github.irdcat.workout.domain.session.RestClocks
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.StartSessionResult
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.SyncInfo
import com.github.irdcat.workout.domain.workout.SyncResult
import com.github.irdcat.workout.domain.workout.SyncWorkoutPlan
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.domain.workout.weekOf
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The active session as Home shows it: its clock, and the rest being taken, if any. */
private data class SessionNow(
    val active: ActiveWorkout,
    /** null when "measure workout time" is off. */
    val elapsed: Duration?,
    val rest: RestNow?,
)

private data class RestNow(val remainingSeconds: Int, val paused: Boolean)

sealed interface HomeNavigation {
    /** A workout was started; open the session. */
    data object SessionStarted : HomeNavigation
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val planRepository: WorkoutPlanRepository,
    private val sessionRepository: ActiveSessionRepository,
    private val logRepository: SessionLogRepository,
    private val clock: Clock,
    settingsRepository: SettingsRepository,
    private val syncWorkoutPlan: SyncWorkoutPlan,
    private val warmUpRests: WarmUpRestRepository,
    private val setRests: SetRestRepository,
    private val restClocks: RestClocks,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)

    /** Set when the last sync failed for lack of a connection. */
    private val isOffline = MutableStateFlow(false)

    /** The active session, ticking every second for its clock and any rest; null when none is open. */
    private val session: Flow<SessionNow?> =
        settingsRepository.settings.map { it.measureWorkoutTime }.distinctUntilChanged().flatMapLatest { measure ->
            activeWorkoutFlow(sessionRepository, planRepository, logRepository).flatMapLatest { active ->
                if (active == null) {
                    flowOf(null)
                } else {
                    combine(
                        elapsedSince(active.session.startedAt, clock),
                        chosenRestFlow(flowOf(active), warmUpRests, setRests),
                        restClocks.changes,
                    ) { elapsed, chosenRest, changes ->
                        SessionNow(active, elapsed.takeIf { measure }, restNow(active, chosenRest, changes))
                    }
                }
            }
        }

    /** A rest still running (or paused) after the last set; one that has run out isn't shown. */
    private fun restNow(active: ActiveWorkout, chosenRest: Int?, changes: Map<Long, RestChanges>): RestNow? {
        val (last, planned) = active.currentRest(chosenRest) ?: return null
        val rest = changes[last.id] ?: RestChanges()
        val remaining = rest.remainingSeconds(planned, last.loggedAt, clock.instant())
        return RestNow(remaining, rest.pause.isPaused).takeIf { remaining > 0 }
    }

    val state: StateFlow<Loadable<HomeUiState>> = combine(
        planRepository.observeMonths(),
        planRepository.observeSyncInfo(),
        isRefreshing,
        isOffline,
        session,
    ) { months, sync, refreshing, offline, session ->
        // Keep the spinner on the very first sync, before anything is cached.
        if (months.isEmpty() && refreshing) {
            Loadable.Loading
        } else {
            Loadable.Ready(homeState(months, sync, refreshing, offline, session, LocalDate.now(clock)))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    private val _navigation = Channel<HomeNavigation>(Channel.BUFFERED)
    val navigation: Flow<HomeNavigation> = _navigation.receiveAsFlow()

    init {
        viewModelScope.launch {
            // First launch, or the cache was cleared: load the sheet straight away.
            if (planRepository.observeSyncInfo().first().lastSyncedAt == null) onRefresh()
        }
    }

    fun onRefresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            val result = syncWorkoutPlan().result
            isOffline.value = result is SyncResult.Failed && result.cause is IOException
            isRefreshing.value = false
        }
    }

    fun onStartWorkout(workoutId: String) {
        viewModelScope.launch {
            // Home only offers Start while no session is open, so AnotherActive can't happen here.
            if (sessionRepository.start(workoutId) is StartSessionResult.Started) {
                _navigation.send(HomeNavigation.SessionStarted)
            }
        }
    }

    private fun homeState(
        months: List<TrainingMonth>,
        sync: SyncInfo,
        refreshing: Boolean,
        offline: Boolean,
        session: SessionNow?,
        today: LocalDate,
    ): HomeUiState {
        val activeWorkout = session?.active?.workout
        val week = months.weekOf(today)
        val monday = today.with(DayOfWeek.MONDAY)
        val lastSynced = sync.lastSyncedAt?.let { formatTime(it, clock.zone) } ?: "—"
        val content = when {
            week == null -> {
                val latest = months.lastOrNull()?.weeks?.lastOrNull()
                HomeContent.NoPlan(
                    weekStart = formatDayMonth(monday),
                    newestSheet = latest?.sheetName,
                    newestSheetEnd = latest?.end?.let(::formatDayMonth),
                    latestWeekRange = latest?.range,
                )
            }
            week.isRestWeek -> HomeContent.RestWeek
            else -> HomeContent.Plan(
                workouts = week.workouts.map { workout ->
                    val summary = workout.toSummary()
                    if (workout.id == activeWorkout?.id) summary.copy(status = WorkoutStatus.InProgress) else summary
                },
            )
        }
        // The session may belong to another week (e.g. left open last week), so it's shown whatever this week holds.
        val activeSession = session?.let { (active, elapsed, rest) ->
            val next = active.progress.next
            val review = active.progress.toReview
            ActiveSession(
                workoutName = active.workout.code,
                elapsed = elapsed?.let(::formatElapsed),
                exercise = next?.exercise?.name ?: review?.name,
                reviewing = review != null,
                set = next?.setNumber,
                setCount = next?.plannedCount,
                rest = rest?.let { ActiveRest(left = formatRestTime(it.remainingSeconds), paused = it.paused) },
            )
        }
        return HomeUiState(
            monthLabel = formatMonthYear(week?.month ?: YearMonth.from(today)),
            weekRange = week?.range ?: formatWeekRange(monday, monday.with(DayOfWeek.SUNDAY)),
            sync = if (offline) SyncStatus.Offline(lastSynced) else SyncStatus.Synced(lastSynced),
            isRefreshing = refreshing,
            activeSession = activeSession,
            content = content,
        )
    }
}
