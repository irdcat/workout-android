package com.github.irdcat.workout.infrastructure.google

import com.github.irdcat.workout.infrastructure.cache.ExerciseEntity
import com.github.irdcat.workout.infrastructure.cache.SetGroupEntity
import com.github.irdcat.workout.infrastructure.cache.SheetContent
import com.github.irdcat.workout.infrastructure.cache.WeekEntity
import com.github.irdcat.workout.infrastructure.cache.WorkoutEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Splits one month tab into weeks, workouts, exercises and set groups.
 *
 * Layout (columns A–H):
 * - Week row: `dd.MM.yyyy - dd.MM.yyyy` in A or B; "WOLNE" in a later cell marks a rest week.
 * - Workout header: the workout code in A and "ĆWICZENIE" in B.
 * - Exercise row: number in A (5, 5a…), name in B, then sets, reps, weight/RPE, tempo, rest, result.
 * - Continuation row: A empty; another set group of the exercise above.
 *
 * Workouts get [workoutId]s built from the tab and week number, so they survive
 * rows being inserted; weeks, exercises and set groups use "<tab>!<row>".
 */
object SheetPlanParser {

    private const val COLUMNS = 8
    private const val HEADER_MARKER = "ĆWICZENIE"
    private const val REST_WEEK_MARKER = "WOLNE"

    private val DateRange = Regex("""^(\d{1,2}\.\d{1,2}\.\d{4})\s*[-–]\s*(\d{1,2}\.\d{1,2}\.\d{4})$""")
    private val DateFormat = DateTimeFormatter.ofPattern("d.M.uuuu")

    fun parse(sheetName: String, rows: List<SheetRow>): SheetContent {
        val weeks = mutableListOf<WeekEntity>()
        val workouts = mutableListOf<WorkoutEntity>()
        val exercises = mutableListOf<ExerciseEntity>()
        val groups = mutableListOf<SetGroupEntity>()

        var week: WeekEntity? = null
        var workout: WorkoutEntity? = null
        var exercise: ExerciseEntity? = null

        for (row in rows) {
            val sheetRow = row.index + 1
            val id = "$sheetName!$sheetRow"
            val cells = row.values.map { it.trim() } + List((COLUMNS - row.values.size).coerceAtLeast(0)) { "" }
            val (a, b) = cells

            val range = dateRange(a) ?: dateRange(b)
            when {
                range != null -> {
                    week = WeekEntity(
                        id = id,
                        sheetName = sheetName,
                        sheetRow = sheetRow,
                        startEpochDay = range.first.toEpochDay(),
                        endEpochDay = range.second.toEpochDay(),
                        isRestWeek = cells.drop(1).any { it.equals(REST_WEEK_MARKER, ignoreCase = true) },
                    ).also(weeks::add)
                    workout = null
                    exercise = null
                }

                b.equals(HEADER_MARKER, ignoreCase = true) -> {
                    val currentWeek = week ?: continue
                    workout = WorkoutEntity(
                        id = uniqueWorkoutId(
                            base = workoutId(
                                sheetName = sheetName,
                                firstWeekYear = LocalDate.ofEpochDay(weeks.first().startEpochDay).year,
                                weekNumber = weeks.size,
                                workoutName = a,
                            ),
                            taken = workouts,
                        ),
                        weekId = currentWeek.id,
                        code = a,
                        position = workouts.count { it.weekId == currentWeek.id },
                    ).also(workouts::add)
                    exercise = null
                }

                cells.all { it.isEmpty() } -> Unit

                a.isNotEmpty() -> {
                    val currentWorkout = workout ?: continue
                    exercise = ExerciseEntity(
                        id = id,
                        workoutId = currentWorkout.id,
                        number = a,
                        name = b,
                        position = exercises.count { it.workoutId == currentWorkout.id },
                    ).also(exercises::add)
                    groups += setGroup(id, exercise.id, sheetRow, cells, note = null, position = 0)
                }

                else -> {
                    val currentExercise = exercise ?: continue
                    groups += setGroup(
                        id = id,
                        exerciseId = currentExercise.id,
                        sheetRow = sheetRow,
                        cells = cells,
                        note = b.takeUnless { it.isEmpty() || it == "-" },
                        position = groups.count { it.exerciseId == currentExercise.id },
                    )
                }
            }
        }
        return SheetContent(weeks, workouts, exercises, groups)
    }

    /**
     * `<sheetName><yearOfFirstWeekStart>/<weekOfMonthNumber>/<workoutName>`, e.g. "CZERWIEC2026/1/T1":
     * T1 in the first week listed on the CZERWIEC tab, whose first week starts in 2026.
     */
    fun workoutId(sheetName: String, firstWeekYear: Int, weekNumber: Int, workoutName: String): String =
        "$sheetName$firstWeekYear/$weekNumber/$workoutName"

    /** A workout name repeated within a week (a typo in the sheet) gets "#2", "#3"… */
    private fun uniqueWorkoutId(base: String, taken: List<WorkoutEntity>): String {
        if (taken.none { it.id == base }) return base
        return generateSequence(2) { it + 1 }.map { "$base#$it" }.first { id -> taken.none { it.id == id } }
    }

    private fun setGroup(
        id: String,
        exerciseId: String,
        sheetRow: Int,
        cells: List<String>,
        note: String?,
        position: Int,
    ) = SetGroupEntity(
        id = id,
        exerciseId = exerciseId,
        sheetRow = sheetRow,
        sets = cells[2],
        reps = cells[3],
        load = cells[4],
        tempo = cells[5],
        rest = cells[6],
        result = cells[7],
        note = note,
        position = position,
    )

    private fun dateRange(cell: String): Pair<LocalDate, LocalDate>? {
        val match = DateRange.matchEntire(cell) ?: return null
        return try {
            LocalDate.parse(match.groupValues[1], DateFormat) to LocalDate.parse(match.groupValues[2], DateFormat)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
