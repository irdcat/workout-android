package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.workout.TrainingWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// The UI copy is English, so dates use English names.

private val UiLocale: Locale = Locale.ENGLISH
private val DayMonth = DateTimeFormatter.ofPattern("d MMM", UiLocale)
private val Day = DateTimeFormatter.ofPattern("d", UiLocale)
private val Time = DateTimeFormatter.ofPattern("H:mm", UiLocale)

/** "1 – 7 Jun", or "29 Jun – 5 Jul" across months. */
fun formatWeekRange(start: LocalDate, end: LocalDate): String =
    if (YearMonth.from(start) == YearMonth.from(end)) {
        "${start.format(Day)} – ${end.format(DayMonth)}"
    } else {
        "${start.format(DayMonth)} – ${end.format(DayMonth)}"
    }

val TrainingWeek.range: String get() = formatWeekRange(start, end)

/** "19 Oct". */
fun formatDayMonth(date: LocalDate): String = date.format(DayMonth)

/** "June 2026". */
fun formatMonthYear(month: YearMonth): String = "${monthName(month)} ${month.year}"

/** "June". */
fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL, UiLocale)

/** "Jun". */
fun shortMonthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT, UiLocale)

/** "14:32" in the phone's time zone. */
fun formatTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).format(Time)

/** "Today 14:32", "Yesterday 9:05" or "12 Oct 14:32"; "Never" without a sync. */
fun formatSyncedAt(instant: Instant?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    if (instant == null) return "Never"
    val date = instant.atZone(zone).toLocalDate()
    val day = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> formatDayMonth(date)
    }
    return "$day ${formatTime(instant, zone)}"
}
