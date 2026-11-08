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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
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

/** Drive's answer at [at], about the sync at [syncedAt]. */
private data class SheetCheck(val at: Instant, val syncedAt: Instant?, val changed: Boolean)

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

    /** Drive's last answer, kept so coming back to Home doesn't ask again straight away. */
    private var lastSheetCheck: SheetCheck? = null

    /**
     * Whether the sheet was edited after the last sync. Checked when Home is shown
     * (collection restarts on return), after each refresh, then every few minutes;
     * an answer younger than that, about the same sync (one started off Home moves the
     * sync time), is reused instead of asking Drive again.
     */
    private val sheetChanged: Flow<Boolean> = isRefreshing.flatMapLatest { refreshing ->
        if (refreshing) {
            // Whatever Drive said was about the sync before this one.
            lastSheetCheck = null
            flowOf(false)
        } else {
            flow {
                while (true) {
                    val check = freshSheetCheck()
                    emit(check.changed)
                    delay(SheetChangeCheckInterval.minus(Duration.between(check.at, clock.instant())).toMillis().coerceAtLeast(0))
                }
            }
        }
    }.distinctUntilChanged()

    private suspend fun freshSheetCheck(): SheetCheck {
        val syncedAt = planRepository.observeSyncInfo().first().lastSyncedAt
        val now = clock.instant()
        lastSheetCheck
            ?.takeIf { it.syncedAt == syncedAt && Duration.between(it.at, now) < SheetChangeCheckInterval }
            ?.let { return it }
        return SheetCheck(now, syncedAt, planRepository.isSheetChangedSinceSync() == true).also { lastSheetCheck = it }
    }

    private val syncState: Flow<SyncState> = combine(isRefreshing, isOffline, sheetChanged, ::SyncState)

    /** The active session, ticking every second for its clock and any rest; null when none is open. */
    private val session: Flow<SessionStatus?> =
        sessionStatus.status.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    /** The week without the session card: built again only when the plan, syncing or the session's workout changes. */
    private val week: Flow<Loadable<HomeUiState>> = combine(
        planRepository.observeMonths(),
        planRepository.observeSyncInfo(),
        syncState,
        session.map { it?.active?.workout?.id }.distinctUntilChanged(),
    ) { months, sync, syncState, activeWorkoutId ->
        // The skeleton (with a working tab bar) on the very first sync, before anything is cached.
        if (months.isEmpty() && syncState.refreshing) {
            Loadable.Loading
        } else {
            Loadable.Ready(homeState(months, sync, syncState, activeWorkoutId, LocalDate.now(clock)))
        }
    }

    /** The week with the session card, which alone follows the clock every second. */
    val state: StateFlow<Loadable<HomeUiState>> = combine(week, session) { week, session ->
        when (week) {
            is Loadable.Ready -> Loadable.Ready(week.value.copy(activeSession = session?.toCard()))
            else -> week
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
        activeWorkoutId: String?,
        today: LocalDate,
    ): HomeUiState {
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
                    if (workout.id == activeWorkoutId) summary.copy(status = WorkoutStatus.InProgress) else summary
                },
            )
        }
        return HomeUiState(
            monthLabel = formatMonthYear(week?.month ?: YearMonth.from(today)),
            weekRange = week?.range ?: formatWeekRange(monday, monday.with(DayOfWeek.SUNDAY)),
            sync = syncState.toStatus(lastSynced),
            isRefreshing = syncState.refreshing,
            content = content,
        )
    }

    /** The session may belong to another week (e.g. left open last week), so it's shown whatever this week holds. */
    private fun SessionStatus.toCard(): ActiveSession {
        val next = active.progress.next
        val review = active.progress.toReview
        return ActiveSession(
            workoutName = active.workout.code,
            elapsed = elapsed?.let(::formatElapsed),
            exercise = next?.exercise?.name ?: review?.name,
            reviewing = review != null,
            set = next?.setNumber,
            setCount = next?.plannedCount,
            rest = rest?.let { ActiveRest(left = formatRestTime(it.remainingSeconds), paused = it.paused) },
        )
    }
}
