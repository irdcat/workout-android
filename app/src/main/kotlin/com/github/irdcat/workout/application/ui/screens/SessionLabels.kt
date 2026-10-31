package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.session.LoggedSet
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.RepsUnit
import com.github.irdcat.workout.domain.workout.SetGroup as PlanSetGroup

// What the session screens say about sets: targets, chips and notes.

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
