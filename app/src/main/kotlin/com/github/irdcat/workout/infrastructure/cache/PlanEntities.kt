package com.github.irdcat.workout.infrastructure.cache

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// Cells are stored as read from the sheet and parsed when mapped to the domain,
// so improving the notation parser never needs a migration.
//
// Workout IDs are "<sheet><year of its first week>/<week number in the sheet>/<workout name>"
// (see SheetPlanParser.workoutId). Other IDs are "<sheet name>!<1-based row>", stable
// as long as rows above don't move.

/** One synced tab. Deleting it cascades to its weeks and everything below. */
@Entity(tableName = "sheets")
data class SheetEntity(
    @PrimaryKey val name: String,
    val sheetIndex: Int,
    val syncedAtMillis: Long,
)

@Entity(
    tableName = "weeks",
    foreignKeys = [
        ForeignKey(
            entity = SheetEntity::class,
            parentColumns = ["name"],
            childColumns = ["sheetName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sheetName"), Index("startEpochDay")],
)
data class WeekEntity(
    @PrimaryKey val id: String,
    val sheetName: String,
    val sheetRow: Int,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val isRestWeek: Boolean,
)

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = WeekEntity::class,
            parentColumns = ["id"],
            childColumns = ["weekId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("weekId")],
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val weekId: String,
    val code: String,
    val position: Int,
)

@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    /** Column A, e.g. "5a". */
    val number: String,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "set_groups",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class SetGroupEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val sheetRow: Int,
    val sets: String,
    val reps: String,
    val load: String,
    val tempo: String,
    val rest: String,
    val result: String,
    /** Column B of a continuation row, when it holds text other than "-". */
    val note: String?,
    val position: Int,
)

data class ExerciseWithGroups(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val groups: List<SetGroupEntity>,
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = ExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<ExerciseWithGroups>,
)

data class WeekWithWorkouts(
    @Embedded val week: WeekEntity,
    @Relation(entity = WorkoutEntity::class, parentColumn = "id", entityColumn = "weekId")
    val workouts: List<WorkoutWithExercises>,
)

/** Everything parsed from one tab, ready to insert. */
data class SheetContent(
    val weeks: List<WeekEntity>,
    val workouts: List<WorkoutEntity>,
    val exercises: List<ExerciseEntity>,
    val setGroups: List<SetGroupEntity>,
)
