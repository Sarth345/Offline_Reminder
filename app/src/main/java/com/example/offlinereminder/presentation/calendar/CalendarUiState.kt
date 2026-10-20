package com.example.offlinereminder.presentation.calendar

import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import java.time.LocalDate
import java.time.YearMonth

/**
 * Immutable UI state for the Calendar screen.
 */
data class CalendarUiState(
    val displayedYearMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val monthOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val occurrencesByDate: Map<LocalDate, List<EventOccurrenceWithEvent>> = emptyMap(),
    val selectedDateOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val datesWithOccurrences: Set<LocalDate> = emptySet(),
    val errorMessage: String? = null
) {
    val isEmptySelectedDate: Boolean
        get() = !isLoading && selectedDateOccurrences.isEmpty()
}
