package com.github.irdcat.workout.infrastructure.google

import com.github.irdcat.workout.domain.workout.TrainingMonth
import com.github.irdcat.workout.infrastructure.cache.ExerciseWithGroups
import com.github.irdcat.workout.infrastructure.cache.SheetContent
import com.github.irdcat.workout.infrastructure.cache.WeekWithWorkouts
import com.github.irdcat.workout.infrastructure.cache.WorkoutWithExercises
import com.github.irdcat.workout.infrastructure.cache.toMonths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Sheet exports in the test-data resource folder, as returned by the Sheets values API. */
object SheetFixtures {

    private val dir: File = File(SheetFixtures::class.java.getResource("/test-data")!!.toURI())

    /** Tab name to its export file, in tab order (the files are prefixed with the tab index). */
    fun files(): List<Pair<String, File>> =
        dir.listFiles { file -> file.name.first().isDigit() && file.extension == "json" }!!
            .sortedBy { it.name.substringBefore('_').toInt() }
            .map { file -> file.nameWithoutExtension.substringAfter('_') to file }

    /** Tab name to its rows, in tab order. */
    fun sheets(): List<Pair<String, List<SheetRow>>> =
        files().map { (name, file) -> name to rows(name, file) }

    fun parsed(): List<Pair<String, SheetContent>> =
        sheets().map { (name, rows) -> name to SheetPlanParser.parse(name, rows) }

    /** Runs the parsed content through the same mapping the repository uses. */
    fun months(): List<TrainingMonth> = parsed().flatMap { (_, content) -> content.toRelations() }.toMonths()

    private fun rows(sheetName: String, file: File): List<SheetRow> {
        val values = Json.parseToJsonElement(file.readText()).jsonObject["values"] as JsonArray
        return values.mapIndexed { index, row ->
            SheetRow(index, sheetName, row.jsonArray.map { it.jsonPrimitive.content })
        }
    }

    /** What Room's @Relation queries would return for this content. */
    private fun SheetContent.toRelations(): List<WeekWithWorkouts> = weeks.map { week ->
        WeekWithWorkouts(
            week = week,
            workouts = workouts.filter { it.weekId == week.id }.map { workout ->
                WorkoutWithExercises(
                    workout = workout,
                    exercises = exercises.filter { it.workoutId == workout.id }.map { exercise ->
                        ExerciseWithGroups(exercise, setGroups.filter { it.exerciseId == exercise.id })
                    },
                )
            },
        )
    }
}
