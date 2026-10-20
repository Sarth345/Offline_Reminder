package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.DashboardOccurrences
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain use case to project occurrences for the Smart Dashboard / Today View.
 * Bounded on-demand recurrence calculation:
 * - Overdue: occurrences before now that remain pending.
 * - Today: occurrences scheduled for today.
 * - Tomorrow: occurrences scheduled for tomorrow.
 * - Upcoming: occurrences beyond tomorrow within the horizon.
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

        val overdue = allItems.filter {
            it.occurrence.status == OccurrenceStatus.OVERDUE ||
                (it.occurrence.status == OccurrenceStatus.PENDING && it.occurrence.effectiveStartDateTime.isBefore(nowLdt))
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        val todayItems = allItems.filter {
            it.occurrence.effectiveStartDateTime.toLocalDate() == today &&
                it.occurrence.status != OccurrenceStatus.OVERDUE &&
                it.occurrence.status != OccurrenceStatus.EXCLUDED
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        val tomorrowItems = allItems.filter {
            it.occurrence.effectiveStartDateTime.toLocalDate() == tomorrow &&
                it.occurrence.status != OccurrenceStatus.OVERDUE &&
                it.occurrence.status != OccurrenceStatus.EXCLUDED
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        val upcomingItems = allItems.filter {
            it.occurrence.effectiveStartDateTime.toLocalDate().isAfter(tomorrow) &&
                it.occurrence.status != OccurrenceStatus.OVERDUE &&
                it.occurrence.status != OccurrenceStatus.EXCLUDED
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        DashboardOccurrences(
            overdue = overdue,
            today = todayItems,
            tomorrow = tomorrowItems,
            upcoming = upcomingItems
        )
    }
}
