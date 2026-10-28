package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Domain use case to project upcoming active occurrences for the Agenda/Timeline.
 * Supports:
 * - Next: Exactly one next occurrence per event/series strictly after today (no overdue, no today).
 * - Overdue: Overdue incomplete occurrences, maximum 4 most recent per event/series.
 * - Date: All occurrences on the selected date (multiple occurrences per event allowed).
 */
class GetUpcomingOccurrencesUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider
) {

    /**
     * Next: Returns exactly ONE next future occurrence per active event/recurring series strictly after [afterDate].
     * Excludes today and overdue occurrences.
     */
    fun nextOccurrencePerEvent(
        afterDate: LocalDate = timeProvider.nowLocalDate(),
        horizonDays: Long = 365
    ): Flow<List<EventOccurrenceWithEvent>> = combine(
        eventRepository.getAllActiveEvents(),
        occurrenceExceptionRepository.getAllExceptionsFlow()
    ) { events, allExceptions ->
        val from = afterDate.plusDays(1).atStartOfDay()
        val to = from.plusDays(horizonDays)

        val exceptions = allExceptions.filter { exc ->
            val start = exc.originalStartDateTime
            val resched = exc.rescheduledStartDateTime
            (!start.isBefore(from) && !start.isAfter(to)) ||
                (resched != null && !resched.isBefore(from) && !resched.isAfter(to))
        }

        val result = mutableListOf<EventOccurrenceWithEvent>()
        for (event in events) {
            val occurrences = recurrenceEngine.projectOccurrences(
                event = event,
                from = from,
                to = to,
                exceptions = exceptions,
                evaluateOverdue = false,
                timeProvider = timeProvider
            )

            // Pick the earliest qualifying future occurrence strictly after afterDate
            val nextOcc = occurrences
                .filter { occ ->
                    occ.effectiveStartDateTime.toLocalDate().isAfter(afterDate) &&
                        (occ.status == OccurrenceStatus.PENDING || occ.status == OccurrenceStatus.RESCHEDULED)
                }
                .minByOrNull { it.effectiveStartDateTime }

            if (nextOcc != null) {
                result.add(EventOccurrenceWithEvent(occurrence = nextOcc, event = event))
            }
        }

        result.sortedBy { it.occurrence.effectiveStartDateTime }
    }

    /**
     * Overdue: Returns overdue, incomplete occurrences as of [asOf], limited to at most [maxPerEvent]
     * most recent overdue occurrences per event/series. Excludes today's normal/current occurrences.
     */
    fun overdueOccurrences(
        asOf: LocalDateTime = timeProvider.nowLocalDateTime(),
        maxPerEvent: Int = 4,
        lookbackDays: Long = 90
    ): Flow<List<EventOccurrenceWithEvent>> = combine(
        eventRepository.getAllActiveEvents(),
        occurrenceExceptionRepository.getAllExceptionsFlow()
    ) { events, allExceptions ->
        val from = asOf.minusDays(lookbackDays)
        val to = asOf

        val exceptions = allExceptions.filter { exc ->
            val start = exc.originalStartDateTime
            val resched = exc.rescheduledStartDateTime
            (!start.isBefore(from) && !start.isAfter(to)) ||
                (resched != null && !resched.isBefore(from) && !resched.isAfter(to))
        }

        val result = mutableListOf<EventOccurrenceWithEvent>()
        for (event in events) {
            val occurrences = recurrenceEngine.projectOccurrences(
                event = event,
                from = from,
                to = to,
                exceptions = exceptions,
                evaluateOverdue = true,
                timeProvider = timeProvider
            )

            val overdueForEvent = occurrences
                .filter { occ ->
                    occ.status != OccurrenceStatus.COMPLETED &&
                        occ.status != OccurrenceStatus.SKIPPED &&
                        occ.status != OccurrenceStatus.EXCLUDED &&
                        occ.effectiveStartDateTime.toLocalDate().isBefore(asOf.toLocalDate()) &&
                        (occ.status == OccurrenceStatus.OVERDUE || occ.status == OccurrenceStatus.PENDING || occ.status == OccurrenceStatus.RESCHEDULED)
                }
                .sortedByDescending { it.effectiveStartDateTime }
                .take(maxPerEvent)

            for (occ in overdueForEvent) {
                result.add(EventOccurrenceWithEvent(occurrence = occ, event = event))
            }
        }

        result.sortedBy { it.occurrence.effectiveStartDateTime }
    }

    /**
     * Date: Returns ALL occurrences occurring on [selectedDate]. Multiple occurrences from the same
     * event are retained.
     */
    fun occurrencesForDate(
        selectedDate: LocalDate
    ): Flow<List<EventOccurrenceWithEvent>> = combine(
        eventRepository.getAllActiveEvents(),
        occurrenceExceptionRepository.getAllExceptionsFlow()
    ) { events, allExceptions ->
        val from = selectedDate.atStartOfDay()
        val to = selectedDate.atTime(LocalTime.MAX)

        val exceptions = allExceptions.filter { exc ->
            val start = exc.originalStartDateTime
            val resched = exc.rescheduledStartDateTime
            (!start.isBefore(from.minusDays(30)) && !start.isAfter(to)) ||
                (resched != null && !resched.isBefore(from.minusDays(30)) && !resched.isAfter(to))
        }

        val result = mutableListOf<EventOccurrenceWithEvent>()
        for (event in events) {
            val occurrences = recurrenceEngine.projectOccurrences(
                event = event,
                from = from.minusDays(30),
                to = to,
                exceptions = exceptions,
                evaluateOverdue = true,
                timeProvider = timeProvider
            )

            for (occ in occurrences) {
                val startDay = occ.effectiveStartDateTime.toLocalDate()
                val endDay = occ.effectiveEndDateTime?.toLocalDate() ?: startDay
                val effectiveEndDay = if (endDay.isBefore(startDay)) startDay else endDay

                if (!selectedDate.isBefore(startDay) && !selectedDate.isAfter(effectiveEndDay) &&
                    occ.status != OccurrenceStatus.EXCLUDED
                ) {
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

    /**
     * Backwards-compatible projection query.
     */
    operator fun invoke(
        horizonDays: Long = 30,
        fromDateTime: LocalDateTime? = null,
        includeCompleted: Boolean = false,
        includeSkipped: Boolean = false,
        includeOverdue: Boolean = false
    ): Flow<List<EventOccurrenceWithEvent>> = combine(
        eventRepository.getAllActiveEvents(),
        occurrenceExceptionRepository.getAllExceptionsFlow()
    ) { events, allExceptions ->
        val from = fromDateTime ?: timeProvider.nowLocalDateTime()
        val to = from.plusDays(horizonDays)
        val exceptions = allExceptions.filter { exc ->
            val start = exc.originalStartDateTime
            val resched = exc.rescheduledStartDateTime
            (!start.isBefore(from) && !start.isAfter(to)) ||
                (resched != null && !resched.isBefore(from) && !resched.isAfter(to))
        }

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
