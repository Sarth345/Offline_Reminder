package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.DashboardOccurrences
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain use case to project occurrences for the Smart Dashboard / Today View.
 * Bounded on-demand recurrence calculation:
 * - Overdue: pending/overdue occurrences before now.
 * - Today: actionable pending occurrences scheduled for today.
 * - Tomorrow: actionable occurrences scheduled for tomorrow.
 * - Upcoming: actionable occurrences beyond tomorrow within the horizon.
 * - Completed-Today: occurrences completed today.
 * - Favorites: actionable favorite occurrences.
 * - High-Priority: actionable high/critical priority occurrences.
 */
class GetDashboardOccurrencesUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider
) {
    operator fun invoke(
        lookbackDays: Long = 14,
        lookaheadDays: Long = 14
    ): Flow<DashboardOccurrences> = eventRepository.getAllActiveEvents().map { events ->
        val nowLdt = timeProvider.nowLocalDateTime()
        val today = timeProvider.nowLocalDate()
        val tomorrow = today.plusDays(1)
        val from = nowLdt.minusDays(lookbackDays)
        val to = nowLdt.plusDays(lookaheadDays)

        val exceptions = occurrenceExceptionRepository.getExceptionsInRange(from, to)

        val allItems = mutableListOf<EventOccurrenceWithEvent>()
        for (event in events) {
            val eventOccurrences = recurrenceEngine.projectOccurrences(
                event = event,
                from = from,
                to = to,
                exceptions = exceptions,
                evaluateOverdue = true,
                timeProvider = timeProvider
            )
            for (occ in eventOccurrences) {
                allItems.add(EventOccurrenceWithEvent(occurrence = occ, event = event))
            }
        }

        // Completed Today: occurrences completed today (either completedAt is today, or scheduled for today and completed)
        val completedToday = allItems.filter { item ->
            item.occurrence.status == OccurrenceStatus.COMPLETED &&
                if (item.occurrence.completedAt != null) {
                    item.occurrence.completedAt.atZone(timeProvider.systemZoneId()).toLocalDate() == today
                } else {
                    item.occurrence.effectiveStartDateTime.toLocalDate() == today
                }
        }.sortedByDescending { it.occurrence.effectiveStartDateTime }

        // Overdue: actionable items strictly before now or marked OVERDUE
        val overdue = allItems.filter { item ->
            item.occurrence.status != OccurrenceStatus.COMPLETED &&
                item.occurrence.status != OccurrenceStatus.SKIPPED &&
                item.occurrence.status != OccurrenceStatus.EXCLUDED &&
                (
                    item.occurrence.status == OccurrenceStatus.OVERDUE ||
                        (item.occurrence.status == OccurrenceStatus.PENDING && item.occurrence.effectiveStartDateTime.isBefore(nowLdt))
                )
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        // Today: actionable items for today that are not overdue
        val todayItems = allItems.filter { item ->
            item.occurrence.effectiveStartDateTime.toLocalDate() == today &&
                item.occurrence.status != OccurrenceStatus.OVERDUE &&
                item.occurrence.status != OccurrenceStatus.EXCLUDED &&
                item.occurrence.status != OccurrenceStatus.COMPLETED &&
                item.occurrence.status != OccurrenceStatus.SKIPPED &&
                !item.occurrence.effectiveStartDateTime.isBefore(nowLdt)
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        // Tomorrow: actionable items for tomorrow
        val tomorrowItems = allItems.filter { item ->
            item.occurrence.effectiveStartDateTime.toLocalDate() == tomorrow &&
                item.occurrence.status != OccurrenceStatus.OVERDUE &&
                item.occurrence.status != OccurrenceStatus.EXCLUDED &&
                item.occurrence.status != OccurrenceStatus.COMPLETED &&
                item.occurrence.status != OccurrenceStatus.SKIPPED
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        // Upcoming: actionable items beyond tomorrow
        val upcomingItems = allItems.filter { item ->
            item.occurrence.effectiveStartDateTime.toLocalDate().isAfter(tomorrow) &&
                item.occurrence.status != OccurrenceStatus.OVERDUE &&
                item.occurrence.status != OccurrenceStatus.EXCLUDED &&
                item.occurrence.status != OccurrenceStatus.COMPLETED &&
                item.occurrence.status != OccurrenceStatus.SKIPPED
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        // Favorites: actionable occurrences of favorite events
        val favoriteItems = (overdue + todayItems + tomorrowItems + upcomingItems)
            .filter { it.event.isFavorite }
            .distinctBy { it.occurrence.occurrenceId }
            .sortedBy { it.occurrence.effectiveStartDateTime }

        // High Priority: actionable high or critical priority occurrences from overdue and today
        val highPriorityItems = (overdue + todayItems)
            .filter { it.event.priority == Priority.HIGH || it.event.priority == Priority.CRITICAL }
            .distinctBy { it.occurrence.occurrenceId }
            .sortedBy { it.occurrence.effectiveStartDateTime }

        DashboardOccurrences(
            overdue = overdue,
            today = todayItems,
            tomorrow = tomorrowItems,
            upcoming = upcomingItems,
            completedToday = completedToday,
            favorites = favoriteItems,
            highPriority = highPriorityItems
        )
    }
}
