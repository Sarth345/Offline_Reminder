package com.example.offlinereminder.domain.model

import java.time.LocalDateTime

/**
 * Authoritative display status representing the execution/lifecycle state of an event
 * or occurrence uniformly across all Planerize screens:
 * - Calendar
 * - Today's Dashboard
 * - All Reminders
 * - Upcoming
 * - Event Detail
 * - Search
 */
enum class EventDisplayStatus {
    OVERDUE,
    COMPLETED,
    ACTIVE,
    SKIPPED,
    RESCHEDULED,
    ARCHIVED,
    TRASHED
}

/**
 * Authoritative state resolution function for an Event and optional Occurrence.
 */
fun resolveEventDisplayStatus(
    event: Event,
    occurrence: EventOccurrence? = null,
    nowLdt: LocalDateTime = LocalDateTime.now()
): EventDisplayStatus {
    if (event.lifecycleStatus == EventLifecycleStatus.TRASHED) return EventDisplayStatus.TRASHED
    if (event.lifecycleStatus == EventLifecycleStatus.ARCHIVED) return EventDisplayStatus.ARCHIVED

    if (occurrence != null) {
        return when (occurrence.status) {
            OccurrenceStatus.COMPLETED -> EventDisplayStatus.COMPLETED
            OccurrenceStatus.SKIPPED -> EventDisplayStatus.SKIPPED
            OccurrenceStatus.RESCHEDULED -> EventDisplayStatus.RESCHEDULED
            OccurrenceStatus.OVERDUE -> EventDisplayStatus.OVERDUE
            OccurrenceStatus.PENDING -> {
                if (occurrence.effectiveStartDateTime.isBefore(nowLdt)) {
                    EventDisplayStatus.OVERDUE
                } else {
                    EventDisplayStatus.ACTIVE
                }
            }
            OccurrenceStatus.EXCLUDED -> EventDisplayStatus.SKIPPED
        }
    }

    val startLdt = event.temporal.startLocalDateTime()
    return if (startLdt.isBefore(nowLdt)) {
        EventDisplayStatus.OVERDUE
    } else {
        EventDisplayStatus.ACTIVE
    }
}

/**
 * Authoritative state resolution function for an Event evaluated against its exceptions.
 */
fun resolveEventDisplayStatus(
    event: Event,
    exceptions: List<OccurrenceException>,
    nowLdt: LocalDateTime = LocalDateTime.now()
): EventDisplayStatus {
    if (event.lifecycleStatus == EventLifecycleStatus.TRASHED) return EventDisplayStatus.TRASHED
    if (event.lifecycleStatus == EventLifecycleStatus.ARCHIVED) return EventDisplayStatus.ARCHIVED

    val eventExceptions = exceptions.filter { it.eventId == event.id }
    if (event.recurrenceRule == null) {
        val initialOcc = event.createInitialOccurrence()
        val exc = eventExceptions.find { it.occurrenceId == initialOcc.occurrenceId }
        if (exc != null) {
            return when (exc.status) {
                OccurrenceStatus.COMPLETED -> EventDisplayStatus.COMPLETED
                OccurrenceStatus.SKIPPED -> EventDisplayStatus.SKIPPED
                OccurrenceStatus.RESCHEDULED -> EventDisplayStatus.RESCHEDULED
                OccurrenceStatus.OVERDUE -> EventDisplayStatus.OVERDUE
                OccurrenceStatus.EXCLUDED -> EventDisplayStatus.SKIPPED
                OccurrenceStatus.PENDING -> {
                    val effStart = exc.rescheduledStartDateTime ?: initialOcc.effectiveStartDateTime
                    if (effStart.isBefore(nowLdt)) EventDisplayStatus.OVERDUE else EventDisplayStatus.ACTIVE
                }
            }
        }
        return if (initialOcc.effectiveStartDateTime.isBefore(nowLdt)) {
            EventDisplayStatus.OVERDUE
        } else {
            EventDisplayStatus.ACTIVE
        }
    } else {
        // Recurring event: check if any exception is overdue, or check nominal
        val hasOverdue = eventExceptions.any {
            it.status == OccurrenceStatus.OVERDUE ||
                (it.status == OccurrenceStatus.PENDING && (it.rescheduledStartDateTime ?: it.originalStartDateTime).isBefore(nowLdt))
        }
        if (hasOverdue) return EventDisplayStatus.OVERDUE

        // If today's occurrence or latest exception was completed and no overdue:
        val completedToday = eventExceptions.any {
            it.status == OccurrenceStatus.COMPLETED &&
                it.originalStartDateTime.toLocalDate() == nowLdt.toLocalDate()
        }
        if (completedToday) return EventDisplayStatus.COMPLETED

        return EventDisplayStatus.ACTIVE
    }
}
