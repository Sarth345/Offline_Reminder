package com.example.offlinereminder.domain.statistics

import com.example.offlinereminder.domain.model.Priority
import java.time.DayOfWeek

/**
 * Domain statistics snapshot computed locally and deterministically.
 */
data class EventStatistics(
    val totalActiveEvents: Int,
    val totalArchivedEvents: Int,
    val totalTrashedEvents: Int,
    val totalRecurringEvents: Int,
    val totalOneTimeEvents: Int,
    val completedOccurrencesCount: Int,
    val skippedOccurrencesCount: Int,
    val overdueOccurrencesCount: Int,
    val pendingOccurrencesCount: Int,
    val completionRatePercentage: Float, // 0.0 - 100.0
    val onTimeCompletionRatePercentage: Float,
    val eventsThisWeekCount: Int,
    val eventsThisMonthCount: Int,
    val categoryBreakdown: List<CategoryStatistic>,
    val priorityBreakdown: Map<Priority, Int>,
    val dayOfWeekDistribution: Map<DayOfWeek, Int>
)

data class CategoryStatistic(
    val categoryId: String,
    val categoryName: String,
    val color: Int,
    val count: Int,
    val percentage: Float
)
