package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.NotificationRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository

/**
 * Domain use case coordinating user skipping of an occurrence:
 * - Persists occurrence skip exception in [OccurrenceExceptionRepository]
 * - Cancels pending notifications for the skipped occurrence
 */
class SkipOccurrenceActionUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val notificationRepository: NotificationRepository? = null,
    private val alarmScheduler: AlarmScheduler? = null,
    private val skipOccurrenceUseCase: SkipOccurrenceUseCase = SkipOccurrenceUseCase()
) {
    suspend operator fun invoke(occurrence: EventOccurrence): Result<Unit> = Result.runCatching {
        val event = eventRepository.getEvent(occurrence.eventId)
            ?: throw IllegalArgumentException("Event not found with id: ${occurrence.eventId}")

        val result = skipOccurrenceUseCase(
            event = event,
            originalStartDateTime = occurrence.originalStartDateTime,
            zoneId = occurrence.zoneId
        )

        when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> {
                when (val mutation = result.data) {
                    is OccurrenceMutationResult.RecurringException -> {
                        occurrenceExceptionRepository.saveException(mutation.exception)
                    }
                    is OccurrenceMutationResult.OneTimeOccurrence -> {
                        val exception = OccurrenceException(
                            eventId = event.id,
                            originalStartDateTime = occurrence.originalStartDateTime,
                            originalZoneId = occurrence.zoneId,
                            status = OccurrenceStatus.SKIPPED
                        )
                        occurrenceExceptionRepository.saveException(exception)
                    }
                }
            }
        }

        // Cancel pending notifications for this occurrence
        if (notificationRepository != null && alarmScheduler != null) {
            val pendingInstances = notificationRepository.getInstancesForEvent(occurrence.eventId)
            for (instance in pendingInstances) {
                if (instance.occurrenceOriginalStartDateTime == occurrence.originalStartDateTime &&
                    instance.occurrenceOriginalZoneId == occurrence.zoneId &&
                    instance.isPending
                ) {
                    alarmScheduler.cancelAlarm(instance.requestCode, NotificationConstants.ACTION_ALARM)
                    notificationRepository.updateStatus(instance.id, NotificationInstanceStatus.CANCELLED)
                }
            }
        }
    }
}
