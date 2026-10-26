package com.github.irdcat.workout.domain.analysis

import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.RepsUnit
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.SetGroup
import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.domain.workout.TrainingWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** An exercise as it's known across weeks: by name, however it's spaced or capitalised. */
fun exerciseKey(name: String): String = name.trim().lowercase().replace(Regex("""\s+"""), " ")

/** What volume is counted in: kilograms lifted, or for an exercise without weights, its own unit. */
sealed interface VolumeUnit {
    data object Kilograms : VolumeUnit
    data class Count(val unit: RepsUnit) : VolumeUnit
}

/** One week of an exercise: what the sheet planned and what was logged on this device. */
data class WeekFigures(
    /** Monday of the week. */
    val weekStart: LocalDate,
    val plannedVolume: Double? = null,
    val actualVolume: Double? = null,
    /** Heaviest set, in kg. */
    val plannedTop: Double? = null,
    val actualTop: Double? = null,
    val plannedRpe: Double? = null,
    /** Average of the RPEs logged, to the nearest half. */
    val actualRpe: Double? = null,
)

/** Everything known about one exercise, week by week. */
data class ExerciseHistory(
    /** As the sheet spells it most recently. */
    val name: String,
    val volumeUnit: VolumeUnit,
    /** Weeks it's in the sheet. */
    val weeksPlanned: Int,
    /** Weeks it was logged on this device. */
    val weeksLogged: Int,
    /** Only weeks with anything planned or logged, oldest first. */
    val weeks: List<WeekFigures>,
    /** A rep range in the plan, e.g. "8–15 s", counted at its upper end; null when there's none. */
    val estimatedRange: String?,
    /** Week of the first set logged on this device. */
    val firstLogged: LocalDate?,
    /** Neither planned nor logged with a weight, ever. */
    val weightless: Boolean,
    /** Never an RPE, in the sheet or the logs. */
    val noRpe: Boolean,
)

/** An exercise to pick: its name and how much there is to see. */
data class ExerciseSummary(val key: String, val name: String, val weeksPlanned: Int, val weeksLogged: Int)

/**
 * The plan's exercises and the sets logged against them, joined: each logged set counts
 * in the week of the plan it was logged against. Sets whose exercise left the plan are dropped.
 */
class ExerciseHistories(months: List<TrainingMonth>, sets: List<LoggedSet>) {

    private data class Occurrence(val week: TrainingWeek, val exercise: Exercise)

    private val occurrences: Map<String, List<Occurrence>> = months
        .flatMap { it.weeks }
        .sortedBy { it.start }
        .flatMap { week -> week.workouts.flatMap { workout -> workout.exercises.map { Occurrence(week, it) } } }
        .groupBy { exerciseKey(it.exercise.name) }

    private val byExerciseId: Map<String, Occurrence> =
        occurrences.values.flatten().associateBy { it.exercise.id }

    private val logged: Map<String, List<Pair<Occurrence, LoggedSet>>> = sets
        .filterNot { it.warmUp }
        .mapNotNull { set -> byExerciseId[set.exerciseId]?.let { it to set } }
        .groupBy { (occurrence, _) -> exerciseKey(occurrence.exercise.name) }

    /** Every exercise, the most trained first. */
    val catalog: List<ExerciseSummary> = occurrences.map { (key, list) ->
        ExerciseSummary(
            key = key,
            name = list.last().exercise.name,
            weeksPlanned = list.map { it.week.start }.distinct().size,
            weeksLogged = logged[key].orEmpty().map { it.first.week.start }.distinct().size,
        )
    }.sortedWith(compareByDescending<ExerciseSummary> { it.weeksLogged }.thenByDescending { it.weeksPlanned }.thenBy { it.name })

    /** The key of the exercise logged last, if any. */
    val lastLogged: String? = sets.filterNot { it.warmUp }.maxByOrNull { it.loggedAt }
        ?.let { byExerciseId[it.exerciseId] }?.let { exerciseKey(it.exercise.name) }

