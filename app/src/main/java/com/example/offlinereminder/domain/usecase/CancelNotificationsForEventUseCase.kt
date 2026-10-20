package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.repository.NotificationRepository

/**
 * Use case to cancel all pending alarms and instances for an event.
 */
class CancelNotificationsForEventUseCase(
    private val notificationRepository: NotificationRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(eventId: String): Result<Int> = Result.runCatching {
        val instances = notificationRepository.getInstancesForEvent(eventId)
        var cancelledCount = 0

        for (instance in instances) {
            if (instance.isPending) {
                alarmScheduler.cancelAlarm(instance.requestCode, NotificationConstants.ACTION_ALARM)
                notificationRepository.updateStatus(instance.id, NotificationInstanceStatus.CANCELLED)
                cancelledCount++
            }
        }

        cancelledCount
    }
}
