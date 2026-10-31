package com.github.irdcat.workout.testing

import com.github.irdcat.workout.domain.session.LoggedSet
import java.time.Instant

/** A working set as logged, not stored yet: no RPE and not a warm-up unless given. */
fun loggedSet(
    exerciseId: String,
    setGroupId: String,
    kg: Double?,
    reps: Int?,
    at: Instant,
    rpe: Double? = null,
    warmUp: Boolean = false,
) = LoggedSet(exerciseId = exerciseId, setGroupId = setGroupId, weightKg = kg, reps = reps, rpe = rpe, warmUp = warmUp, loggedAt = at)