    fun history(key: String): ExerciseHistory? {
        val planned = occurrences[key] ?: return null
        val sets = logged[key].orEmpty()
        val groups = planned.flatMap { it.exercise.setGroups }
        val weighted = groups.any { it.load.topKg() != null } || sets.any { (it.second.weightKg ?: 0.0) > 0 }
        val unit = if (weighted) VolumeUnit.Kilograms else VolumeUnit.Count(planned.last().exercise.repsUnit)

        val plannedByWeek = planned.groupBy { it.week.start }
        val loggedByWeek = sets.groupBy({ it.first.week.start }, { it.second })
        val weeks = (plannedByWeek.keys + loggedByWeek.keys).sorted().map { start ->
            val plannedGroups = plannedByWeek[start].orEmpty().flatMap { it.exercise.setGroups }
            val weekSets = loggedByWeek[start].orEmpty()
            WeekFigures(
                weekStart = start,
                plannedVolume = plannedGroups.mapNotNull { it.plannedVolume(weighted) }.takeIf { it.isNotEmpty() }?.sum(),
                actualVolume = weekSets.takeIf { it.isNotEmpty() }?.sumOf { it.volume(weighted) },
                plannedTop = plannedGroups.mapNotNull { it.load.topKg() }.maxOrNull(),
                actualTop = weekSets.mapNotNull { set -> set.weightKg?.takeIf { it > 0 } }.maxOrNull(),
                plannedRpe = plannedGroups.mapNotNull { (it.load as? Load.Rpe)?.value }.maxOrNull(),
                actualRpe = weekSets.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average()?.let { (it * 2).roundToInt() / 2.0 },
            )
        }
        return ExerciseHistory(
            name = planned.last().exercise.name,
            volumeUnit = unit,
            weeksPlanned = plannedByWeek.size,
            weeksLogged = loggedByWeek.size,
            weeks = weeks,
            estimatedRange = groups.firstOrNull { it.reps.isRange() }?.reps?.let(::rangeText),
            firstLogged = loggedByWeek.keys.minOrNull(),
            weightless = !weighted,
            noRpe = weeks.none { it.plannedRpe != null || it.actualRpe != null },
        )
    }

    /** Sets × reps (upper end of a range) × kg, or without weights just sets × reps; null when unknown. */
    private fun SetGroup.plannedVolume(weighted: Boolean): Double? {
        val count = (sets as? SetCount.Fixed)?.count ?: return null
        val reps = reps.upperCount() ?: return null
        return if (weighted) load.topKg()?.let { count * reps * it } else count * reps.toDouble()
    }

    private fun LoggedSet.volume(weighted: Boolean): Double {
        val count = (reps ?: 0).toDouble()
        return if (weighted) count * (weightKg?.takeIf { it > 0 } ?: 0.0) else count
    }

    private fun Reps.upperCount(): Int? = when (this) {
        is Reps.Count -> count
        is Reps.Range -> max
        is Reps.UpTo -> count
        is Reps.Duration -> maxSeconds
        is Reps.PerSide -> count
        is Reps.Steps -> count
        is Reps.Lengths -> count
        is Reps.Other -> null
    }

    private fun Reps.isRange(): Boolean =
        this is Reps.Range || (this is Reps.Duration && minSeconds != maxSeconds)

    private fun rangeText(reps: Reps): String = when (reps) {
        is Reps.Range -> "${reps.min}–${reps.max} reps"
        is Reps.Duration -> "${reps.minSeconds}–${reps.maxSeconds} s"
        else -> ""
    }
}

/** The heaviest weight a load asks for, per implement; null when it isn't a weight. */
fun Load.topKg(): Double? = when (this) {
    is Load.Weight -> kg
    is Load.Implements -> kgEach
    is Load.Range -> maxKg
    is Load.Choice -> kg.maxOrNull()
    is Load.Rpe, Load.Bodyweight, is Load.Other -> null
}
