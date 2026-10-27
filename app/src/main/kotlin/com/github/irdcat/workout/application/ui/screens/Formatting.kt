package com.github.irdcat.workout.application.ui.screens

import java.time.Duration
import kotlin.math.abs

/** "1 week", "3 weeks". */
internal fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

/** "1:26", "58:24" or "1:02:31"; the sign is dropped. */
internal fun formatClock(seconds: Int): String {
    val s = abs(seconds)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "12:04", or "1:02:31" from an hour on. */
fun formatElapsed(elapsed: Duration): String = formatClock(elapsed.seconds.coerceAtLeast(0).toInt())

/** "1 hour 2 minutes", "45 seconds": a time for screen readers. */
internal fun spokenDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return buildList {
        if (h > 0) add(plural(h, "hour"))
        if (m > 0) add(plural(m, "minute"))
        if (s > 0 || isEmpty()) add(plural(s, "second"))
    }.joinToString(" ")
}
