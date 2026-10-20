package com.example.offlinereminder.presentation.calendar

import com.example.offlinereminder.domain.model.EventOccurrence
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * User actions for the Calendar screen (Unidirectional Data Flow).
 */
sealed interface CalendarAction {
    data object PreviousMonth : CalendarAction
    data object NextMonth : CalendarAction
    data class SelectDate(val date: LocalDate) : CalendarAction
    data object JumpToToday : CalendarAction
    data object Refresh : CalendarAction
    data object ClearError : CalendarAction
    data class CompleteOccurrence(
        val occurrence: EventOccurrence,
        val actualExecutionDateTime: LocalDateTime? = null
    ) : CalendarAction
    data class SkipOccurrence(val occurrence: EventOccurrence) : CalendarAction
    data class RescheduleOccurrence(
        val occurrence: EventOccurrence,
        val newStartDateTime: LocalDateTime
    ) : CalendarAction
}
