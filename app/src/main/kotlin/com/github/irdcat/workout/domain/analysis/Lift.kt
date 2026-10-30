package com.github.irdcat.workout.domain.analysis

/**
 * The three competition lifts, by any of the names a sheet calls them. Only the lift itself
 * counts: variations such as "Front Squat", "Close Grip Bench Press" or "Deficyt Deadlift"
 * are other exercises.
 */
enum class Lift(val label: String, private val aliases: Set<String>) {
    Squat(
        "Squat",
        setOf("squat", "back squat", "przysiad", "przysiad tylny", "przysiad ze sztangą", "przysiad ze sztanga"),
    ),
    Bench(
        "Bench",
        setOf(
            "bench press", "bench", "wyciskanie leżąc", "wyciskanie lezac", "wyciskanie sztangi leżąc",
            "wyciskanie sztangi lezac", "wyciskanie na ławce", "wyciskanie na lawce",
        ),
    ),
    Deadlift(
        "Deadlift",
        setOf(
            "deadlift", "conventional deadlift", "classic deadlift", "sumo deadlift", "martwy ciąg",
            "martwy ciag", "martwy ciąg klasyczny", "martwy ciag klasyczny", "martwy ciąg sumo", "martwy ciag sumo",
        ),
    ),
    ;

    companion object {
        /** The lift an exercise name means, ignoring case, spacing, a trailing dot and notes in brackets. */
        fun of(name: String): Lift? {
            val key = name.replace(Regex("""\(.*?\)"""), " ")
                .trim().trimEnd('.').trim().lowercase().replace(Regex("""\s+"""), " ")
            return entries.firstOrNull { key in it.aliases }
        }
    }
}
