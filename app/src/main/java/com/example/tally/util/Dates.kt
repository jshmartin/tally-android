package com.example.tally.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** [start, end) in epoch millis, in the phone's time zone. */
fun YearMonth.bounds(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
    atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
        plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d")
private val DAY_WITH_YEAR = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val TIME = DateTimeFormatter.ofPattern("h:mm a")

fun YearMonth.label(): String = format(MONTH)

/** "Today", "Yesterday", "Tue, Nov 10", or with the year when it isn't this year. */
fun LocalDate.dayLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(if (year == today.year) DAY else DAY_WITH_YEAR)
}

fun Long.timeLabel(zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(this).atZone(zone).format(TIME)

fun Long.dateTimeLabel(): String = "${toLocalDate().dayLabel()}, ${timeLabel()}"
