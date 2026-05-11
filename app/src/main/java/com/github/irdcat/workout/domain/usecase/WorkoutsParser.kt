package com.github.irdcat.workout.domain.usecase

import com.github.irdcat.workout.data.model.SheetRowDto
import com.github.irdcat.workout.domain.model.ExerciseEntry
import com.github.irdcat.workout.domain.model.ExerciseSet
import com.github.irdcat.workout.domain.model.Reps
import com.github.irdcat.workout.domain.model.Rest
import com.github.irdcat.workout.domain.model.Tempo
import com.github.irdcat.workout.domain.model.Weight
import com.github.irdcat.workout.domain.model.WorkoutDay
import com.github.irdcat.workout.domain.model.WorkoutWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class WorkoutsParser {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private data class RawSet(
        val reps: String?,
        val weightOrRpe: String?,
        val tempo: String?,
        val rest: String?,
        val notes: String?,
    )

    private data class RawExercise(
        val name: String,
        val sets: MutableList<RawSet>,
        val baseNumber: Int,
        val letter: Char?,
        val results: MutableMap<String, String> = mutableMapOf(),
        val resultDestinations: MutableMap<String, String> = mutableMapOf()
    )

    fun parse(rows: List<SheetRowDto>): List<WorkoutWeek> {
        val weeks: MutableList<WorkoutWeek> = mutableListOf()
        var currentWeek: WorkoutWeek? = null
        var currentDayExercises: MutableList<RawExercise>? = null
        var currentDayType: String? = null
        var currentRawExercise: RawExercise? = null

        for(row in rows) {
            if (row.values.all { it.isBlank() }) {
                continue
            }

            val firstCol = row.values.getOrElse(0) { "" }.trim()
            val secondCol = row.values.getOrElse(1) { "" }.trim()

            if (secondCol.contains(" - ") && secondCol.contains(".")) {
                finalizeDay(currentDayExercises, currentDayType, currentWeek)
                finalizeWeek(currentWeek, weeks)

                val dates = secondCol.split(" - ")
                if (dates.size == 2) {
                    val start = LocalDate.parse(dates[0], dateFormatter)
                    val end = LocalDate.parse(dates[1], dateFormatter)
                    currentWeek = WorkoutWeek(start, end, mutableListOf())
                }
                currentDayExercises = null
                currentRawExercise = null
                currentDayType = null
                continue
            }

            if (firstCol.matches(Regex("^[LTU][\\d]$"))) {
                finalizeDay(currentDayExercises, currentDayType, currentWeek)
                currentDayExercises = mutableListOf()
                currentDayType = firstCol
                currentRawExercise = null
                continue
            }

            if (currentDayExercises != null) {
                val numberMatch = Regex("^(\\d+)([a-z])?$").find(firstCol)

                if (numberMatch != null) {
                    // New exercise (or new superset part)
                    val number = numberMatch.groupValues[1].toInt()
                    val letter = numberMatch.groupValues.getOrNull(2)?.firstOrNull()
                    currentRawExercise = RawExercise(
                        name = secondCol,
                        sets = mutableListOf(),
                        baseNumber = number,
                        letter = letter
                    )
                    currentRawExercise.let { currentDayExercises.add(it) }
                    addRawSet(currentRawExercise, row)
                } else if (firstCol.isEmpty()) {
                    // Continuation of the previous exercise (additional sets)
                    if (currentRawExercise != null) {
                        addRawSet(currentRawExercise, row)
                    }
                }
            }
        }
        finalizeDay(currentDayExercises, currentDayType, currentWeek)
        finalizeWeek(currentWeek, weeks)

        return weeks
    }

    private fun addRawSet(rawExercise: RawExercise?, row: SheetRowDto) {
        rawExercise?.let {
            val seriesText = row.values.getOrNull(2)?.trim() ?: "1"
            val seriesCount = seriesText.toIntOrNull() ?: 1

            val reps = row.values.getOrNull(3)?.trim()
            val weight = row.values.getOrNull(4)?.trim()
            val tempo = row.values.getOrNull(5)?.trim()
            val rest = row.values.getOrNull(6)?.trim()
            val notes = row.values.getOrNull(7)?.trim()

            repeat(seriesCount) { _ ->
                it.sets.add(
                    RawSet(
                        reps = reps,
                        weightOrRpe = weight,
                        tempo = tempo,
                        rest = rest,
                        notes = notes
                    )
                )
            }

            notes
                .orEmpty()
                .let { n ->
                    if (it.results.contains(it.name) && it.results[it.name]?.isNotBlank() == true) {
                        n.takeIf { note -> note.isNotBlank() }
                            ?.let { note ->
                                it.results[it.name] = note
                                it.resultDestinations[it.name] = buildResultDestination(row)
                            }
                        return@let
                    }
                    it.results[it.name] = n
                    it.resultDestinations[it.name] = buildResultDestination(row)
                }
        }
    }

    private fun buildResultDestination(row: SheetRowDto) = "${row.sheetName}!${'A'.plus(7)}${row.rowIndex}"

    private fun finalizeDay(rawExercises: MutableList<RawExercise>?, dayType: String?, currentWeek: WorkoutWeek?) {
        if (rawExercises != null && dayType != null && currentWeek != null) {
            val items = buildExerciseEntries(rawExercises)
            val day = WorkoutDay(dayType, items)
            (currentWeek.days as MutableList).add(day)
        }
    }

    private fun finalizeWeek(currentWeek: WorkoutWeek?, weeks: MutableList<WorkoutWeek>) {
        currentWeek?.let { weeks.add(it) }
    }

    private fun buildExerciseEntries(rawExercises: List<RawExercise>): List<ExerciseEntry> {
        val entries = mutableListOf<ExerciseEntry>()
        var i = 0
        while(i < rawExercises.size) {
            val ex = rawExercises[i]
            if (ex.letter != null) {
                // Start of superset: collect all exercises with the same baseNumber
                val group = mutableSetOf<RawExercise>()
                var j = i
                while (j < rawExercises.size
                    && rawExercises[j].letter != null
                    && rawExercises[j].baseNumber == ex.baseNumber) {
                    group.add(rawExercises[j])
                    j++
                }

                // Build superset rounds by interleaving sets
                val maxSets = group.maxOfOrNull { it.sets.size } ?: 0
                val roundExercises = mutableListOf<ExerciseSet>()
                val groupResults = mutableMapOf<String, String>()
                val groupResultDestinations = mutableMapOf<String, String>()
                for (roundIdx in 0 until maxSets) {
                    for (g in group) {
                        if (roundIdx < g.sets.size) {
                            val s = g.sets[roundIdx]
                            roundExercises.add(
                                ExerciseSet(
                                    name = g.name,
                                    reps = Reps.fromString(s.reps ?: ""),
                                    weight = Weight.fromString(s.weightOrRpe ?: ""),
                                    tempo = Tempo.fromString(s.tempo ?: ""),
                                    rest = Rest.fromString(s.rest ?: "")
                                )
                            )
                        }
                        groupResults.putAll(g.results)
                        groupResultDestinations.putAll(g.resultDestinations)
                    }
                }
                entries.add(
                    ExerciseEntry(
                        roundExercises,
                        groupResults,
                        groupResultDestinations
                    )
                )
                i = j
            } else {
                // Regular exercise
                val sets = ex.sets.map {
                    ExerciseSet(
                        name = ex.name,
                        reps = Reps.fromString(it.reps ?: ""),
                        weight = Weight.fromString(it.weightOrRpe ?: ""),
                        tempo = Tempo.fromString(it.tempo ?: ""),
                        rest = Rest.fromString(it.rest ?: "")
                    )
                }
                entries.add(
                    ExerciseEntry(
                        sets,
                        ex.results,
                        ex.resultDestinations
                    )
                )
                i++
            }
        }
        return entries
    }
}