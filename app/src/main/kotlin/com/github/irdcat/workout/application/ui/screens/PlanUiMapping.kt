package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.application.ui.components.Tempo
import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.RepsUnit
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.TrainingWeek
import com.github.irdcat.workout.domain.workout.Workout
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.github.irdcat.workout.domain.workout.SetGroup as PlanSetGroup
import com.github.irdcat.workout.domain.workout.WorkoutItem as PlanItem

// Domain plan → screen state. The UI copy is English, so dates use English names.

private val UiLocale: Locale = Locale.ENGLISH
private val DayMonth = DateTimeFormatter.ofPattern("d MMM", UiLocale)
private val Day = DateTimeFormatter.ofPattern("d", UiLocale)
private val Time = DateTimeFormatter.ofPattern("H:mm", UiLocale)

/** "1 – 7 Jun", or "29 Jun – 5 Jul" across months. */
fun formatWeekRange(start: LocalDate, end: LocalDate): String =
    if (YearMonth.from(start) == YearMonth.from(end)) {
        "${start.format(Day)} – ${end.format(DayMonth)}"
    } else {
        "${start.format(DayMonth)} – ${end.format(DayMonth)}"
    }

val TrainingWeek.range: String get() = formatWeekRange(start, end)

/** "19 Oct". */
fun formatDayMonth(date: LocalDate): String = date.format(DayMonth)

/** "June 2026". */
fun formatMonthYear(month: YearMonth): String = "${monthName(month)} ${month.year}"

/** "June". */
fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, UiLocale)

/** "Jun". */
fun shortMonthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT, UiLocale)

/** "14:32" in the phone's time zone. */
fun formatTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(Time)

/** "Today 14:32", "Yesterday 9:05" or "12 Oct 14:32"; "Never" without a sync. */
fun formatSyncedAt(instant: Instant?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    if (instant == null) return "Never"
    val date = instant.atZone(zone).toLocalDate()
    val day = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> formatDayMonth(date)
    }
    return "$day ${formatTime(instant, zone)}"
}

fun TrainingWeek.timing(today: LocalDate): WeekTiming = when {
    today in this -> WeekTiming.Current
    end.isBefore(today) -> WeekTiming.Past
    else -> WeekTiming.Upcoming
}

/** The sheet only says whether results are filled in, so there is no completion day. */
fun Workout.uiStatus(): WorkoutStatus = when {
    isCompleted -> WorkoutStatus.Completed(day = null)
    isStarted -> WorkoutStatus.InProgress
    else -> WorkoutStatus.NotStarted
}

fun Workout.toSummary(): WorkoutSummary = WorkoutSummary(
    id = id,
    name = code,
    title = title(),
    exerciseCount = exercises.size,
    supersetCount = items.count { it is PlanItem.Superset },
    status = uiStatus(),
)

fun Workout.toDetailItems(): List<WorkoutItem> = items.mapIndexed { position, item ->
    // Number items by position so a gap in the sheet's numbering doesn't show.
    val index = position + 1
    when (item) {
        is PlanItem.Single -> WorkoutItem.Single(index, item.exercise.toDetail())
        is PlanItem.Superset -> WorkoutItem.Superset(index, item.exercises.map { it.toDetail() })
    }
}

fun Exercise.toDetail(): ExerciseDetail = ExerciseDetail(
    id = id,
    name = name,
    setGroups = setGroups.map { it.toUi() },
    restSeconds = setGroups.firstNotNullOfOrNull { it.restSeconds },
    tempo = exerciseTempo(setGroups),
    result = setGroups.mapNotNull { it.result }.joinToString(" · ").ifEmpty { null },
    progress = if (isDone) ExerciseProgress.Done else ExerciseProgress.Pending,
)

private fun PlanSetGroup.toUi(): SetGroup = SetGroup(
    sets = (sets as? SetCount.Fixed)?.count,
    reps = reps.label(),
    load = load.label(),
    unlimited = sets == SetCount.Unlimited,
)

/**
 * One tempo for the exercise: shown as is when every group shares it, or with
 * "last group"/"last N groups" when only the trailing groups have it.
 */
fun exerciseTempo(groups: List<PlanSetGroup>): Tempo? {
    val tempos = groups.map { it.tempo?.phases }
    val first = tempos.firstNotNullOfOrNull { it } ?: return null
    if (tempos.all { it == first }) return Tempo(first)
    val trailing = tempos.takeLastWhile { it == first }.size
    val rest = tempos.dropLast(trailing)
    return when {
        trailing > 0 && rest.all { it == null } ->
            Tempo(first, appliesTo = if (trailing == 1) "last group" else "last $trailing groups")
        else -> Tempo(first, appliesTo = "some groups")
    }
}

fun Reps.label(): String = when (this) {
    is Reps.Count -> "$count"
    is Reps.Range -> "$min–$max"
    is Reps.Duration -> if (minSeconds == maxSeconds) "$minSeconds s" else "$minSeconds–$maxSeconds s"
    is Reps.PerSide -> "$count/side"
    is Reps.Steps -> "$count steps"
    is Reps.Lengths -> "$count lengths"
    is Reps.UpTo -> "≤$count"
    is Reps.Other -> raw
}

