package com.github.irdcat.workout.domain.session

import kotlinx.coroutines.flow.Flow

/** What the rest after an unlimited set starts from when neither the sheet nor the user gave one. */
const val DEFAULT_SET_REST_SECONDS = 90

/**
 * The rest the user picks after sets of unlimited sets (an empty sets cell), per exercise.
 * Kept by exercise name like [WarmUpRestRepository], so it carries over to later weeks.
 */
interface SetRestRepository {

    /** Seconds; null until one was saved for [exerciseName]. */
    fun observe(exerciseName: String): Flow<Int?>

    suspend fun save(exerciseName: String, seconds: Int)
}
