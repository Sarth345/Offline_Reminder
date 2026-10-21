package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/**
 * Domain use case to project occurrences within a bounded calendar horizon (e.g., month view).
 * Pure Kotlin, zero Android/Compose/Room dependencies.
 */
class GetCalendarOccurrencesUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider
) {
    operator fun invoke(from: LocalDateTime, to: LocalDateTime): Flow<List<EventOccurrenceWithEvent>> =
        combine(
            eventRepository.getAllActiveEvents(),
            occurrenceExceptionRepository.getAllExceptionsFlow()
        ) { events, allExceptions ->
            if (to.isBefore(from)) return@combine emptyList()

            val exceptions = allExceptions.filter { exc ->
                val start = exc.originalStartDateTime
                val resched = exc.rescheduledStartDateTime
                (!start.isBefore(from) && !start.isAfter(to)) ||
                    (resched != null && !resched.isBefore(from) && !resched.isAfter(to))
            }
            val results = mutableListOf<EventOccurrenceWithEvent>()

            for (event in events) {
                val occurrences = recurrenceEngine.projectOccurrences(
                    event = event,
                    from = from,
                    to = to,
                    exceptions = exceptions,
                    evaluateOverdue = true,
                    timeProvider = timeProvider
                )
                for (occ in occurrences) {
                    results.add(EventOccurrenceWithEvent(occurrence = occ, event = event))
                }
            }

            results.sortedBy { it.occurrence.effectiveStartDateTime }
        }
}