fun Load.label(): String = when (this) {
    is Load.Weight -> "${kg.kg()} kg"
    is Load.Implements -> "$count×${kgEach.kg()} kg"
    is Load.Range -> "${minKg.kg()}–${maxKg.kg()} kg"
    is Load.Choice -> kg.joinToString("/") { it.kg() } + " kg"
    is Load.Rpe -> "RPE ${value.kg()}"
    Load.Bodyweight -> "BW"
    is Load.Other -> raw
}

/** 60.0 → "60", 92.5 → "92.5". */
fun Double.kg(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

// ---- Session labels ------------------------------------------------------

/** "5 reps", "8–10 reps"; other kinds as they read, e.g. "25 s" or "10/side". */
fun Reps.repsText(): String = when (this) {
    is Reps.Count, is Reps.Range, is Reps.UpTo -> "${label()} reps"
    else -> label()
}

/** A planned set: "92.5×5" for a weight, else "8 reps". */
fun PlanSetGroup.setLabel(): String = when (val load = load) {
    is Load.Weight -> "${load.kg.kg()}×${reps.label()}"
    is Load.Implements -> "${load.kgEach.kg()}×${reps.label()}"
    else -> reps.repsText()
}

/** A planned set in a list: "92.5 kg × 5" for a weight, else the reps, e.g. "8 reps". */
fun PlanSetGroup.rowValue(): String = when (val load = load) {
    is Load.Weight -> "${load.kg.kg()} kg × ${reps.label()}"
    is Load.Implements -> "${load.kgEach.kg()} kg × ${reps.label()}"
    is Load.Range, is Load.Choice -> "${load.label()} × ${reps.label()}"
    else -> reps.repsText()
}

/** What goes beside [rowValue] when the load isn't a weight, e.g. "RPE 8.5". */
fun PlanSetGroup.rowNote(): String? = when (val load = load) {
    is Load.Rpe -> load.label()
    is Load.Other -> load.raw
    else -> null
}

/** "RPE 8" for a set logged with one. */
fun LoggedSet.rpeNote(): String? = rpe?.let { "RPE ${it.kg()}" }

/** "5 reps × 92.5 kg", "8 reps × RPE 8.5", or just the reps for bodyweight. */
fun PlanSetGroup.targetText(): String =
    if (load == Load.Bodyweight) reps.repsText() else "${reps.repsText()} × ${load.label()}"

/** The weight lifted; null for bodyweight, which older sets may have stored as 0. */
val LoggedSet.addedWeightKg: Double? get() = weightKg?.takeIf { it > 0 }

/** A count on its own: "6 reps", "10/side", "30 s", "50 steps", "4 lengths". */
fun RepsUnit.amount(count: Int): String = when (this) {
    RepsUnit.Reps -> "$count reps"
    RepsUnit.RepsPerSide -> "$count/side"
    RepsUnit.Seconds -> "$count s"
    RepsUnit.Steps -> "$count steps"
    RepsUnit.Lengths -> "$count lengths"
}

/** A count after a weight: plain for reps ("60×6"), else with its unit ("10×30 s"). */
private fun RepsUnit.afterWeight(count: Int): String = if (this == RepsUnit.Reps) "$count" else amount(count)

/** What the set card's count field is called. */
val RepsUnit.fieldLabel: String
    get() = when (this) {
        RepsUnit.Reps -> "Reps"
        RepsUnit.RepsPerSide -> "Reps per side"
        RepsUnit.Seconds -> "Seconds"
        RepsUnit.Steps -> "Steps"
        RepsUnit.Lengths -> "Lengths"
    }

/** How much a −/+ tap changes the count: 5 s for a hold, else one. */
val RepsUnit.step: Int get() = if (this == RepsUnit.Seconds) 5 else 1

/** A logged set for chips and results: "60×6", "6 reps" for bodyweight, "30 s" for a hold. */
fun LoggedSet.chipLabel(unit: RepsUnit = RepsUnit.Reps): String {
    val kg = addedWeightKg
    return when {
        kg != null && reps != null -> "${kg.kg()}×${unit.afterWeight(reps)}"
        kg != null -> "${kg.kg()} kg"
        reps != null -> unit.amount(reps)
        else -> "—"
    }
}

/** A logged set in words: "90 kg × 5", "5 reps", "10 kg × 30 s" or "90 kg". */
fun LoggedSet.summaryText(unit: RepsUnit = RepsUnit.Reps): String {
    val kg = addedWeightKg
    return when {
        kg != null && reps != null -> "${kg.kg()} kg × ${unit.afterWeight(reps)}"
        kg != null -> "${kg.kg()} kg"
        reps != null -> unit.amount(reps)
        else -> "no weight or reps"
    }
}

/** Tag of a superset member, e.g. "5b" for the second member of item 5. */
fun supersetMemberTag(itemNumber: Int, memberIndex: Int): String = "$itemNumber${'a' + memberIndex}"
