package com.example.offlinereminder.domain.model

import com.example.offlinereminder.core.time.DateTimeUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Domain representation of an event's temporal bounds.
 * Accommodates all date modes specified in the requirements:
 * 1. Date-only (e.g. Birthday, Anniversary)
 * 2. Date + time (e.g. Meeting, Doctor appointment)
 * 3. Date range (e.g. Vacation, Multi-day trip)
 * 4. Date range + time (e.g. Conference sessions)
 */
sealed interface EventDateTime {

    val startDate: LocalDate
    val isAllDay: Boolean

    fun startLocalDateTime(defaultTime: LocalTime = LocalTime.MIDNIGHT): LocalDateTime
    fun endLocalDateTime(defaultTime: LocalTime = LocalTime.MIDNIGHT): LocalDateTime? = when (this) {
        is DateOnly -> null
        is DateTime -> null
        is DateRange -> endDate.atTime(defaultTime)
        is DateTimeRange -> endDateTime
    }
    fun startEpochMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long

    /**
     * Discrete date without a specific time of day.
     */
    data class DateOnly(
        override val startDate: LocalDate
    ) : EventDateTime {
        override val isAllDay: Boolean get() = true

        override fun startLocalDateTime(defaultTime: LocalTime): LocalDateTime =
            startDate.atTime(defaultTime)

        override fun startEpochMillis(zoneId: ZoneId): Long =
            DateTimeUtils.toEpochMillis(startDate.atStartOfDay(), zoneId)
    }

    /**
     * Specific local date and time.
     */
    data class DateTime(
        val dateTime: LocalDateTime,
        val zoneId: ZoneId = ZoneId.systemDefault()
    ) : EventDateTime {
        override val startDate: LocalDate get() = dateTime.toLocalDate()
        override val isAllDay: Boolean get() = false

        override fun startLocalDateTime(defaultTime: LocalTime): LocalDateTime = dateTime

        override fun startEpochMillis(zoneId: ZoneId): Long =
            DateTimeUtils.toEpochMillis(dateTime, this.zoneId)
    }

    /**
     * Multi-day date range without specific start/end times.
     */
    data class DateRange(
        override val startDate: LocalDate,
        val endDate: LocalDate
    ) : EventDateTime {
        init {
            require(!endDate.isBefore(startDate)) {
                "endDate ($endDate) must not be before startDate ($startDate)"
            }
        }

        override val isAllDay: Boolean get() = true

        override fun startLocalDateTime(defaultTime: LocalTime): LocalDateTime =
            startDate.atTime(defaultTime)

        override fun startEpochMillis(zoneId: ZoneId): Long =
            DateTimeUtils.toEpochMillis(startDate.atStartOfDay(), zoneId)
    }

    /**
     * Multi-day or same-day range with specific start and end times.
     */
    data class DateTimeRange(
        val startDateTime: LocalDateTime,
        val endDateTime: LocalDateTime,
        val zoneId: ZoneId = ZoneId.systemDefault()
    ) : EventDateTime {
        init {
            require(!endDateTime.isBefore(startDateTime)) {
                "endDateTime ($endDateTime) must not be before startDateTime ($startDateTime)"
            }
        }

        override val startDate: LocalDate get() = startDateTime.toLocalDate()
        override val isAllDay: Boolean get() = false

        override fun startLocalDateTime(defaultTime: LocalTime): LocalDateTime = startDateTime

        override fun startEpochMillis(zoneId: ZoneId): Long =
            DateTimeUtils.toEpochMillis(startDateTime, this.zoneId)
    }
}
