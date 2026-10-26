package com.github.irdcat.workout.testing

import android.content.Context
import android.content.Intent
import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.auth.AuthorizationResult
import com.github.irdcat.workout.domain.auth.SignInResult
import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.results.SavedResult
import com.github.irdcat.workout.domain.results.WriteResultsOutcome
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.PastSession
import com.github.irdcat.workout.domain.session.SessionLog
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.StartSessionResult
import com.github.irdcat.workout.domain.session.WarmUpRestRepository
import com.github.irdcat.workout.domain.session.warmUpRestKey
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.RestAlarm
import com.github.irdcat.workout.domain.settings.RestEndAlert
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.SyncInfo
import com.github.irdcat.workout.domain.workout.SyncResult
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Runs viewModelScope (Dispatchers.Main) on a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** A clock tests move by hand with [advance]. */
class MutableClock(private var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    fun advance(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}

/** A clock stopped at [date] [time] UTC. */
fun fixedClock(date: String, time: String = "18:00"): Clock =
    Clock.fixed(LocalDate.parse(date).atTime(LocalTime.parse(time)).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

class FakeWorkoutPlanRepository(
    months: List<TrainingMonth> = emptyList(),
    lastSyncedAt: Instant? = Instant.parse("2026-01-01T12:00:00Z"),
) : WorkoutPlanRepository {

    val months = MutableStateFlow(months)
    val syncInfo = MutableStateFlow(SyncInfo(lastSyncedAt, sheetCount = 0, weekCount = 0))

    /** What [sync] returns; on success the plan becomes [monthsAfterSync]. */
    var syncResult: SyncResult = SyncResult.Success(0, 0)
    var monthsAfterSync: List<TrainingMonth>? = null
    var syncCalls = 0
        private set

    private val weeks: Flow<List<TrainingWeek>> = this.months.map { list -> list.flatMap { it.weeks } }

    override fun observeMonths(): Flow<List<TrainingMonth>> = months

    override fun observeWeek(weekId: String): Flow<TrainingWeek?> =
        weeks.map { list -> list.firstOrNull { it.id == weekId } }

    override fun observeWorkout(workoutId: String): Flow<Workout?> =
        weeks.map { list -> list.flatMap { it.workouts }.firstOrNull { it.id == workoutId } }

    override fun observeWeekOfWorkout(workoutId: String): Flow<TrainingWeek?> =
        weeks.map { list -> list.firstOrNull { week -> week.workouts.any { it.id == workoutId } } }

    override fun observeSyncInfo(): Flow<SyncInfo> = syncInfo

    override suspend fun sync(): SyncResult {
        syncCalls++
        if (syncResult is SyncResult.Success) {
            monthsAfterSync?.let { months.value = it }
            syncInfo.value = syncInfo.value.copy(lastSyncedAt = Instant.parse("2026-01-01T12:00:00Z"))
        }
        return syncResult
    }

    override suspend fun clearCache() {
        months.value = emptyList()
        syncInfo.value = SyncInfo(null, 0, 0)
    }
}

class FakeActiveSessionRepository(
    private val clock: Clock,
    initial: ActiveSession? = null,
) : ActiveSessionRepository {

    val session = MutableStateFlow(initial)

    override val activeSession: Flow<ActiveSession?> = session

    override suspend fun getActiveSession(): ActiveSession? = session.first()

    override suspend fun start(workoutId: String): StartSessionResult {
        val current = session.value
        return when {
            current == null -> StartSessionResult.Started(ActiveSession(workoutId, clock.instant()).also { session.value = it })
            current.workoutId == workoutId -> StartSessionResult.Started(current)
            else -> StartSessionResult.AnotherActive(current)
        }
    }

    override suspend fun end() {
        session.value = null
    }
}

class FakeAuthRepository(
    var signInResult: SignInResult = SignInResult.SignedIn("your.name@gmail.com"),
    var authorizeResult: AuthorizationResult = AuthorizationResult.Granted,
    var consentResult: AuthorizationResult = AuthorizationResult.Granted,
) : AuthRepository {

    val email = MutableStateFlow<String?>(null)
    var signInCalls = 0
        private set

    override val signedInEmail: Flow<String?> = email

    override suspend fun getSignedInEmail(): String? = email.value

    override suspend fun signIn(activityContext: Context, serverClientId: String): SignInResult {
        signInCalls++
        return signInResult.also { if (it is SignInResult.SignedIn) email.value = it.email }
    }

    override suspend fun authorize(): AuthorizationResult = authorizeResult

    override fun completeAuthorization(data: Intent?): AuthorizationResult = consentResult

    override suspend fun signOut(revokeAccess: Boolean) {
        email.value = null
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun getSettings(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

class FakeWarmUpRestRepository : WarmUpRestRepository {

    /** By [warmUpRestKey]. */
    val rests = MutableStateFlow<Map<String, Int>>(emptyMap())

    override fun observe(exerciseName: String): Flow<Int?> = rests.map { it[warmUpRestKey(exerciseName)] }

    override suspend fun save(exerciseName: String, seconds: Int) {
        rests.value = rests.value + (warmUpRestKey(exerciseName) to seconds)
    }
}

class FakeSetRestRepository : SetRestRepository {

    /** By [warmUpRestKey], as the real one keys them. */
    val rests = MutableStateFlow<Map<String, Int>>(emptyMap())

    override fun observe(exerciseName: String): Flow<Int?> = rests.map { it[warmUpRestKey(exerciseName)] }

    override suspend fun save(exerciseName: String, seconds: Int) {
        rests.value = rests.value + (warmUpRestKey(exerciseName) to seconds)
    }
}

class FakeSessionLogRepository : SessionLogRepository {

    val logs = MutableStateFlow<Map<ActiveSession, SessionLog>>(emptyMap())
    /** Sessions finished with "Save results", and when. */
    val finished = mutableMapOf<ActiveSession, Instant>()
    private var nextId = 1L

    override fun observe(session: ActiveSession): Flow<SessionLog> = logs.map { it[session] ?: SessionLog() }

    override suspend fun logSet(session: ActiveSession, set: LoggedSet) {
        update(session) { it.copy(sets = it.sets + set.copy(id = nextId++)) }
    }

    override suspend fun undoLastSet(session: ActiveSession): LoggedSet? {
        val last = logs.value[session]?.sets?.lastOrNull() ?: return null
        update(session) { it.copy(sets = it.sets.dropLast(1), rests = it.rests - last.id) }
        return last
    }

    override suspend fun recordRest(session: ActiveSession, setId: Long, endedAt: Instant) {
        val set = logs.value[session]?.sets?.firstOrNull { it.id == setId } ?: return
        update(session) { log ->
            if (setId in log.rests) log else log.copy(rests = log.rests + (setId to Duration.between(set.loggedAt, endedAt)))
        }
    }

    override suspend fun finishSession(session: ActiveSession, finishedAt: Instant) {
        finished[session] = finishedAt
    }

    override suspend fun discardSession(session: ActiveSession) {
        logs.value = logs.value - session
        finished -= session
    }

    override suspend fun latestSessionOf(workoutId: String): PastSession? =
        logs.value.filter { (session, log) -> session.workoutId == workoutId && log.sets.isNotEmpty() }
            .maxByOrNull { it.key.startedAt }
            ?.let { (session, log) -> PastSession(session, log, finished[session]) }

    override fun observeAllSets(): Flow<List<LoggedSet>> =
        logs.map { all -> all.values.flatMap { it.sets }.filterNot { it.warmUp }.sortedBy { it.loggedAt } }

    override suspend fun finishExercises(session: ActiveSession, exerciseIds: Collection<String>) {
        update(session) { it.copy(finishedExerciseIds = it.finishedExerciseIds + exerciseIds) }
    }

    private fun update(session: ActiveSession, transform: (SessionLog) -> SessionLog) {
        logs.value = logs.value + (session to transform(logs.value[session] ?: SessionLog()))
    }
}

class FakeResultRepository : ResultRepository {

    val saved = MutableStateFlow<List<SavedResult>>(emptyList())
    /** Everything [writeSaved] wrote to the "sheet", oldest first. */
    val written = mutableListOf<SavedResult>()

    /** What [writeSaved] returns; on [WriteResultsOutcome.Written] everything saved is cleared. */
    var writeOutcome: WriteResultsOutcome? = null
    var writeCalls = 0
        private set

    override fun observeSaved(): Flow<List<SavedResult>> = saved

    override suspend fun save(result: SavedResult) {
        saved.value = saved.value.filterNot { it.target == result.target } + result
    }

    override suspend fun discardSavedSince(workoutId: String, since: Instant) {
        saved.value = saved.value.filterNot { it.target.workoutId == workoutId && it.savedAt >= since }
    }

    override suspend fun writeSaved(): WriteResultsOutcome {
        writeCalls++
        val outcome = writeOutcome
            ?: if (saved.value.isEmpty()) WriteResultsOutcome.NothingToWrite else WriteResultsOutcome.Written(saved.value.size)
        if (outcome is WriteResultsOutcome.Written) {
            written += saved.value
            saved.value = emptyList()
        }
        return outcome
    }
}

/** Remembers the rest alarm instead of setting one. */
class RecordingRestAlarm : RestAlarm {

    /** The alarm as it stands: null once cancelled. */
    var scheduled: Pair<Instant, RestEndAlert>? = null
        private set
    val history = mutableListOf<Pair<Instant, RestEndAlert>?>()

    override fun schedule(endsAt: Instant, alert: RestEndAlert) {
        scheduled = endsAt to alert
        history += scheduled
    }

    override fun cancel() {
        scheduled = null
        history += null
    }
}
