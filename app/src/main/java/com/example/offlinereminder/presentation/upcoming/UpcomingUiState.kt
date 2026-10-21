package com.example.offlinereminder.presentation.upcoming

import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import java.time.LocalDate

/**
 * Immutable UI State for the Upcoming Agenda / Timeline screen.
 */
data class UpcomingUiState(
    val isLoading: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val horizonDays: Long = 14,
    val includeCompleted: Boolean = true,
    val includeOverdue: Boolean = true,
    val occurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val occurrencesByDate: Map<LocalDate, List<EventOccurrenceWithEvent>> = emptyMap(),
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && occurrences.isEmpty()
}
