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
    val completedTodayOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val favoriteOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val highPriorityOccurrences: List<EventOccurrenceWithEvent> = emptyList(),
    val errorMessage: String? = null
) {
    val totalActionableCount: Int
        get() = overdueOccurrences.size + todayOccurrences.size

    val completedCountToday: Int
        get() = completedTodayOccurrences.size

    val totalTodayCount: Int
        get() = todayOccurrences.size + completedTodayOccurrences.size

    val progress: Float
        get() = if (totalTodayCount > 0) completedCountToday.toFloat() / totalTodayCount.toFloat() else 0f

    val isEmpty: Boolean
        get() = !isLoading &&
            overdueOccurrences.isEmpty() &&
            todayOccurrences.isEmpty() &&
            tomorrowOccurrences.isEmpty() &&
            upcomingOccurrences.isEmpty() &&
            completedTodayOccurrences.isEmpty()

    val isAllTodayCompleted: Boolean
        get() = totalTodayCount > 0 && todayOccurrences.isEmpty() && completedTodayOccurrences.isNotEmpty()
}
