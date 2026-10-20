package com.example.offlinereminder.presentation.dashboard

import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent

/**
 * Immutable UI State for the Smart Dashboard screen.
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val todayOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val overdueOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val tomorrowOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val upcomingOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val errorMessage: String? = null
) {
    val totalActionableCount: Int
        get() = overdueOccurrences.size + todayOccurrences.size

    val isEmpty: Boolean
        get() = !isLoading &&
            overdueOccurrences.isEmpty() &&
            todayOccurrences.isEmpty() &&
            tomorrowOccurrences.isEmpty() &&
            upcomingOccurrences.isEmpty()
}
