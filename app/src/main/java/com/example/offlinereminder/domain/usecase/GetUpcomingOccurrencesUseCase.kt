package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/**
 * Domain use case to project upcoming active occurrences within a bounded horizon for the Agenda/Timeline.
 * Supports date navigation from a specific start date-time, configurable status filtering
 * (completed, skipped, overdue, pending), and deterministic recurrence projection.
 */
class GetUpcomingOccurrencesUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider
) {
    operator fun invoke(
        horizonDays: Long = 30,
        fromDateTime: LocalDateTime? = null,
        includeCompleted: Boolean = false,
        includeSkipped: Boolean = false,
        includeOverdue: Boolean = false
    ): Flow<List<EventOccurrenceWithEvent>> =
        eventRepository.getAllActiveEvents().map { events ->
            val from = fromDateTime ?: timeProvider.nowLocalDateTime()
            val to = from.plusDays(horizonDays)
            val exceptions = occurrenceExceptionRepository.getExceptionsInRange(from, to)

            val result = mutableListOf<EventOccurrenceWithEvent>()
            for (event in events) {
                val occurrences = recurrenceEngine.projectOccurrences(
                    event = event,
                    from = from,
                    to = to,
                    exceptions = exceptions,
                    evaluateOverdue = includeOverdue,
                    timeProvider = timeProvider
                )
                for (occ in occurrences) {
                    val statusMatches = when (occ.status) {
                        OccurrenceStatus.PENDING, OccurrenceStatus.RESCHEDULED -> true
                        OccurrenceStatus.OVERDUE -> includeOverdue
                        OccurrenceStatus.COMPLETED -> includeCompleted
                        OccurrenceStatus.SKIPPED -> includeSkipped
                        OccurrenceStatus.EXCLUDED -> false
                    }
                    if (statusMatches) {
                        result.add(EventOccurrenceWithEvent(occurrence = occ, event = event))
                    }
                }
            }
            result.sortedWith(
                compareBy(
                    { it.occurrence.effectiveStartDateTime },
                    { it.occurrence.effectiveEndDateTime ?: it.occurrence.effectiveStartDateTime }
                )
            )
        }
}
