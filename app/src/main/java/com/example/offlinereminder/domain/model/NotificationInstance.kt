package com.example.offlinereminder.domain.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class NotificationInstanceStatus {
    SCHEDULED,
    DELIVERED,
    DISMISSED,
    SNOOZED,
    CANCELLED,
    RINGING,
    TIMED_OUT
}

/**
 * Conceptual domain representation of a concrete, scheduled notification instance
 * tied to a specific [EventOccurrence] and [NotificationSchedule].
 */
data class NotificationInstance(
    val id: String,
    val eventId: String,
    val occurrenceOriginalStartDateTime: LocalDateTime,
    val occurrenceOriginalZoneId: ZoneId,
    val scheduleId: String? = null,
    val triggerEpochMillis: Long,
    val status: NotificationInstanceStatus = NotificationInstanceStatus.SCHEDULED,
    val snoozeCount: Int = 0,
    val snoozeUntil: Instant? = null,
    val requestCode: Int = createRequestCode(id)
) {
    val occurrenceId: String
        get() = EventOccurrence.createOccurrenceId(eventId, occurrenceOriginalStartDateTime, occurrenceOriginalZoneId)

    val isPending: Boolean
        get() = status == NotificationInstanceStatus.SCHEDULED ||
                status == NotificationInstanceStatus.SNOOZED ||
                status == NotificationInstanceStatus.RINGING

    val isTerminal: Boolean
        get() = status in listOf(
            NotificationInstanceStatus.DELIVERED,
            NotificationInstanceStatus.DISMISSED,
            NotificationInstanceStatus.TIMED_OUT,
            NotificationInstanceStatus.CANCELLED
        )

    companion object {
        fun createInstanceId(
            occurrenceId: String,
            scheduleId: String?
        ): String = if (scheduleId != null) "${occurrenceId}_${scheduleId}" else occurrenceId

        fun createRequestCode(instanceId: String): Int = instanceId.hashCode()
    }
}
