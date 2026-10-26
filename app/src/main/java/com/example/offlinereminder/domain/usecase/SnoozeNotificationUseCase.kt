package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.NotificationInstance
import com.example.offlinereminder.domain.model.SnoozeCalculator
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.repository.NotificationRepository
import java.time.Duration
import java.time.Instant

/**
 * Use case to snooze an active notification.
 * Updates the database state (snooze count, snooze_until, new trigger time)
 * and registers a new wake-up alarm with [AlarmScheduler].
 */
class SnoozeNotificationUseCase(
    private val notificationRepository: NotificationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        instanceId: String,
        snoozeDuration: Duration = SnoozeCalculator.DEFAULT_SNOOZE_DURATION,
        deliveryType: com.example.offlinereminder.domain.model.ReminderDeliveryType? = null
    ): Result<Instant> = Result.runCatching {
        val instance = notificationRepository.getInstanceById(instanceId)
            ?: throw IllegalArgumentException("Notification instance not found for ID: $instanceId")

        val now = timeProvider.nowInstant()
        val snoozeUntil = now.plus(snoozeDuration)
        val newTriggerMillis = snoozeUntil.toEpochMilli()

        // Cancel previous alarm
        alarmScheduler.cancelAlarm(instance.requestCode, NotificationConstants.ACTION_ALARM)

        // Persist snooze state in database
        notificationRepository.snooze(instance.id, snoozeUntil, newTriggerMillis)

        val effectiveDeliveryType = deliveryType ?: if (instance.status == com.example.offlinereminder.domain.model.NotificationInstanceStatus.RINGING) {
            com.example.offlinereminder.domain.model.ReminderDeliveryType.ALARM
        } else {
            null
        }

        val extras = mutableMapOf(
            NotificationConstants.EXTRA_INSTANCE_ID to instance.id,
            NotificationConstants.EXTRA_EVENT_ID to instance.eventId,
            NotificationConstants.EXTRA_OCCURRENCE_START to instance.occurrenceOriginalStartDateTime.toString(),
            NotificationConstants.EXTRA_OCCURRENCE_ZONE to instance.occurrenceOriginalZoneId.id
        )
        if (effectiveDeliveryType != null) {
            extras[NotificationConstants.EXTRA_DELIVERY_TYPE] = effectiveDeliveryType.name
        }

        // Schedule new wake-up alarm
        alarmScheduler.scheduleAlarm(
            requestCode = instance.requestCode,
            triggerEpochMillis = newTriggerMillis,
            intentAction = NotificationConstants.ACTION_ALARM,
            extras = extras
        )

        snoozeUntil
    }
}
