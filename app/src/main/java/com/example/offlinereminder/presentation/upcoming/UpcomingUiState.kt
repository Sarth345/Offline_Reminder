package com.example.offlinereminder.presentation.upcoming

import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import java.time.LocalDate

enum class UpcomingMode {
    NEXT,
    OVERDUE,
    DATE
}

/**
 * Immutable UI State for the Upcoming Agenda / Timeline screen.
 */
data class UpcomingUiState(
    val isLoading: Boolean = false,
    val mode: UpcomingMode = UpcomingMode.NEXT,
    val selectedDate: LocalDate = LocalDate.now(),
    val occurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val occurrencesByDate: Map<LocalDate, List<EventOccurrenceWithEvent>> = emptyMap(),
    val errorMessage: String? = null,
    val horizonDays: Long = 14,
    val includeCompleted: Boolean = true,
    val includeOverdue: Boolean = true
) {
    val isEmpty: Boolean
        get() = !isLoading && occurrences.isEmpty()
}
