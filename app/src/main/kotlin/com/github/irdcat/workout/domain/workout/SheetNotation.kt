package com.github.irdcat.workout.domain.workout

import java.time.Duration

private const val SECONDS_PER_MINUTE = 60

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

    /** Tried in order; the first that matches the whole cell wins. */
    private val RepsFormats: List<Pair<Regex, (MatchResult) -> Reps>> = listOf(
        RepsCount to { Reps.Count(it.int(1)) },
        RepsRange to { Reps.Range(it.int(1), it.int(2)) },
        RepsDuration to { Reps.Duration(it.int(1), it.groupValues[2].toIntOrNull() ?: it.int(1)) },
        RepsPerSide to { Reps.PerSide(it.int(1)) },
        RepsSteps to { Reps.Steps(it.int(1)) },
        RepsLengths to { Reps.Lengths(it.int(1)) },
        RepsUpTo to { Reps.UpTo(it.int(1)) },
    )

    /** Tried in order, after bodyweight; the first that matches the whole cell wins. */
    private val LoadFormats: List<Pair<Regex, (MatchResult) -> Load>> = listOf(
        LoadRpe to { Load.Rpe(it.decimal(1)) },
        LoadImplements to { Load.Implements(it.int(1), it.decimal(2)) },
        LoadChoice to { match -> Load.Choice(match.value.split('/').map { it.replace(KgSuffix, "").trim().toDecimal() }) },
        LoadRange to { Load.Range(it.decimal(1), it.decimal(2)) },
        LoadWeight to { Load.Weight(it.decimal(1)) },
    )

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
        return RepsFormats.firstNotNullOfOrNull { (pattern, reps) -> pattern.matchEntire(text)?.let(reps) } ?: Reps.Other(text)
    }

    fun load(raw: String): Load {
        val text = raw.trim()
        if (text.isEmpty() || text == "-" || text == "–") return Load.Bodyweight
        return LoadFormats.firstNotNullOfOrNull { (pattern, load) -> pattern.matchEntire(text)?.let(load) } ?: Load.Other(text)
    }

    fun tempo(raw: String): TempoPattern? {
        val text = raw.trim()
        return if (TempoPhases.matches(text)) TempoPattern(text.uppercase()) else null
    }

    fun restSeconds(raw: String): Int? {
        val text = raw.trim()
        return RestSeconds.matchEntire(text)?.int(1)
            ?: RestMinutes.matchEntire(text)?.let { (it.decimal(1) * SECONDS_PER_MINUTE).toInt() }
    }

    fun result(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

    /** A time limit written in an exercise's text, e.g. "Czas: 10:00" or "czas 12 min". */
    fun timeLimit(text: String): Duration? {
        return TimeLimitClock.find(text)?.let { Duration.ofMinutes(it.int(1).toLong()).plusSeconds(it.int(2).toLong()) }
            ?: TimeLimitMinutes.find(text)?.let { Duration.ofMinutes(it.int(1).toLong()) }
    }

    private fun MatchResult.int(group: Int): Int = groupValues[group].toInt()

    private fun MatchResult.decimal(group: Int): Double = groupValues[group].toDecimal()

    private fun String.toDecimal(): Double = replace(',', '.').toDouble()
}
