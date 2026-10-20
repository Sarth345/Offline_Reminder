package com.example.offlinereminder.domain.model

/**
 * Grouped presentation model for the Smart Dashboard / Today View.
 * Groups projected occurrences into Overdue, Today, Tomorrow, and Upcoming partitions.
 */
data class DashboardOccurrences(
    val overdue: List<EventOccurrenceWithEvent> = emptyList(),
    val today: List<EventOccurrenceWithEvent> = emptyList(),
    val tomorrow: List<EventOccurrenceWithEvent> = emptyList(),
    val upcoming: List<EventOccurrenceWithEvent> = emptyList()
)
