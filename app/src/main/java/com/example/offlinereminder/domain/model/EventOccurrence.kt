package com.example.offlinereminder.domain.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Conceptual representation of a single temporal occurrence.
 * For a one-time event, this represents its sole execution instance.
 * For a recurring event, this is an in-memory projected or exception-backed instance.
 *
 * Preserves the canonical original occurrence identity while distinguishing between
 * the new scheduled time ([rescheduledStartDateTime]) and actual execution time ([actualExecutionDateTime]).
 */
data class EventOccurrence(
    val eventId: String,
    val originalStartDateTime: LocalDateTime,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val occurrenceId: String = createOccurrenceId(eventId, originalStartDateTime, zoneId),
    val rescheduledStartDateTime: LocalDateTime? = null,
    val rescheduledEndDateTime: LocalDateTime? = null,
    val rescheduledZoneId: ZoneId? = null,
    val actualExecutionDateTime: LocalDateTime? = null,
    val defaultEndDateTime: LocalDateTime? = null,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val completedAt: Instant? = null,
    val isException: Boolean = false,
    val titleOverride: String? = null,
    val notesOverride: String? = null
) {
    /**
     * The effective scheduled start date-time for calendar rendering and notification scheduling.
     * Evaluates to [rescheduledStartDateTime] if rescheduled, otherwise [originalStartDateTime].
     */
    val effectiveStartDateTime: LocalDateTime
        get() = rescheduledStartDateTime ?: originalStartDateTime

    /**
     * The effective scheduled end date-time.
     */
    val effectiveEndDateTime: LocalDateTime?
        get() = rescheduledEndDateTime ?: defaultEndDateTime

    /**
     * The effective timezone for the occurrence.
     */
    val effectiveZoneId: ZoneId
        get() = rescheduledZoneId ?: zoneId

    val isCompleted: Boolean get() = status == OccurrenceStatus.COMPLETED
    val isSkipped: Boolean get() = status == OccurrenceStatus.SKIPPED
    val isRescheduled: Boolean get() = status == OccurrenceStatus.RESCHEDULED
    val isOverdue: Boolean get() = status == OccurrenceStatus.OVERDUE
    val isMultiDay: Boolean
        get() = effectiveEndDateTime != null &&
            effectiveEndDateTime!!.toLocalDate().isAfter(effectiveStartDateTime.toLocalDate())

    companion object {
        fun createOccurrenceId(
            eventId: String,
            originalStartDateTime: LocalDateTime,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): String = "${eventId}_${originalStartDateTime}_${zoneId.id}"
    }
}
