package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.DashboardOccurrences
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain use case to project occurrences for the Smart Dashboard / Today View.
 * Bounded on-demand recurrence calculation:
 * - Shows strictly occurrences belonging to today.
 * - OVERDUE: Today's occurrences whose scheduled time has passed and which remain incomplete/overdue.
 * - TODAY / REMAINING: Today's occurrences that are still current/upcoming.
 * - COMPLETED TODAY: Occurrences completed today.
 * - FAVORITES / HIGH PRIORITY: Derived from today's occurrences.
 * - Never shows tomorrow or future recurring occurrences.
 */
class GetDashboardOccurrencesUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider
) {

    /**
     * Projects occurrences strictly for [today].
     */
    fun occurrencesForToday(
        today: LocalDate = timeProvider.nowLocalDate()
    ): Flow<DashboardOccurrences> = combine(
        eventRepository.getAllActiveEvents(),
        occurrenceExceptionRepository.getAllExceptionsFlow()
    ) { events, allExceptions ->
        val nowLdt = timeProvider.nowLocalDateTime()
        val from = today.atStartOfDay()
        val to = today.atTime(LocalTime.MAX)

        val exceptions = allExceptions.filter { exc ->
            val start = exc.originalStartDateTime
            val resched = exc.rescheduledStartDateTime
            (!start.isBefore(from) && !start.isAfter(to)) ||
                (resched != null && !resched.isBefore(from) && !resched.isAfter(to))
        }

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
                if (occ.effectiveStartDateTime.toLocalDate() == today) {
                    allItems.add(EventOccurrenceWithEvent(occurrence = occ, event = event))
                }
            }
        }

        // Completed Today: occurrences completed today (either completedAt is today, or scheduled for today and completed)
        val completedToday = mutableListOf<EventOccurrenceWithEvent>()
        for (item in allItems) {
            if (item.occurrence.status == OccurrenceStatus.COMPLETED) {
                val compDate = if (item.occurrence.completedAt != null) {
                    item.occurrence.completedAt.atZone(timeProvider.systemZoneId()).toLocalDate()
                } else {
                    item.occurrence.effectiveStartDateTime.toLocalDate()
                }
                if (compDate == today) {
                    completedToday.add(item)
                }
            }
        }
        for (exc in allExceptions) {
            if (exc.status == OccurrenceStatus.COMPLETED && exc.completedAt != null) {
                val compDate = exc.completedAt.atZone(timeProvider.systemZoneId()).toLocalDate()
                if (compDate == today && completedToday.none {
                        it.event.id == exc.eventId &&
                            it.occurrence.originalStartDateTime == exc.originalStartDateTime &&
                            it.occurrence.zoneId == exc.originalZoneId
                    }
                ) {
                    val event = events.firstOrNull { it.id == exc.eventId } ?: continue
                    val occ = EventOccurrence(
                        eventId = event.id,
                        originalStartDateTime = exc.originalStartDateTime,
                        zoneId = exc.originalZoneId,
                        rescheduledStartDateTime = exc.rescheduledStartDateTime,
                        rescheduledEndDateTime = exc.rescheduledEndDateTime,
                        rescheduledZoneId = exc.rescheduledZoneId,
                        status = OccurrenceStatus.COMPLETED,
                        completedAt = exc.completedAt
                    )
                    completedToday.add(EventOccurrenceWithEvent(occurrence = occ, event = event))
                }
            }
        }
        completedToday.sortByDescending { it.occurrence.effectiveStartDateTime }

        // Overdue: today's occurrences strictly before now or marked OVERDUE
        val overdue = allItems.filter { item ->
            item.occurrence.status != OccurrenceStatus.COMPLETED &&
                item.occurrence.status != OccurrenceStatus.SKIPPED &&
                item.occurrence.status != OccurrenceStatus.EXCLUDED &&
                (
                    item.occurrence.status == OccurrenceStatus.OVERDUE ||
                        ((item.occurrence.status == OccurrenceStatus.PENDING || item.occurrence.status == OccurrenceStatus.RESCHEDULED) &&
                            item.occurrence.effectiveStartDateTime.isBefore(nowLdt))
                )
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        // Today / Remaining: today's occurrences that are current/upcoming (not overdue)
        val todayItems = allItems.filter { item ->
            item.occurrence.status != OccurrenceStatus.OVERDUE &&
                item.occurrence.status != OccurrenceStatus.EXCLUDED &&
                item.occurrence.status != OccurrenceStatus.COMPLETED &&
                item.occurrence.status != OccurrenceStatus.SKIPPED &&
                !item.occurrence.effectiveStartDateTime.isBefore(nowLdt)
        }.sortedBy { it.occurrence.effectiveStartDateTime }

        // Favorites: actionable occurrences of favorite events for today
        val favoriteItems = (overdue + todayItems)
            .filter { it.event.isFavorite }
            .distinctBy { it.occurrence.occurrenceId }
            .sortedBy { it.occurrence.effectiveStartDateTime }

        // High Priority: actionable high or critical priority occurrences for today
        val highPriorityItems = (overdue + todayItems)
            .filter { it.event.priority == Priority.HIGH || it.event.priority == Priority.CRITICAL }
            .distinctBy { it.occurrence.occurrenceId }
            .sortedBy { it.occurrence.effectiveStartDateTime }

        DashboardOccurrences(
            overdue = overdue,
            today = todayItems,
            tomorrow = emptyList(),
            upcoming = emptyList(),
            completedToday = completedToday,
            favorites = favoriteItems,
            highPriority = highPriorityItems
        )
    }

    operator fun invoke(
        lookbackDays: Long = 0,
        lookaheadDays: Long = 0
    ): Flow<DashboardOccurrences> = occurrencesForToday(timeProvider.nowLocalDate())
}
