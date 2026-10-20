package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.repository.NotificationRepository

/**
 * Use case to dismiss an active notification instance.
 */
class DismissNotificationUseCase(
    private val notificationRepository: NotificationRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(instanceId: String): Result<Unit> = Result.runCatching {
        val instance = notificationRepository.getInstanceById(instanceId)
        if (instance != null) {
            alarmScheduler.cancelAlarm(instance.requestCode, NotificationConstants.ACTION_ALARM)
            notificationRepository.updateStatus(instance.id, NotificationInstanceStatus.DISMISSED)
        }
    }
}
