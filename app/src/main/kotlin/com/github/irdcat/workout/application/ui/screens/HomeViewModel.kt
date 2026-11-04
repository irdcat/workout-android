package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.session.SessionStatus
import com.github.irdcat.workout.application.session.SessionStatusSource
import com.github.irdcat.workout.application.sync.SyncCoordinator
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.StartSessionResult
import com.github.irdcat.workout.domain.workout.SyncInfo
import com.github.irdcat.workout.domain.workout.SyncResult
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.github.irdcat.workout.domain.workout.weekOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

/** How syncing stands: running, failed for lack of a connection, or the sheet edited since. */
private data class SyncState(val refreshing: Boolean, val offline: Boolean, val sheetChanged: Boolean)

/** Offline wins: the sheet can't be read then, changed or not. */
private fun SyncState.toStatus(lastSynced: String): SyncStatus = when {
    offline -> SyncStatus.Offline(lastSynced)
    sheetChanged -> SyncStatus.OutOfSync(lastSynced)
    else -> SyncStatus.Synced(lastSynced)
}

/** How often Home asks Drive whether the sheet changed, while it's shown. */
private val SheetChangeCheckInterval = Duration.ofMinutes(5)

sealed interface HomeNavigation {
    /** A workout was started; open the session. */
    data object SessionStarted : HomeNavigation
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val planRepository: WorkoutPlanRepository,
    private val sessionRepository: ActiveSessionRepository,
    private val clock: Clock,
    private val syncCoordinator: SyncCoordinator,
    sessionStatus: SessionStatusSource,
) : ViewModel() {

    /** Any sync, wherever it was started: Home, Settings, or results being written. */
    private val isRefreshing: Flow<Boolean> = syncCoordinator.isSyncing

    /** Set when the last sync failed for lack of a connection. */
    private val isOffline: Flow<Boolean> = syncCoordinator.lastResult
        .map { it is SyncResult.Failed && it.cause is IOException }
        .distinctUntilChanged()

    /**
     * Whether the sheet was edited after the last sync. Checked when Home is shown
     * (collection restarts on return), after each refresh, then every few minutes.
     */
    private val sheetChanged: Flow<Boolean> = isRefreshing.flatMapLatest { refreshing ->
        if (refreshing) {
            flowOf(false)
        } else {
            flow {
                while (true) {
                    emit(planRepository.isSheetChangedSinceSync() == true)
                    delay(SheetChangeCheckInterval.toMillis())
                }
            }
        }
    }.distinctUntilChanged()

    private val syncState: Flow<SyncState> = combine(isRefreshing, isOffline, sheetChanged, ::SyncState)

    /** The active session, ticking every second for its clock and any rest; null when none is open. */
    private val session: Flow<SessionStatus?> = sessionStatus.status

    val state: StateFlow<Loadable<HomeUiState>> = combine(
        planRepository.observeMonths(),
        planRepository.observeSyncInfo(),
        syncState,
        session,
    ) { months, sync, syncState, session ->
        // The skeleton (with a working tab bar) on the very first sync, before anything is cached.
        if (months.isEmpty() && syncState.refreshing) {
            Loadable.Loading
        } else {
            Loadable.Ready(homeState(months, sync, syncState, session, LocalDate.now(clock)))
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

    /** Starts a sync, or joins the one running; it carries on if Home is left. */
    fun onRefresh() {
        syncCoordinator.sync()
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
        syncState: SyncState,
        session: SessionStatus?,
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
            sync = syncState.toStatus(lastSynced),
            isRefreshing = syncState.refreshing,
            activeSession = activeSession,
            content = content,
        )
    }
}
