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

/**
 * Domain use case to project upcoming active occurrences within a bounded horizon.
 */
class GetUpcomingOccurrencesUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider
) {
    operator fun invoke(horizonDays: Long = 30): Flow<List<EventOccurrenceWithEvent>> =
        eventRepository.getAllActiveEvents().map { events ->
            val nowLdt = timeProvider.nowLocalDateTime()
            val to = nowLdt.plusDays(horizonDays)
            val exceptions = occurrenceExceptionRepository.getExceptionsInRange(nowLdt, to)

            val upcoming = mutableListOf<EventOccurrenceWithEvent>()
            for (event in events) {
                val occurrences = recurrenceEngine.projectOccurrences(
                    event = event,
                    from = nowLdt,
                    to = to,
                    exceptions = exceptions,
                    evaluateOverdue = false,
                    timeProvider = timeProvider
                )
                for (occ in occurrences) {
                    if (occ.status == OccurrenceStatus.PENDING || occ.status == OccurrenceStatus.RESCHEDULED) {
                        upcoming.add(EventOccurrenceWithEvent(occurrence = occ, event = event))
                    }
                }
            }
            upcoming.sortedBy { it.occurrence.effectiveStartDateTime }
        }
}
