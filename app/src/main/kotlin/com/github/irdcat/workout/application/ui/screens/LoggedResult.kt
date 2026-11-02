package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.workout.Exercise
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.RepsUnit
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.unit
import com.github.irdcat.workout.domain.workout.SetGroup as PlanSetGroup

// What the app writes to WYNIK from the logged sets: only what the plan doesn't already say.

/**
 * The result of [sets] (working sets, in order) without its RPE, row by row, joined with "; ".
 * A row says only what the plan doesn't: the weight when the row gives none (e.g. "RPE 8") or
 * a set's differed, the reps when a set's differed, each set when their number did. Otherwise
 * nothing; a row with no sets is "skipped". Empty when everything went as planned: the RPE
 * says the rest. Values that vary are listed per set ("5, 5, 3 reps"), else given once ("100 kg").
 */
fun Exercise.loggedResultBody(sets: List<LoggedSet>): String {
    if (sets.isEmpty()) return ""
    val known = setGroups.map { it.id }.toSet()
    // Sets of a row no longer in the plan say everything about themselves.
    val orphans = sets.filter { it.setGroupId !in known }
    // With sets the plan can't place, a row without any isn't known to be skipped.
    val parts = setGroups.mapNotNull { group -> group.describe(sets.filter { it.setGroupId == group.id }, orphans.isEmpty()) } +
        listOfNotNull(orphans.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.chipLabel(repsUnitOf(it.setGroupId)) })
    return parts.joinToString("; ")
}

/** Every set as logged, e.g. "60×6, 75×6, 90×5": for when the result would say nothing else. */
fun Exercise.allSetsText(sets: List<LoggedSet>): String = sets.joinToString(", ") { it.chipLabel(repsUnitOf(it.setGroupId)) }

private fun PlanSetGroup.describe(logged: List<LoggedSet>, toldSkipped: Boolean): String? {
    if (logged.isEmpty()) return "skipped".takeIf { toldSkipped && (sets as? SetCount.Fixed)?.count?.let { it > 0 } == true }
    val countAsPlanned = (sets as? SetCount.Fixed)?.count == logged.size
    val showWeight = logged.any { !weightAsPlanned(it) }
    // A different number of sets (or open ones) is told by listing them: by their reps if nothing else.
    val showReps = logged.any { !repsAsPlanned(it) } || (!countAsPlanned && !showWeight)
    if (!showWeight && !showReps) return null
    val unit = reps.unit
    val shown = logged.map { set -> Shown(set.addedWeightKg.takeIf { showWeight }, set.reps.takeIf { showReps }) }
    // Told once when every set was the same, unless their number is what differed.
    val listed = if (countAsPlanned && shown.distinct().size == 1) shown.take(1) else shown
    return when {
        showWeight && showReps -> listed.joinToString(", ") { it.label(unit) }
        showWeight -> weightsText(listed.map { it.kg })
        else -> repsText(listed.map { it.reps }, unit)
    }
}

/** What's told of one set: "100×5", "100 kg" or "5 reps". */
private data class Shown(val kg: Double?, val reps: Int?) {
    fun label(unit: RepsUnit): String = when {
        kg != null && reps != null -> "${kg.kg()}×${if (unit == RepsUnit.Reps) "$reps" else unit.amount(reps)}"
        kg != null -> "${kg.kg()} kg"
        reps != null -> unit.amount(reps)
        else -> "—"
    }
}

/** "100, 102.5 kg"; "BW" for a set without weight. */
private fun weightsText(weights: List<Double?>): String =
    if (weights.all { it != null }) {
        weights.joinToString(", ") { it!!.kg() } + " kg"
    } else {
        weights.joinToString(", ") { kg -> kg?.let { "${it.kg()} kg" } ?: "BW" }
    }

/** "5, 5, 3 reps", "30, 25 s": the unit once, at the end. */
private fun repsText(counts: List<Int?>, unit: RepsUnit): String =
    counts.dropLast(1).joinToString("") { "${it ?: "—"}, " } + (counts.last()?.let(unit::amount) ?: "—")

/** The set's weight is the one planned: a fixed weight, per implement or in all, or none for bodyweight. */
private fun PlanSetGroup.weightAsPlanned(set: LoggedSet): Boolean = when (val load = load) {
    is Load.Weight -> set.addedWeightKg == load.kg
    is Load.Implements -> set.addedWeightKg == load.kgEach || set.addedWeightKg == load.kgEach * load.count
    Load.Bodyweight -> set.addedWeightKg == null
    // An RPE, a range or a choice leaves the weight to the day: it's always told.
    is Load.Rpe, is Load.Range, is Load.Choice, is Load.Other -> set.addedWeightKg == null
}

/** The set's count is the one planned; a range or "up to" leaves it open, so it's always told. */
private fun PlanSetGroup.repsAsPlanned(set: LoggedSet): Boolean = when (val reps = reps) {
    is Reps.Count -> set.reps == reps.count
    is Reps.Duration -> reps.minSeconds == reps.maxSeconds && set.reps == reps.minSeconds
    is Reps.PerSide -> set.reps == reps.count
    is Reps.Steps -> set.reps == reps.count
    is Reps.Lengths -> set.reps == reps.count
    is Reps.Range, is Reps.UpTo, is Reps.Other -> false
}
