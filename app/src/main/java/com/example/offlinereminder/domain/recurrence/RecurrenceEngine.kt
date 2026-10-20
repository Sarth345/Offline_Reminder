package com.example.offlinereminder.domain.recurrence

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.OccurrenceException
import java.time.LocalDateTime

/**
 * Pure Kotlin recurrence calculation and occurrence projection engine.
 * Calculates finite, bounded occurrences on demand without materializing
 * infinite occurrences into persistent storage.
 */
interface RecurrenceEngine {

    /**
     * Projects occurrences for a given [event] within the bounded window [[from], [to]].
     *
     * Applies recurrence rules, excluded dates, and occurrence exceptions deterministically.
     * Returned occurrences are chronologically sorted and bounded strictly to the requested range.
     *
     * @param event The event definition (one-time or recurring).
     * @param from The inclusive start bound of the projection window.
     * @param to The inclusive end bound of the projection window.
     * @param exceptions List of occurrence exceptions (deviations, completions, skips, reschedules) for this event.
     * @param evaluateOverdue Whether to evaluate past uncompleted occurrences as OVERDUE.
     * @param timeProvider Optional provider for current time, used when [evaluateOverdue] is true.
     * @return Chronologically sorted list of projected [EventOccurrence]s within the window.
     */
    fun projectOccurrences(
        event: Event,
        from: LocalDateTime,
        to: LocalDateTime,
        exceptions: List<OccurrenceException> = emptyList(),
        evaluateOverdue: Boolean = false,
        timeProvider: TimeProvider? = null
    ): List<EventOccurrence>
}
