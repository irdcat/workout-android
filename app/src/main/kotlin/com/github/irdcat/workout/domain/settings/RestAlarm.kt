package com.github.irdcat.workout.domain.settings

import java.time.Instant

/**
 * Sets off [RestEndAlert] when a rest ends, even if the app is in the background
 * or was closed meanwhile. There's one rest at a time: scheduling replaces the last one.
 */
interface RestAlarm {

    fun schedule(endsAt: Instant, alert: RestEndAlert)

    fun cancel()
}
