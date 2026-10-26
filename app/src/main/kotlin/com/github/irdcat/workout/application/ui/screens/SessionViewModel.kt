package com.github.irdcat.workout.application.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.irdcat.workout.application.ui.components.Loadable
import com.github.irdcat.workout.domain.session.AbandonSession
import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.ActiveSessionRepository
import com.github.irdcat.workout.domain.session.FindLastTime
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.DEFAULT_SET_REST_SECONDS
import com.github.irdcat.workout.domain.session.NextSet
import com.github.irdcat.workout.domain.session.SessionLogRepository
import com.github.irdcat.workout.domain.session.SessionProgress
import com.github.irdcat.workout.domain.session.SetRestRepository
import com.github.irdcat.workout.domain.session.StopwatchPause
import com.github.irdcat.workout.domain.session.StopwatchPauses
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.settings.SettingsRepository
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.unit
import com.github.irdcat.workout.domain.workout.SheetNotation
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
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import com.github.irdcat.workout.domain.session.LastTime as LastTimeData
import com.github.irdcat.workout.domain.workout.SetGroup as PlanSetGroup
import com.github.irdcat.workout.domain.workout.WorkoutItem as PlanItem

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

private val LastTimeDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** "Last time" for the entry, with the weight an RPE-only target may take from it. */
private data class LastTimeEntry(val ui: LastTime, val weightKg: Double?)

