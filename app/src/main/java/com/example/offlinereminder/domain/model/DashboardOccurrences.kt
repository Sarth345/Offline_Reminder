package com.example.offlinereminder.domain.model

/**
 * Grouped presentation model for the Smart Dashboard / Today View.
 * Groups projected occurrences into Overdue, Today, Tomorrow, Upcoming,
 * Completed-Today, Favorites, and High-Priority partitions with summary metrics.
 */
data class DashboardOccurrences(
    val overdue: List<EventOccurrenceWithEvent> = emptyList(),
    val today: List<EventOccurrenceWithEvent> = emptyList(),
    val tomorrow: List<EventOccurrenceWithEvent> = emptyList(),
    val upcoming: List<EventOccurrenceWithEvent> = emptyList(),
    val completedToday: List<EventOccurrenceWithEvent> = emptyList(),
    val favorites: List<EventOccurrenceWithEvent> = emptyList(),
    val highPriority: List<EventOccurrenceWithEvent> = emptyList()
) {
    val totalActionableCount: Int
        get() = overdue.size + today.size

    val completedCountToday: Int
        get() = completedToday.size

    val totalTodayCount: Int
        get() = today.size + completedToday.size

    val highPriorityCount: Int
        get() = highPriority.size

    val favoriteCount: Int
        get() = favorites.size

    val progress: Float
        get() = if (totalTodayCount > 0) completedCountToday.toFloat() / totalTodayCount.toFloat() else 0f
}
