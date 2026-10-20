package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.repository.NotificationRepository

/**
 * Recovers and re-registers alarms after a device reboot or app upgrade.
 * Reconciles stale instances and schedules all valid future instances.
 */
class RebootRecoveryUseCase(
    private val notificationRepository: NotificationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(): Result<Int> = Result.runCatching {
        val activeInstances = notificationRepository.getAllActiveScheduled()
        val nowMillis = timeProvider.nowInstant().toEpochMilli()
        var rescheduledCount = 0

        for (instance in activeInstances) {
            if (instance.triggerEpochMillis <= nowMillis) {
                // Stale instance missed/expired while device was off (not falsely marked DELIVERED)
                notificationRepository.updateStatus(instance.id, NotificationInstanceStatus.CANCELLED)
            } else {
                // Future valid instance -> re-register with AlarmScheduler
                val success = alarmScheduler.scheduleAlarm(
                    requestCode = instance.requestCode,
                    triggerEpochMillis = instance.triggerEpochMillis,
                    intentAction = NotificationConstants.ACTION_ALARM,
                    extras = mapOf(
                        NotificationConstants.EXTRA_INSTANCE_ID to instance.id,
                        NotificationConstants.EXTRA_EVENT_ID to instance.eventId,
                        NotificationConstants.EXTRA_OCCURRENCE_START to instance.occurrenceOriginalStartDateTime.toString(),
                        NotificationConstants.EXTRA_OCCURRENCE_ZONE to instance.occurrenceOriginalZoneId.id
                    )
                )
                if (success) {
                    rescheduledCount++
                }
            }
        }

        rescheduledCount
    }
}