/** What the screen shows plus what's needed to act on it. */
private data class SessionView(
    val ui: SessionUiState,
    val active: ActiveWorkout,
    /** The set the entry is for; null while a result waits for review and once the workout is done. */
    val next: NextSet?,
    /** Keys the user's entry edits: they belong to one set and reset for the next. */
    val entryKey: String,
    val autoStartRestTimer: Boolean,
)

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

    /** The rest last picked for the exercise up next, when it's one of unlimited sets. */
    private val savedSetRest: Flow<Int?> = activeWorkout
        .map { active -> active?.progress?.next?.exercise?.takeIf { it.isUnlimited }?.name }
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
        activeWithClock?.let { (active, elapsed) -> sessionView(active, elapsed, edits, settings, lastTime, savedRest, pauses) }
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
        when (event) {
            is SessionEvent.WeightChanged -> updateEntry { it.copy(weight = event.value.filterDecimal()) }
            is SessionEvent.AdjustWeight -> updateEntry { it.copy(weight = stepWeight(it.weight, it.weightStep, event.direction)) }
            is SessionEvent.WeightStepSelected -> updateEntry { it.copy(weightStep = event.step) }
            is SessionEvent.RepsChanged -> updateEntry { it.copy(reps = event.value.filter(Char::isDigit)) }
            is SessionEvent.AdjustReps -> updateEntry {
                val reps = (it.reps.toIntOrNull() ?: 0) + event.direction * it.repsStep
                it.copy(reps = reps.coerceAtLeast(0).toString())
            }
            is SessionEvent.RpeChanged -> updateEntry { it.copy(rpe = event.value.filterDecimal()) }
            is SessionEvent.AdjustRpe -> updateEntry { it.copy(rpe = stepRpe(it.rpe, it.rpeStep, event.direction)) }
            is SessionEvent.RpeStepSelected -> updateEntry { it.copy(rpeStep = event.step) }
            is SessionEvent.RestChanged -> updateEntry { entry -> entry.rest?.let { entry.copy(rest = event.value.filterRestTime()) } ?: entry }
            is SessionEvent.AdjustRest -> updateEntry { entry ->
                val rest = entry.rest ?: return@updateEntry entry
                val seconds = (parseRestTime(rest) ?: 0) + 15 * event.direction
                entry.copy(rest = formatRestTime(seconds.coerceAtLeast(0)))
            }
            SessionEvent.ToggleStopwatch -> view.value?.let { current ->
                // Only once it runs, from the first logged set.
                val exercise = current.next?.exercise
                if (exercise != null && (current.ui.exercise as? SessionExercise.OpenSets)?.stopwatch != null) {
                    stopwatchPauses.toggle(current.active.session, exercise.id, clock.instant())
                }
            }
            SessionEvent.LogSet -> logSet()
            SessionEvent.OpenResult -> view.value?.next?.let { next ->
                viewModelScope.launch { _navigation.send(SessionNavigation.EditResult(next.exercise.id)) }
            }
            SessionEvent.ReviewResult -> (view.value?.ui?.exercise as? SessionExercise.Review)?.let { review ->
                viewModelScope.launch { _navigation.send(SessionNavigation.ReviewResult(review.exerciseId)) }
            }
            SessionEvent.FinishExercise -> finishExercise()
            // Ending the session sends SessionEnded, which leaves the screen.
            SessionEvent.AbandonWorkout -> viewModelScope.launch { abandonSession() }
            SessionEvent.Back, SessionEvent.FinishWorkout, SessionEvent.OpenWarmUp -> Unit // Navigation; handled by the route.
        }
    }

    private fun logSet() {
        val current = view.value ?: return
        val next = current.next ?: return
        val entry = current.ui.entry ?: return
        val weight = entry.weight.toWeightKg() ?: return
        val reps = entry.reps.toIntOrNull() ?: return
        // Only unlimited sets pick their rest; null keeps the sheet's.
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

    // ---- Mapping ---------------------------------------------------------

    private fun sessionView(
        active: ActiveWorkout,
        elapsed: Duration,
        edits: Pair<String, SetEntry>?,
        settings: AppSettings,
        lastTime: LastTimeData?,
        savedSetRest: Int?,
        pauses: Map<Pair<ActiveSession, String>, StopwatchPause>,
    ): SessionView {
        val progress = active.progress
        val workout = active.workout
        val exercises = workout.exercises
        val next = progress.next ?: return reviewOrCompleteView(active, elapsed, settings)
        val item = workout.items[next.itemIndex]
        val member = next.exercise
        val logged = progress.loggedSets(member)

        val exercise = when {
            item is PlanItem.Superset -> supersetExercise(active, next.itemIndex + 1, item, next)
            member.isOpenSets -> SessionExercise.OpenSets(
                position = exercises.indexOf(member) + 1,
                exerciseCount = exercises.size,
                name = member.name,
                repsPerSet = next.group.reps.repsText(),
                startWeight = (next.group.load as? Load.Weight)?.let { next.group.load.label() },
                tempo = exerciseTempo(member.setGroups),
                restSeconds = member.restSeconds(),
                // Unlimited sets have no time limit to watch.
                stopwatch = logged.firstOrNull()?.takeUnless { member.isUnlimited }?.let { first ->
                    val stopwatch = pauses[active.session to member.id] ?: StopwatchPause()
                    Stopwatch(
                        elapsed = formatElapsed(stopwatch.elapsed(first.loggedAt, clock.instant())),
                        limit = member.timeLimit()?.let(::formatElapsed),
                        running = !stopwatch.isPaused,
                    )
                },
                done = logged.doneRows(member),
                set = next.setNumber,
                unlimited = member.isUnlimited,
            )
            else -> SessionExercise.Planned(
                position = exercises.indexOf(member) + 1,
                exerciseCount = exercises.size,
                name = member.name,
                tempo = exerciseTempo(member.setGroups),
                restSeconds = next.group.restSeconds ?: member.restSeconds(),
                done = logged.doneRows(member),
                set = next.setNumber,
                setCount = next.plannedCount ?: next.setNumber,
                target = next.group.targetText(),
                upcoming = progress.plannedSets(member).drop(next.setNumber).mapIndexed { index, group ->
                    SetRow(
                        label = "Set ${next.setNumber + 1 + index}",
                        value = group.rowValue(),
                        note = group.rowNote() ?: "after this".takeIf { index == 0 },
                    )
                },
            )
        }

        val entryKey = "${member.id}#${next.setNumber}"
        val entry = edits?.takeIf { it.first == entryKey }?.second
            ?: next.group.toEntry(settings, lastTime?.let { lastTimeOf(it, member, next.setNumber) }).copy(
                rest = if (member.isUnlimited) {
                    formatRestTime(savedSetRest ?: next.group.restSeconds ?: DEFAULT_SET_REST_SECONDS)
                } else {
                    null
                },
            )
        val currentIds = item.exercises.map { it.id }.toSet()
        return SessionView(
            ui = SessionUiState(
                workoutName = workout.code,
                weekRange = active.week.range,
                elapsed = if (settings.measureWorkoutTime) formatElapsed(elapsed) else null,
                progress = exercises.map {
                    when {
                        it.id in currentIds -> ProgressSegment.Current
                        progress.isComplete(it) -> ProgressSegment.Done
                        else -> ProgressSegment.Pending
                    }
                },
                exercise = exercise,
                entry = entry,
                warmUpSets = progress.warmUpSets(member).size,
            ),
            active = active,
            next = next,
            entryKey = entryKey,
            autoStartRestTimer = settings.autoStartRestTimer,
        )
    }

    /** Nothing to log: a result to review before going on, or the whole workout done. */
    private fun reviewOrCompleteView(active: ActiveWorkout, elapsed: Duration, settings: AppSettings): SessionView {
        val progress = active.progress
        val exercises = active.workout.exercises
        val review = progress.toReview
        val reviewItem = review?.let { progress.itemOf(it) }
        val exercise = if (review != null && reviewItem != null) {
            val (index, item) = reviewItem
            val tag = if (item is PlanItem.Superset) "${supersetMemberTag(index + 1, item.exercises.indexOf(review))} · " else ""
            SessionExercise.Review(
                position = exercises.indexOf(review) + 1,
                exerciseCount = exercises.size,
                exerciseId = review.id,
                name = tag + review.name,
                done = progress.loggedSets(review).doneRows(review),
            )
        } else {
            SessionExercise.Complete
        }
        val currentIds = reviewItem?.value?.exercises?.map { it.id }.orEmpty().toSet()
        return SessionView(
            ui = SessionUiState(
                workoutName = active.workout.code,
                weekRange = active.week.range,
                elapsed = if (settings.measureWorkoutTime) formatElapsed(elapsed) else null,
                progress = exercises.map {
                    when {
                        it.id in currentIds -> ProgressSegment.Current
                        progress.isComplete(it) -> ProgressSegment.Done
                        else -> ProgressSegment.Pending
                    }
                },
                exercise = exercise,
                entry = null,
            ),
            active = active,
            next = null,
            entryKey = "",
            autoStartRestTimer = settings.autoStartRestTimer,
        )
    }

    private fun supersetExercise(
        active: ActiveWorkout,
        itemNumber: Int,
        superset: PlanItem.Superset,
        next: NextSet,
    ): SessionExercise.Superset {
        val progress = active.progress
        fun tag(exercise: Exercise) = supersetMemberTag(itemNumber, superset.exercises.indexOf(exercise))
        val slots = progress.slots(superset)
        val currentSlot = slots.indexOfFirst { it.exercise.id == next.exercise.id && it.round == next.round }
        val following = slots.drop(currentSlot + 1).firstOrNull { !it.logged && !progress.isComplete(it.exercise) }
        val memberIds = superset.exercises.map { it.id }.toSet()
        return SessionExercise.Superset(
            index = itemNumber,
            rounds = slots.groupBy { it.round }.toSortedMap().values.map { round ->
                round.map { slot ->
                    val status = when {
                        slot.logged -> ProgressSegment.Done
                        slot.exercise.id == next.exercise.id && slot.round == next.round -> ProgressSegment.Current
                        else -> ProgressSegment.Pending
                    }
                    SupersetSlot(tag(slot.exercise), status)
                }
            },
            current = SupersetMember(
                tag = tag(next.exercise),
                name = next.exercise.name,
                round = next.round ?: next.setNumber,
                target = next.group.targetText(),
                restSeconds = next.group.restSeconds,
            ),
            done = active.log.sets.filter { it.exerciseId in memberIds && !it.warmUp }.map { set ->
                val member = superset.exercises.first { it.id == set.exerciseId }
                SetRow(
                    label = "R${progress.setNumberOf(set)}",
                    value = set.summaryText(member.repsUnitOf(set.setGroupId)),
                    note = set.rpeNote(),
                    tag = tag(member),
                )
            },
            next = following?.let { slot ->
                val group = progress.plannedSets(slot.exercise).getOrElse(slot.round - 1) { slot.exercise.setGroups.last() }
                SetRow(label = "R${slot.round}", value = group.rowValue(), note = group.rowNote(), tag = tag(slot.exercise))
            },
        )
    }

    private fun Exercise.restSeconds(): Int? = setGroups.firstNotNullOfOrNull { it.restSeconds }

    private fun List<LoggedSet>.doneRows(exercise: Exercise): List<SetRow> = mapIndexed { index, set ->
        SetRow(label = "Set ${index + 1}", value = set.summaryText(exercise.repsUnitOf(set.setGroupId)), note = set.rpeNote())
    }

    /** A limit written in the name or a row's note, e.g. "Czas: 10:00 od pierwszej serii". */
    private fun Exercise.timeLimit(): Duration? =
        (listOf(name) + setGroups.mapNotNull { it.note }).firstNotNullOfOrNull { SheetNotation.timeLimit(it) }

    /**
     * The same exercise last time, from what the app logged then (the matching set, or
     * the last one) and the sheet's result. The sheet has no dates or sets, so a result
     * that only comes from the sheet shows without them; null when there's nothing at all.
     */
    private fun lastTimeOf(lastTime: LastTimeData, exercise: Exercise, setNumber: Int): LastTimeEntry? {
        val sets = lastTime.setsLike(exercise)
        val set = sets.getOrNull(setNumber - 1) ?: sets.lastOrNull()
        val note = lastTime.resultLike(exercise)
        if (set == null && note == null) return null
        return LastTimeEntry(
            ui = LastTime(
                date = lastTime.session?.takeIf { set != null }?.session?.startedAt?.atZone(clock.zone)?.format(LastTimeDate),
                // That week's rows are another workout's: the unit is the exercise's now.
                summary = set?.summaryText(exercise.repsUnit),
                note = note,
            ),
            weightKg = set?.addedWeightKg,
        )
    }

    /**
     * Prefilled from the sheet's target; an RPE-only target can take last time's weight.
     * Without a weight it starts at 0, which is logged as bodyweight. The RPE is the
     * target's when it has one, else [DefaultRpe].
     */
    private fun PlanSetGroup.toEntry(settings: AppSettings, lastTime: LastTimeEntry?): SetEntry {
        val weight = when (val load = load) {
            is Load.Weight -> load.kg.kg()
            is Load.Implements -> load.kgEach.kg()
            is Load.Range -> load.minKg.kg()
            is Load.Choice -> load.kg.first().kg()
            is Load.Rpe -> lastTime?.weightKg?.takeIf { settings.prefillWeightFromLastTime }?.kg() ?: "0"
            else -> "0"
        }
        val reps = when (val reps = reps) {
            is Reps.Count -> reps.count
            is Reps.Range -> reps.min
            is Reps.UpTo -> reps.count
            is Reps.PerSide -> reps.count
            is Reps.Steps -> reps.count
            is Reps.Lengths -> reps.count
            is Reps.Duration -> reps.minSeconds
            is Reps.Other -> null
        }
        return SetEntry(
            weightLabel = if (load is Load.Implements) "Each DB (kg)" else "Weight (kg)",
            weight = weight,
            weightStep = settings.defaultWeightStepKg.kg(),
            repsLabel = this.reps.unit.fieldLabel,
            reps = reps?.toString().orEmpty(),
            repsStep = this.reps.unit.step,
            rpe = ((load as? Load.Rpe)?.value?.toBigDecimal() ?: DefaultRpe).stripTrailingZeros().toPlainString(),
            lastTime = lastTime?.ui,
        )
    }
}

