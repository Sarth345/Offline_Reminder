package com.example.offlinereminder.domain.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Represents a deviation or override from the canonical recurring event definition.
 * Used to track single-occurrence completion, skip, reschedule, or notes override.
 *
 * Preserves the original occurrence identity ([eventId], [originalStartDateTime], [originalZoneId])
 * while explicitly separating replacement scheduling ([rescheduledStartDateTime], [rescheduledZoneId])
 * from actual execution ([actualExecutionDateTime], [completedAt]).
 */
data class OccurrenceException(
    val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val originalStartDateTime: LocalDateTime,
    val originalZoneId: ZoneId = ZoneId.systemDefault(),
    val status: OccurrenceStatus,
    val rescheduledStartDateTime: LocalDateTime? = null,
    val rescheduledEndDateTime: LocalDateTime? = null,
    val rescheduledZoneId: ZoneId? = null,
    val actualExecutionDateTime: LocalDateTime? = null,
    val completedAt: Instant? = null,
    val titleOverride: String? = null,
    val notesOverride: String? = null
) {
    /**
     * Unique, deterministic identity corresponding to the original scheduled instance.
     * Preserved across rescheduling and edits so exception lookup remains stable.
     */
    val occurrenceId: String
        get() = "${eventId}_${originalStartDateTime}_${originalZoneId.id}"

    init {
        if (status == OccurrenceStatus.RESCHEDULED) {
            requireNotNull(rescheduledStartDateTime) {
                "rescheduledStartDateTime must not be null when status is RESCHEDULED"
            }
        }
    }
}
