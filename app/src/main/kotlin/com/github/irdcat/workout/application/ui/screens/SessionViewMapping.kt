package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.session.ActiveSession
import com.github.irdcat.workout.domain.session.DEFAULT_SET_REST_SECONDS
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.session.NextSet
import com.github.irdcat.workout.domain.session.SessionProgress
import com.github.irdcat.workout.domain.session.StopwatchPause
import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.SheetNotation
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.github.irdcat.workout.domain.session.LastTime as LastTimeData
import com.github.irdcat.workout.domain.workout.WorkoutItem as PlanItem
import com.github.irdcat.workout.domain.session.itemOf

// The active workout → what the session screen shows.

private val LastTimeDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** "Last time" for the entry, with the weight an RPE-only target may take from it. */
internal data class LastTimeEntry(val ui: LastTime, val weightKg: Double?)

/** What the screen shows plus what's needed to act on it. */
internal data class SessionView(
    val ui: SessionUiState,
    val active: ActiveWorkout,
    /** The set the entry is for; null while a result waits for review and once the workout is done. */
    val next: NextSet?,
    /** Keys the user's entry edits: they belong to one set and reset for the next. */
    val entryKey: String,
    val autoStartRestTimer: Boolean,
)

internal fun sessionView(
    active: ActiveWorkout,
    elapsed: Duration,
    edits: Pair<String, SetEntry>?,
    settings: AppSettings,
    lastTime: LastTimeData?,
    savedSetRest: Int?,
    pauses: Map<Pair<ActiveSession, String>, StopwatchPause>,
    clock: Clock,
): SessionView {
    val progress = active.progress
    val next = progress.next ?: return reviewOrCompleteView(active, elapsed, settings)
    val item = active.workout.items[next.itemIndex]
    val member = next.exercise
    val exercise = when {
        item is PlanItem.Superset -> supersetExercise(active, next.itemIndex + 1, item, next)
        member.isOpenSets -> openSetsExercise(active, next, pauses, clock)
        else -> plannedExercise(active, next)
    }
    val entryKey = "${member.id}#${next.setNumber}"
    val entry = edits?.takeIf { it.first == entryKey }?.second
        ?: next.group.toEntry(settings, lastTime?.let { lastTimeOf(it, member, next.setNumber, clock) }).copy(
            rest = if (member.picksRest) {
                formatRestTime(savedSetRest ?: next.group.restSeconds ?: DEFAULT_SET_REST_SECONDS)
            } else {
                null
            },
        )
    return SessionView(
        ui = SessionUiState(
            workoutName = active.workout.code,
            weekRange = active.week.range,
            elapsed = if (settings.measureWorkoutTime) formatElapsed(elapsed) else null,
            progress = progressSegments(progress, active.workout.exercises, current = item.exercises),
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

/** One segment per exercise of the workout: done, on now ([current]) or still to come. */
private fun progressSegments(progress: SessionProgress, exercises: List<Exercise>, current: List<Exercise>): List<ProgressSegment> {
    val currentIds = current.map { it.id }.toSet()
    return exercises.map {
        when {
            it.id in currentIds -> ProgressSegment.Current
            progress.isComplete(it) -> ProgressSegment.Done
            else -> ProgressSegment.Pending
        }
    }
}

private fun openSetsExercise(
    active: ActiveWorkout,
    next: NextSet,
    pauses: Map<Pair<ActiveSession, String>, StopwatchPause>,
    clock: Clock,
): SessionExercise.OpenSets {
    val exercises = active.workout.exercises
    val member = next.exercise
    val logged = active.progress.loggedSets(member)
    return SessionExercise.OpenSets(
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
        picksRest = member.picksRest,
    )
}

private fun plannedExercise(active: ActiveWorkout, next: NextSet): SessionExercise.Planned {
    val exercises = active.workout.exercises
    val member = next.exercise
    return SessionExercise.Planned(
        position = exercises.indexOf(member) + 1,
        exerciseCount = exercises.size,
        name = member.name,
        tempo = exerciseTempo(member.setGroups),
        restSeconds = next.group.restSeconds ?: member.restSeconds(),
        done = active.progress.loggedSets(member).doneRows(member),
        set = next.setNumber,
        setCount = next.plannedCount ?: next.setNumber,
        target = next.group.targetText(),
        upcoming = active.progress.plannedSets(member).drop(next.setNumber).mapIndexed { index, group ->
            SetRow(
                label = "Set ${next.setNumber + 1 + index}",
                value = group.rowValue(),
                note = group.rowNote() ?: "after this".takeIf { index == 0 },
            )
        },
    )
}

/** Nothing to log: a result to review before going on, or the whole workout done. */
private fun reviewOrCompleteView(active: ActiveWorkout, elapsed: Duration, settings: AppSettings): SessionView {
    val progress = active.progress
    val exercises = active.workout.exercises
    val review = progress.toReview
    val reviewItem = review?.let { progress.workout.itemOf(it) }
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
    return SessionView(
        ui = SessionUiState(
            workoutName = active.workout.code,
            weekRange = active.week.range,
            elapsed = if (settings.measureWorkoutTime) formatElapsed(elapsed) else null,
            progress = progressSegments(progress, exercises, current = reviewItem?.value?.exercises.orEmpty()),
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
private fun lastTimeOf(lastTime: LastTimeData, exercise: Exercise, setNumber: Int, clock: Clock): LastTimeEntry? {
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