/** The entered weight in kg; empty counts as 0 (bodyweight). null when it isn't a number. */
internal fun String.toWeightKg(): Double? = ifEmpty { "0" }.toDoubleOrNull()

/** RPE a set starts with when the sheet gives none, and a tap starts from when the field is empty. */
private val DefaultRpe = BigDecimal("8")

/** [rpe] moved [direction] steps of [step] within 1–10; from an empty field the first tap gives 8. */
internal fun stepRpe(rpe: String, step: String, direction: Int): String {
    val current = rpe.toBigDecimalOrNull() ?: return DefaultRpe.toPlainString()
    val delta = (step.toBigDecimalOrNull() ?: BigDecimal("0.5")) * direction.toBigDecimal()
    return (current + delta).max(BigDecimal.ONE).min(BigDecimal.TEN).stripTrailingZeros().toPlainString()
}

/** [weight] moved [direction] (-1 or +1) steps of [step], never below zero. */
internal fun stepWeight(weight: String, step: String, direction: Int): String {
    val current = weight.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val delta = (step.toBigDecimalOrNull() ?: BigDecimal.ONE) * direction.toBigDecimal()
    return (current + delta).max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
}

/** Digits and a single decimal separator; a comma is read as a dot. */
internal fun String.filterDecimal(): String {
    var seenSeparator = false
    return buildString {
        for (c in this@filterDecimal) {
            when {
                c.isDigit() -> append(c)
                (c == '.' || c == ',') && !seenSeparator -> {
                    seenSeparator = true
                    append('.')
                }
            }
        }
    }
}
