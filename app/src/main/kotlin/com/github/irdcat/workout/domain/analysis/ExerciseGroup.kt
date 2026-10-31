package com.github.irdcat.workout.domain.analysis

import java.text.Normalizer

/**
 * Exercises analysed as one, e.g. "Squat" for both "Squat" and "Przysiad". Only the names
 * listed count: a variation such as "Front Squat" is another exercise unless it's added.
 */
data class ExerciseGroup(val name: String, val exercises: List<String>)

/** What a new install analyses: the three competition lifts, by their English and Polish names. */
val DefaultExerciseGroups: List<ExerciseGroup> = listOf(
    ExerciseGroup("Squat", listOf("Squat", "Back Squat", "Przysiad", "Przysiad tylny", "Przysiad ze sztangą")),
    ExerciseGroup(
        "Bench",
        listOf("Bench Press", "Bench", "Wyciskanie leżąc", "Wyciskanie sztangi leżąc", "Wyciskanie na ławce"),
    ),
    ExerciseGroup(
        "Deadlift",
        listOf(
            "Deadlift", "Conventional Deadlift", "Classic Deadlift", "Sumo Deadlift",
            "Martwy ciąg", "Martwy ciąg klasyczny", "Martwy ciąg sumo",
        ),
    ),
)

/**
 * An exercise name as groups compare it: ignoring case, spacing, accents, a trailing dot
 * and notes in brackets, so "PRZYSIAD (ciężka piątka)" is "przysiad".
 */
fun exerciseKey(name: String): String {
    val plain = Normalizer.normalize(name.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
    return plain.replace(Regex("""\(.*?\)"""), " ")
        .trim().trimEnd('.').trim().lowercase().replace(Regex("""\s+"""), " ")
}

/** The group an exercise belongs to; the first one listing it when several do. */
fun List<ExerciseGroup>.groupOf(exerciseName: String): ExerciseGroup? {
    val key = exerciseKey(exerciseName)
    return firstOrNull { group -> group.exercises.any { exerciseKey(it) == key } }
}

/** Whether [name] can name a group: not blank, and not one of [taken] (but [renaming]), ignoring case. */
fun isFreeGroupName(taken: Collection<String>, name: String, renaming: String? = null): Boolean {
    val wanted = name.trim()
    return wanted.isNotEmpty() && taken.none { it != renaming && it.equals(wanted, ignoreCase = true) }
}
