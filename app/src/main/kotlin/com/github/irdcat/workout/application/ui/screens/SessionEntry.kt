package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.settings.AppSettings
import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.unit
import java.math.BigDecimal
import com.github.irdcat.workout.domain.workout.SetGroup as PlanSetGroup

// The set entry on the session screen: what it starts from and how the user's edits change it.

/** A rest −/+ tap moves it this much. */
private const val REST_STEP_SECONDS = 15

/** How [event] changes the entry; null for events that aren't edits to it. */
internal fun entryEdit(event: SessionEvent): ((SetEntry) -> SetEntry)? = when (event) {
    is SessionEvent.WeightChanged -> { entry -> entry.copy(weight = event.value.filterDecimal()) }
    is SessionEvent.AdjustWeight -> { entry -> entry.copy(weight = stepWeight(entry.weight, entry.weightStep, event.direction)) }
    is SessionEvent.WeightStepSelected -> { entry -> entry.copy(weightStep = event.step) }
    is SessionEvent.RepsChanged -> { entry -> entry.copy(reps = event.value.filter(Char::isDigit)) }
    is SessionEvent.AdjustReps -> { entry -> entry.copy(reps = stepReps(entry.reps, entry.repsStep, event.direction)) }
    is SessionEvent.RpeChanged -> { entry -> entry.copy(rpe = event.value.filterDecimal()) }
    is SessionEvent.AdjustRpe -> { entry -> entry.copy(rpe = stepRpe(entry.rpe, entry.rpeStep, event.direction)) }
    is SessionEvent.RpeStepSelected -> { entry -> entry.copy(rpeStep = event.step) }
    is SessionEvent.RestChanged -> { entry -> entry.withRest { event.value.filterRestTime() } }
    is SessionEvent.AdjustRest -> { entry -> entry.withRest { stepRest(it, event.direction) } }
    else -> null
}

/** Only an entry that picks its rest has one to change. */
private fun SetEntry.withRest(transform: (String) -> String): SetEntry = rest?.let { copy(rest = transform(it)) } ?: this

private fun stepReps(reps: String, step: Int, direction: Int): String =
    ((reps.toIntOrNull() ?: 0) + direction * step).coerceAtLeast(0).toString()

private fun stepRest(rest: String, direction: Int): String =
    formatRestTime(((parseRestTime(rest) ?: 0) + REST_STEP_SECONDS * direction).coerceAtLeast(0))

/**
 * Prefilled from the sheet's target; an RPE-only target can take last time's weight.
 * Without a weight it starts at 0, which is logged as bodyweight. The RPE is the
 * target's when it has one, else [DefaultRpe].
 */
internal fun PlanSetGroup.toEntry(settings: AppSettings, lastTime: LastTimeEntry?): SetEntry {
    val lastWeight = lastTime?.weightKg?.takeIf { settings.prefillWeightFromLastTime }
    return SetEntry(
        weightLabel = if (load is Load.Implements) "Each DB (kg)" else "Weight (kg)",
        weight = load.startKg(lastWeight)?.kg() ?: "0",
        weightStep = settings.defaultWeightStepKg.kg(),
        repsLabel = reps.unit.fieldLabel,
        reps = reps.startCount()?.toString().orEmpty(),
        repsStep = reps.unit.step,
        rpe = ((load as? Load.Rpe)?.value?.toBigDecimal() ?: DefaultRpe).stripTrailingZeros().toPlainString(),
        lastTime = lastTime?.ui,
    )
}

/** The lighter end of the target; for an RPE-only one, [lastWeight] if there is one. */
private fun Load.startKg(lastWeight: Double?): Double? = when (this) {
    is Load.Weight -> kg
    is Load.Implements -> kgEach
    is Load.Range -> minKg
    is Load.Choice -> kg.first()
    is Load.Rpe -> lastWeight
    Load.Bodyweight, is Load.Other -> null
}

/** The lower end of the target; null when it isn't a count. */
private fun Reps.startCount(): Int? = when (this) {
    is Reps.Count -> count
    is Reps.Range -> min
    is Reps.UpTo -> count
    is Reps.PerSide -> count
    is Reps.Steps -> count
    is Reps.Lengths -> count
    is Reps.Duration -> minSeconds
    is Reps.Other -> null
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
