package com.example.offlinereminder.domain.notification

import com.example.offlinereminder.core.time.DateTimeUtils
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.QuietHours
import com.example.offlinereminder.domain.model.QuietHoursPolicy
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure Kotlin calculation engine for resolving notification trigger times
 * based on occurrence timing, schedule offsets, quiet hours policies, and timezones.
 */
object NotificationScheduleCalculator {

    data class CalculatedNotification(
        val instanceId: String,
        val eventId: String,
        val occurrenceOriginalStartDateTime: LocalDateTime,
        val occurrenceOriginalZoneId: ZoneId,
        val scheduleId: String,
        val nominalDateTime: LocalDateTime,
        val deliveryDateTime: LocalDateTime,
        val triggerEpochMillis: Long,
        val isSilent: Boolean,
        val requestCode: Int,
        val deliveryType: com.example.offlinereminder.domain.model.ReminderDeliveryType = com.example.offlinereminder.domain.model.ReminderDeliveryType.NOTIFICATION
    ) {
        fun toInstance(): NotificationInstance = NotificationInstance(
            id = instanceId,
            eventId = eventId,
            occurrenceOriginalStartDateTime = occurrenceOriginalStartDateTime,
            occurrenceOriginalZoneId = occurrenceOriginalZoneId,
            scheduleId = scheduleId,
            triggerEpochMillis = triggerEpochMillis,
            requestCode = requestCode
        )
    }

    /**
     * Resolves the trigger time and delivery parameters for a single occurrence and schedule.
     */
    fun calculate(
        occurrence: EventOccurrence,
        schedule: NotificationSchedule,
        quietHours: QuietHours? = null,
        priority: Priority = Priority.NORMAL
    ): CalculatedNotification {
        val nominalDateTime = calculateNominalTime(occurrence, schedule)
        val zoneId = occurrence.effectiveZoneId

        var deliveryDateTime = nominalDateTime
        var isSilent = false

        if (quietHours != null && quietHours.enabled && quietHours.isInQuietHours(nominalDateTime.toLocalTime())) {
            val isExempt = quietHours.allowCritical && (priority == Priority.HIGH || priority == Priority.CRITICAL)
            if (!isExempt) {
                when (quietHours.policy) {
                    QuietHoursPolicy.DELAY -> {
                        deliveryDateTime = quietHours.nextValidDeliveryTime(nominalDateTime)
                    }
                    QuietHoursPolicy.SILENT -> {
                        isSilent = true
                    }
                    QuietHoursPolicy.ALLOW_CRITICAL -> {
                        deliveryDateTime = quietHours.nextValidDeliveryTime(nominalDateTime)
                    }
                }
            }
        }

        val triggerEpochMillis = DateTimeUtils.toEpochMillis(deliveryDateTime, zoneId)
        val instanceId = NotificationInstance.createInstanceId(occurrence.occurrenceId, schedule.id)
        val requestCode = NotificationInstance.createRequestCode(instanceId)

        return CalculatedNotification(
            instanceId = instanceId,
            eventId = occurrence.eventId,
            occurrenceOriginalStartDateTime = occurrence.originalStartDateTime,
            occurrenceOriginalZoneId = occurrence.zoneId,
            scheduleId = schedule.id,
            nominalDateTime = nominalDateTime,
            deliveryDateTime = deliveryDateTime,
            triggerEpochMillis = triggerEpochMillis,
            isSilent = isSilent,
            requestCode = requestCode,
            deliveryType = schedule.deliveryType
        )
    }

    private fun calculateNominalTime(
        occurrence: EventOccurrence,
        schedule: NotificationSchedule
    ): LocalDateTime {
        return when (schedule.triggerType) {
            NotificationTriggerType.BEFORE_START ->
                occurrence.effectiveStartDateTime.minus(schedule.offsetDuration)

            NotificationTriggerType.AT_START ->
                occurrence.effectiveStartDateTime

            NotificationTriggerType.BEFORE_END -> {
                val end = occurrence.effectiveEndDateTime ?: occurrence.effectiveStartDateTime
                end.minus(schedule.offsetDuration)
            }

            NotificationTriggerType.AT_END ->
                occurrence.effectiveEndDateTime ?: occurrence.effectiveStartDateTime
        }
    }
}
