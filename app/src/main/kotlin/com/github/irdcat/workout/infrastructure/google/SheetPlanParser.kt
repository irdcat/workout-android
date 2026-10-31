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

    fun parse(sheetName: String, rows: List<SheetRow>): SheetContent =
        TabReader(sheetName).apply { rows.forEach(::read) }.content()

    /** Reads one tab top to bottom, remembering the week, workout and exercise each row falls under. */
    private class TabReader(private val sheetName: String) {
        private val weeks = mutableListOf<WeekEntity>()
        private val workouts = mutableListOf<WorkoutEntity>()
        private val exercises = mutableListOf<ExerciseEntity>()
        private val groups = mutableListOf<SetGroupEntity>()

        private var week: WeekEntity? = null
        private var workout: WorkoutEntity? = null
        private var exercise: ExerciseEntity? = null

        fun content() = SheetContent(weeks, workouts, exercises, groups)

        fun read(row: SheetRow) {
            val sheetRow = row.index + 1
            val id = "$sheetName!$sheetRow"
            val cells = row.values.map { it.trim() } + List((COLUMNS - row.values.size).coerceAtLeast(0)) { "" }
            val (a, b) = cells
            val range = dateRange(a) ?: dateRange(b)
            when {
                range != null -> startWeek(id, sheetRow, range, cells)
                b.equals(HEADER_MARKER, ignoreCase = true) -> startWorkout(code = a)
                cells.all { it.isEmpty() } -> Unit
                a.isNotEmpty() -> startExercise(id, sheetRow, cells)
                else -> addSetGroup(id, sheetRow, cells)
            }
        }

        private fun startWeek(id: String, sheetRow: Int, range: Pair<LocalDate, LocalDate>, cells: List<String>) {
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

        /** A header row: column A names the workout. Ignored before the first week. */
        private fun startWorkout(code: String) {
            val currentWeek = week ?: return
            workout = WorkoutEntity(
                id = uniqueWorkoutId(
                    base = workoutId(
                        sheetName = sheetName,
                        firstWeekYear = LocalDate.ofEpochDay(weeks.first().startEpochDay).year,
                        weekNumber = weeks.size,
                        workoutName = code,
                    ),
                    taken = workouts,
                ),
                weekId = currentWeek.id,
                code = code,
                position = workouts.count { it.weekId == currentWeek.id },
            ).also(workouts::add)
            exercise = null
        }

        /** A numbered row: a new exercise and its first set group. Ignored outside a workout. */
        private fun startExercise(id: String, sheetRow: Int, cells: List<String>) {
            val currentWorkout = workout ?: return
            val started = ExerciseEntity(
                id = id,
                workoutId = currentWorkout.id,
                number = cells[0],
                name = cells[1],
                position = exercises.count { it.workoutId == currentWorkout.id },
            ).also(exercises::add)
            exercise = started
            groups += setGroup(id, started.id, sheetRow, cells, note = null, position = 0)
        }

        /** A continuation row: another set group for the exercise above. Ignored without one. */
        private fun addSetGroup(id: String, sheetRow: Int, cells: List<String>) {
            val currentExercise = exercise ?: return
            groups += setGroup(
                id = id,
                exerciseId = currentExercise.id,
                sheetRow = sheetRow,
                cells = cells,
                note = cells[1].takeUnless { it.isEmpty() || it == "-" },
                position = groups.count { it.exerciseId == currentExercise.id },
            )
        }
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
