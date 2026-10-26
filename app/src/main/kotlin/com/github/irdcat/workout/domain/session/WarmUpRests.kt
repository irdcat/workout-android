package com.github.irdcat.workout.domain.session

import kotlinx.coroutines.flow.Flow

/** What a warm-up rest starts from before the user has picked one for an exercise. */
const val DEFAULT_WARM_UP_REST_SECONDS = 60

/**
 * The rest the user takes after warm-up sets, per exercise: the sheet has none.
 * Kept by exercise name, so it carries over to the same exercise in later weeks.
 */
interface WarmUpRestRepository {

    /** Seconds; null until one was saved for [exerciseName]. */
    fun observe(exerciseName: String): Flow<Int?>

    suspend fun save(exerciseName: String, seconds: Int)
}

/** Names match however they're spaced or capitalised in the sheet. */
fun warmUpRestKey(exerciseName: String): String =
    exerciseName.trim().lowercase().replace(Regex("""\s+"""), " ")
