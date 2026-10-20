package com.example.offlinereminder.core.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Common date and time utilities for offline reminder calculations.
 */
object DateTimeUtils {

    val ISO_LOCAL_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    val ISO_LOCAL_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Clamps a day-of-month to the maximum valid day in that target month.
     * E.g. target 31 in April (30 days) -> returns April 30.
     * E.g. target 31 in Feb 2026 (28 days) -> returns Feb 28.
     * E.g. target 29 in Feb 2028 (leap year) -> returns Feb 29.
     */
    fun clampDayOfMonth(year: Int, month: Int, desiredDay: Int): LocalDate {
        val yearMonth = YearMonth.of(year, month)
        val validDay = desiredDay.coerceIn(1, yearMonth.lengthOfMonth())
        return LocalDate.of(year, month, validDay)
    }

    /**
     * Handles Feb 29 yearly recurrence for non-leap years.
     * In non-leap years, clamps Feb 29 to Feb 28.
     */
    fun clampFeb29Yearly(year: Int): LocalDate {
        val isLeap = YearMonth.of(year, 2).isLeapYear
        val day = if (isLeap) 29 else 28
        return LocalDate.of(year, 2, day)
    }

    /**
     * Converts a [LocalDateTime] in a given [ZoneId] to Unix epoch milliseconds.
     */
    fun toEpochMillis(localDateTime: LocalDateTime, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return localDateTime.atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Converts Unix epoch milliseconds to a [LocalDateTime] in the given [ZoneId].
     */
    fun fromEpochMillis(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()
    }

    /**
     * Calculates countdown components between [from] and [to].
     */
    fun calculateCountdown(from: LocalDateTime, to: LocalDateTime): CountdownResult {
        val isPast = from.isAfter(to)
        val duration = if (isPast) {
            Duration.between(to, from)
        } else {
            Duration.between(from, to)
        }

        val totalDays = duration.toDays()
        val totalHours = duration.toHours() % 24
        val totalMinutes = duration.toMinutes() % 60
        val totalSeconds = duration.seconds % 60

        return CountdownResult(
            days = totalDays,
            hours = totalHours,
            minutes = totalMinutes,
            seconds = totalSeconds,
            isPast = isPast
        )
    }

    data class CountdownResult(
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val isPast: Boolean
    )
}
