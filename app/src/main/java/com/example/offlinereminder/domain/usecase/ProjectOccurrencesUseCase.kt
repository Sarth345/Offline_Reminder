package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import java.time.LocalDateTime

/**
 * Domain use case to project occurrences for an event within a bounded window.
 * Delegates to [RecurrenceEngine] for pure recurrence calculations.
 */
class ProjectOccurrencesUseCase(
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine()
) {
    operator fun invoke(
        event: Event,
        from: LocalDateTime,
        to: LocalDateTime,
        exceptions: List<OccurrenceException> = emptyList(),
        evaluateOverdue: Boolean = false,
        timeProvider: TimeProvider? = null
    ): List<EventOccurrence> = recurrenceEngine.projectOccurrences(
        event = event,
        from = from,
        to = to,
        exceptions = exceptions,
        evaluateOverdue = evaluateOverdue,
        timeProvider = timeProvider
    )
}
