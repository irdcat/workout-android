package com.github.irdcat.workout.application.session

import com.github.irdcat.workout.application.ui.screens.formatRestTime
import java.time.Instant

/**
 * What the session notification shows: Home's session card, without the per-second
 * values. The clock is left to the notification's own chronometer, so the content
 * only changes when the session does (a set logged, a rest started, paused or over).
 */
data class SessionNotificationContent(
    val title: String,
    val text: String,
    val chronometer: Chronometer?,
)

/** A clock the notification runs itself: up from [base], or down to it. */
data class Chronometer(val base: Instant, val countDown: Boolean)

/**
 * "RDL · set 4 of 5", while resting "RDL · resting · set 4 of 5 next" with the rest
 * counting down; otherwise the workout's clock, when it's measured.
 */
fun SessionStatus.toNotificationContent(): SessionNotificationContent {
    val restEnd = rest?.endsAt
    val chronometer = when {
        restEnd != null -> Chronometer(restEnd, countDown = true)
        elapsed != null -> Chronometer(active.session.startedAt, countDown = false)
        else -> null
    }
    return SessionNotificationContent(title = active.workout.code, text = line(), chronometer = chronometer)
}

private fun SessionStatus.line(): String {
    val next = active.progress.next
    val review = active.progress.toReview
    val exercise = next?.exercise?.name ?: review?.name ?: return "All exercises logged"
    if (review != null) return "$exercise · review the result"
    val set = next?.setNumber?.let { number -> "set $number" + (next.plannedCount?.let { " of $it" } ?: "") }
    val rest = rest ?: return listOfNotNull(exercise, set).joinToString(" · ")
    val resting = if (rest.paused) "rest paused, ${formatRestTime(rest.remainingSeconds)} left" else "resting"
    return listOfNotNull(exercise, resting, set?.let { "$it next" }).joinToString(" · ")
}
