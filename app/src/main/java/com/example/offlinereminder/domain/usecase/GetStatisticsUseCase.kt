package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.statistics.CategoryStatistic
import com.example.offlinereminder.domain.statistics.EventStatistics
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

class GetStatisticsUseCase(
    private val eventRepository: EventRepository,
    private val categoryRepository: CategoryRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(): EventStatistics {
        val allEvents = eventRepository.getAllEvents().first()
        val allCategories = categoryRepository.getAllCategories().first()
        val exceptions = occurrenceExceptionRepository.getAllExceptions()

        val activeEvents = allEvents.filter { it.lifecycleStatus == EventLifecycleStatus.ACTIVE }
        val archivedEvents = allEvents.filter { it.lifecycleStatus == EventLifecycleStatus.ARCHIVED }
        val trashedEvents = allEvents.filter { it.lifecycleStatus == EventLifecycleStatus.TRASHED }

        val recurringEvents = activeEvents.filter { it.isRecurring }
        val oneTimeEvents = activeEvents.filter { it.isOneTime }

        val completedExceptions = exceptions.filter { it.status == OccurrenceStatus.COMPLETED }
        val skippedExceptions = exceptions.filter { it.status == OccurrenceStatus.SKIPPED }
        val completedEventIds = completedExceptions.map { it.eventId }.toSet()

        val nowLdt = timeProvider.nowLocalDateTime()
        val today = timeProvider.nowLocalDate()
        val zone = timeProvider.systemZoneId()

        // Overdue one-time events: active, start time before now, not completed
        val overdueOneTimeEvents = oneTimeEvents.filter { event ->
            val eventStart = event.temporal.startLocalDateTime()
            eventStart.isBefore(nowLdt) && !completedEventIds.contains(event.id)
        }

        val completedCount = completedExceptions.size
        val skippedCount = skippedExceptions.size
        val overdueCount = overdueOneTimeEvents.size
        val pendingCount = (oneTimeEvents.size - overdueCount - oneTimeEvents.count { completedEventIds.contains(it.id) })
            .coerceAtLeast(0)

        val totalTracked = completedCount + overdueCount
        val completionRate = if (totalTracked > 0) {
            (completedCount.toFloat() / totalTracked) * 100f
        } else if (activeEvents.isNotEmpty()) {
            100f
        } else {
            0f
        }

        // On-time completion: completed within 2 hours after scheduled start or before it
        var onTimeCount = 0
        for (comp in completedExceptions) {
            val schedStart = comp.originalStartDateTime.atZone(comp.originalZoneId).toInstant()
            val compAt = comp.completedAt
            if (compAt == null || !compAt.isAfter(schedStart.plusSeconds(7200))) {
                onTimeCount++
            }
        }
        val onTimeRate = if (completedCount > 0) {
            (onTimeCount.toFloat() / completedCount) * 100f
        } else {
            100f
        }

        // Week and Month counts
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val startOfMonth = today.withDayOfMonth(1)
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())

        val eventsThisWeek = activeEvents.count { event ->
            val eventDate = event.temporal.startDate
            !eventDate.isBefore(startOfWeek) && !eventDate.isAfter(endOfWeek)
        }
        val eventsThisMonth = activeEvents.count { event ->
            val eventDate = event.temporal.startDate
            !eventDate.isBefore(startOfMonth) && !eventDate.isAfter(endOfMonth)
        }

        // Category breakdown
        val categoryMap = allCategories.associateBy { it.id }
        val categoryCounts = activeEvents.groupBy { it.categoryId }
        val categoryBreakdown = categoryCounts.map { (catId, eventsInCat) ->
            val category = categoryMap[catId]
            val catName = category?.name ?: "Uncategorized"
            val catColor = category?.color ?: 0xFF4285F4.toInt()
            val count = eventsInCat.size
            val percentage = if (activeEvents.isNotEmpty()) (count.toFloat() / activeEvents.size) * 100f else 0f
            CategoryStatistic(
                categoryId = catId,
                categoryName = catName,
                color = catColor,
                count = count,
                percentage = percentage
            )
        }.sortedByDescending { it.count }

        // Priority breakdown
        val priorityBreakdown = Priority.values().associateWith { p ->
            activeEvents.count { it.priority == p }
        }

        // Day of week distribution
        val dayOfWeekDistribution = DayOfWeek.values().associateWith { dow ->
            activeEvents.count { it.temporal.startDate.dayOfWeek == dow }
        }

        return EventStatistics(
            totalActiveEvents = activeEvents.size,
            totalArchivedEvents = archivedEvents.size,
            totalTrashedEvents = trashedEvents.size,
            totalRecurringEvents = recurringEvents.size,
            totalOneTimeEvents = oneTimeEvents.size,
            completedOccurrencesCount = completedCount,
            skippedOccurrencesCount = skippedCount,
            overdueOccurrencesCount = overdueCount,
            pendingOccurrencesCount = pendingCount,
            completionRatePercentage = completionRate,
            onTimeCompletionRatePercentage = onTimeRate,
            eventsThisWeekCount = eventsThisWeek,
            eventsThisMonthCount = eventsThisMonth,
            categoryBreakdown = categoryBreakdown,
            priorityBreakdown = priorityBreakdown,
            dayOfWeekDistribution = dayOfWeekDistribution
        )
    }
}
