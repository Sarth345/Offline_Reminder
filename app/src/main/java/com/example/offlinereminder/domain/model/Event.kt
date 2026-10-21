package com.example.offlinereminder.domain.model

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Authoritative canonical entity definition of an event or reminder.
 * Represents either a one-time event or the master template/series definition
 * for a recurring event.
 */
data class Event(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val eventTypeId: String,
    val categoryId: String,
    val priority: Priority = Priority.NORMAL,
    val temporal: EventDateTime,
    val location: LocationData? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val notificationSchedules: List<NotificationSchedule> = emptyList(),
    val checklist: Checklist? = null,
    val lifecycleStatus: EventLifecycleStatus = EventLifecycleStatus.ACTIVE,
    val isFavorite: Boolean = false,
    val groupIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val parentEventId: String? = null,
    val templateId: String? = null,
    val customMetadata: Map<String, String> = emptyMap(),
    val attachments: List<AttachmentMetadata> = emptyList(),
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val isRecurring: Boolean get() = recurrenceRule != null

    val isOneTime: Boolean get() = recurrenceRule == null

    val isTrashed: Boolean get() = lifecycleStatus == EventLifecycleStatus.TRASHED

    val isArchived: Boolean get() = lifecycleStatus == EventLifecycleStatus.ARCHIVED

    val isActive: Boolean get() = lifecycleStatus == EventLifecycleStatus.ACTIVE

    val schedules: List<NotificationSchedule> get() = notificationSchedules

    /**
     * Creates the execution occurrence instance for a one-time event.
     * Unifies one-time and recurring event lifecycles.
     */
    fun createInitialOccurrence(
        status: OccurrenceStatus = OccurrenceStatus.PENDING,
        completedAt: Instant? = null
    ): EventOccurrence {
        val startLdt = temporal.startLocalDateTime()
        val zoneId = when (temporal) {
            is EventDateTime.DateTime -> temporal.zoneId
            is EventDateTime.DateTimeRange -> temporal.zoneId
            else -> ZoneId.systemDefault()
        }
        val endLdt = when (temporal) {
            is EventDateTime.DateRange -> temporal.endDate.atTime(23, 59, 59)
            is EventDateTime.DateTimeRange -> temporal.endDateTime
            else -> null
        }

        return EventOccurrence(
            eventId = id,
            originalStartDateTime = startLdt,
            zoneId = zoneId,
            defaultEndDateTime = endLdt,
            status = status,
            completedAt = completedAt,
            isException = false
        )
    }
}
