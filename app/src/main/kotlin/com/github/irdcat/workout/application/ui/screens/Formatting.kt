package com.github.irdcat.workout.application.ui.screens

import java.time.Duration
import kotlin.math.abs

internal const val SECONDS_PER_MINUTE = 60
internal const val SECONDS_PER_HOUR = 3600

/** "1 week", "3 weeks". */
internal fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

/** "1:26", "58:24" or "1:02:31"; the sign is dropped. */
internal fun formatClock(seconds: Int): String {
    val s = abs(seconds)
    val h = s / SECONDS_PER_HOUR
    val m = s % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val sec = s % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "12:04", or "1:02:31" from an hour on. */
fun formatElapsed(elapsed: Duration): String = formatClock(elapsed.seconds.coerceAtLeast(0).toInt())

/** "1 hour 2 minutes", "45 seconds": a time for screen readers. */
internal fun spokenDuration(seconds: Int): String {
    val h = seconds / SECONDS_PER_HOUR
    val m = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val s = seconds % SECONDS_PER_MINUTE
    return buildList {
        if (h > 0) add(plural(h, "hour"))
        if (m > 0) add(plural(m, "minute"))
        if (s > 0 || isEmpty()) add(plural(s, "second"))
    }.joinToString(" ")
}

/** A rest time to type over: "1:00" for 60; minutes aren't padded. */
internal fun formatRestTime(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / SECONDS_PER_MINUTE, s % SECONDS_PER_MINUTE)
}

/** Seconds in "m:ss" or plain seconds ("90"); null when it isn't a time. */
internal fun parseRestTime(text: String): Int? {
    val parts = text.trim().split(':')
    val minutes = if (parts.size == 2) parts[0].ifEmpty { "0" }.toIntOrNull() else 0
    // Plain seconds can be any number; after a colon they stop at 59.
    val seconds = parts.last().toIntOrNull()?.takeIf { parts.size == 1 || it < SECONDS_PER_MINUTE }
    return if (parts.size > 2 || minutes == null || seconds == null) null else minutes * SECONDS_PER_MINUTE + seconds
}
