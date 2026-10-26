package com.github.irdcat.workout.domain.settings

/** Signals that a rest is over, the way [RestEndAlert] says. */
fun interface RestEndNotifier {
    fun notify(alert: RestEndAlert)
}
