package com.example.offlinereminder.presentation.components

import com.example.offlinereminder.domain.model.EventDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateTimeFormatters {
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a")

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMATTER)

    fun formatTime(time: LocalTime): String = time.format(TIME_FORMATTER)

    fun formatDateTime(dateTime: LocalDateTime): String = dateTime.format(DATE_TIME_FORMATTER)

    fun formatEventDateTime(temporal: EventDateTime): String = when (temporal) {
        is EventDateTime.DateOnly -> formatDate(temporal.startDate)
        is EventDateTime.DateTime -> formatDateTime(temporal.dateTime)
        is EventDateTime.DateRange -> "${formatDate(temporal.startDate)} – ${formatDate(temporal.endDate)}"
        is EventDateTime.DateTimeRange -> "${formatDateTime(temporal.startDateTime)} – ${formatDateTime(temporal.endDateTime)}"
    }

    fun formatCountdown(now: LocalDateTime, target: LocalDateTime): String {
        val isPast = now.isAfter(target)
        val minutes = ChronoUnit.MINUTES.between(
            if (isPast) target else now,
            if (isPast) now else target
        )
        val hours = minutes / 60
        val days = hours / 24

        val text = when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            else -> "${minutes}m"
        }

        return if (isPast) "Overdue by $text" else "In $text"
    }
}
