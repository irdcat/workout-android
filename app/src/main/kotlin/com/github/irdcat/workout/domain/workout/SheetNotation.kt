package com.github.irdcat.workout.domain.workout

import java.time.Duration

/**
 * Reads the notation used in the spreadsheet's cells. Every parser accepts any
 * text and falls back to an "unknown"/"other" value instead of failing.
 */
object SheetNotation {

    private const val DASH = "[-–]"
    private const val NUMBER = """\d+(?:[.,]\d+)?"""

    private val ExerciseNumberPattern = Regex("""^(\d+)([a-zA-Z])?$""")

    private val RepsCount = Regex("""^(\d+)$""")
    private val RepsRange = Regex("""^(\d+)\s*$DASH\s*(\d+)$""")
    private val RepsDuration = Regex("""^(\d+)(?:\s*$DASH\s*(\d+))?\s*(?:sek|s)\.?$""", RegexOption.IGNORE_CASE)
    private val RepsPerSide = Regex("""^(\d+)\s*/\s*str\.?$""", RegexOption.IGNORE_CASE)
    private val RepsSteps = Regex("""^(\d+)\s*krok(?:ów|i)?$""", RegexOption.IGNORE_CASE)
    private val RepsLengths = Regex("""^(\d+)\s*długoś(?:ci|ć)$""", RegexOption.IGNORE_CASE)
    private val RepsUpTo = Regex("""^<\s*(\d+)$""")

    private val LoadRpe = Regex("""^RPE\s*($NUMBER)$""", RegexOption.IGNORE_CASE)
    private val LoadImplements = Regex("""^(\d+)\s*x\s*($NUMBER)\s*(?:kg)?$""", RegexOption.IGNORE_CASE)
    private val LoadRange = Regex("""^($NUMBER)\s*(?:kg)?\s*$DASH\s*($NUMBER)\s*(?:kg)?$""", RegexOption.IGNORE_CASE)
    private val LoadChoice = Regex("""^$NUMBER\s*(?:kg)?(?:\s*/\s*$NUMBER\s*(?:kg)?)+$""", RegexOption.IGNORE_CASE)
    private val LoadWeight = Regex("""^($NUMBER)\s*(?:kg)?$""", RegexOption.IGNORE_CASE)

    private val KgSuffix = Regex("""\s*kg$""", RegexOption.IGNORE_CASE)

    private val TempoPhases = Regex("""^[0-9xX-]{4}$""")
    private val RestSeconds = Regex("""^(\d+)\s*(?:sek|s)?\.?$""", RegexOption.IGNORE_CASE)
    private val RestMinutes = Regex("""^($NUMBER)\s*min\.?$""", RegexOption.IGNORE_CASE)
    private val TimeLimitClock = Regex("""\bczas\s*:?\s*(\d{1,3}):(\d{2})""", RegexOption.IGNORE_CASE)
    private val TimeLimitMinutes = Regex("""\bczas\s*:?\s*(\d{1,3})\s*min""", RegexOption.IGNORE_CASE)

    fun exerciseNumber(raw: String): ExerciseNumber? {
        val match = ExerciseNumberPattern.matchEntire(raw.trim()) ?: return null
        val (index, member) = match.destructured
        return ExerciseNumber(index.toInt(), member.firstOrNull()?.lowercaseChar())
    }

    fun sets(raw: String): SetCount {
        val text = raw.trim()
        return when {
            text.isEmpty() -> SetCount.Unlimited
            text.equals("x", ignoreCase = true) -> SetCount.Open
            text.toIntOrNull() != null -> SetCount.Fixed(text.toInt())
            else -> SetCount.Unknown(text)
        }
    }

    fun reps(raw: String): Reps {
        val text = raw.trim()
        RepsCount.matchEntire(text)?.let { return Reps.Count(it.int(1)) }
        RepsRange.matchEntire(text)?.let { return Reps.Range(it.int(1), it.int(2)) }
        RepsDuration.matchEntire(text)?.let { m ->
            val min = m.int(1)
            return Reps.Duration(min, m.groupValues[2].toIntOrNull() ?: min)
        }
        RepsPerSide.matchEntire(text)?.let { return Reps.PerSide(it.int(1)) }
        RepsSteps.matchEntire(text)?.let { return Reps.Steps(it.int(1)) }
        RepsLengths.matchEntire(text)?.let { return Reps.Lengths(it.int(1)) }
        RepsUpTo.matchEntire(text)?.let { return Reps.UpTo(it.int(1)) }
        return Reps.Other(text)
    }

    fun load(raw: String): Load {
        val text = raw.trim()
        if (text.isEmpty() || text == "-" || text == "–") return Load.Bodyweight
        LoadRpe.matchEntire(text)?.let { return Load.Rpe(it.decimal(1)) }
        LoadImplements.matchEntire(text)?.let { return Load.Implements(it.int(1), it.decimal(2)) }
        LoadChoice.matchEntire(text)?.let {
            val kg = text.split('/').map { part -> part.replace(KgSuffix, "").trim().toDecimal() }
            return Load.Choice(kg)
        }
        LoadRange.matchEntire(text)?.let { return Load.Range(it.decimal(1), it.decimal(2)) }
        LoadWeight.matchEntire(text)?.let { return Load.Weight(it.decimal(1)) }
        return Load.Other(text)
    }

    fun tempo(raw: String): TempoPattern? {
        val text = raw.trim()
        return if (TempoPhases.matches(text)) TempoPattern(text.uppercase()) else null
    }

    fun restSeconds(raw: String): Int? {
        val text = raw.trim()
        RestSeconds.matchEntire(text)?.let { return it.int(1) }
        RestMinutes.matchEntire(text)?.let { return (it.decimal(1) * 60).toInt() }
        return null
    }

    fun result(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

    /** A time limit written in an exercise's text, e.g. "Czas: 10:00" or "czas 12 min". */
    fun timeLimit(text: String): Duration? {
        TimeLimitClock.find(text)?.let { return Duration.ofMinutes(it.int(1).toLong()).plusSeconds(it.int(2).toLong()) }
        TimeLimitMinutes.find(text)?.let { return Duration.ofMinutes(it.int(1).toLong()) }
        return null
    }

    private fun MatchResult.int(group: Int): Int = groupValues[group].toInt()

    private fun MatchResult.decimal(group: Int): Double = groupValues[group].toDecimal()

    private fun String.toDecimal(): Double = replace(',', '.').toDouble()
}
