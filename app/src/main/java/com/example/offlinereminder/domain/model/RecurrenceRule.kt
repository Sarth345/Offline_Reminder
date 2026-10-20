package com.example.offlinereminder.domain.model

import com.example.offlinereminder.core.result.Result
import java.time.DayOfWeek
import java.time.LocalDate

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

sealed interface RecurrenceEnd {
    data object Never : RecurrenceEnd
    data class UntilDate(val date: LocalDate) : RecurrenceEnd
    data class Count(val maxOccurrences: Int) : RecurrenceEnd
}

/**
 * Definition of recurrence for a recurring event.
 * Follows iCalendar RFC-5545 semantics.
 * Does NOT contain occurrence execution state or alarms.
 */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int? = null,
    val setPosition: Int? = null,
    val monthOfYear: Int? = null,
    val endCondition: RecurrenceEnd = RecurrenceEnd.Never,
    val excludedDates: Set<LocalDate> = emptySet()
) {

    fun withExcludedDate(date: LocalDate): RecurrenceRule =
        copy(excludedDates = excludedDates + date)

    fun isExcluded(date: LocalDate): Boolean =
        excludedDates.contains(date)

    fun validate(): Result<Unit> {
        if (interval < 1) {
            return Result.Failure(IllegalArgumentException("Recurrence interval must be at least 1, but was $interval"))
        }

        if (dayOfMonth != null && dayOfMonth !in 1..31) {
            return Result.Failure(IllegalArgumentException("dayOfMonth must be between 1 and 31, but was $dayOfMonth"))
        }

        if (monthOfYear != null && monthOfYear !in 1..12) {
            return Result.Failure(IllegalArgumentException("monthOfYear must be between 1 and 12, but was $monthOfYear"))
        }

        if (setPosition != null && (setPosition == 0 || setPosition !in -5..5)) {
            return Result.Failure(IllegalArgumentException("setPosition must be in [-5..5] excluding 0, but was $setPosition"))
        }

        if (frequency == RecurrenceFrequency.WEEKLY && daysOfWeek.isEmpty()) {
            return Result.Failure(IllegalArgumentException("Weekly recurrence must specify at least one day of the week"))
        }

        if (endCondition is RecurrenceEnd.Count && endCondition.maxOccurrences < 1) {
            return Result.Failure(IllegalArgumentException("Recurrence Count must be at least 1, but was ${endCondition.maxOccurrences}"))
        }

        return Result.Success(Unit)
    }

    companion object {
        fun daily(interval: Int = 1, endCondition: RecurrenceEnd = RecurrenceEnd.Never): RecurrenceRule =
            RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = interval, endCondition = endCondition)

        fun weekly(
            daysOfWeek: Set<DayOfWeek>,
            interval: Int = 1,
            endCondition: RecurrenceEnd = RecurrenceEnd.Never
        ): RecurrenceRule =
            RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, interval = interval, daysOfWeek = daysOfWeek, endCondition = endCondition)

        fun weekdays(endCondition: RecurrenceEnd = RecurrenceEnd.Never): RecurrenceRule =
            weekly(
                daysOfWeek = setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
                ),
                endCondition = endCondition
            )

        fun weekends(endCondition: RecurrenceEnd = RecurrenceEnd.Never): RecurrenceRule =
            weekly(
                daysOfWeek = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                endCondition = endCondition
            )

        fun monthly(
            dayOfMonth: Int,
            interval: Int = 1,
            endCondition: RecurrenceEnd = RecurrenceEnd.Never
        ): RecurrenceRule =
            RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, interval = interval, dayOfMonth = dayOfMonth, endCondition = endCondition)

        fun yearly(
            monthOfYear: Int,
            dayOfMonth: Int,
            interval: Int = 1,
            endCondition: RecurrenceEnd = RecurrenceEnd.Never
        ): RecurrenceRule =
            RecurrenceRule(
                frequency = RecurrenceFrequency.YEARLY,
                interval = interval,
                monthOfYear = monthOfYear,
                dayOfMonth = dayOfMonth,
                endCondition = endCondition
            )
    }
}
