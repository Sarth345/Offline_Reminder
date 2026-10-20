package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.NotificationInstanceStatus
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.notification.NotificationConstants
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.NotificationRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import java.time.LocalDateTime

/**
 * Domain use case coordinating user completion of an occurrence:
 * - Persists occurrence completion exception in [OccurrenceExceptionRepository]
 * - Reconciles pending notifications for the completed occurrence
 */
class CompleteOccurrenceActionUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val notificationRepository: NotificationRepository? = null,
    private val alarmScheduler: AlarmScheduler? = null,
    private val completeOccurrenceUseCase: CompleteOccurrenceUseCase,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        occurrence: EventOccurrence,
        actualExecutionDateTime: LocalDateTime? = null
    ): Result<Unit> = Result.runCatching {
        val event = eventRepository.getEvent(occurrence.eventId)
            ?: throw IllegalArgumentException("Event not found with id: ${occurrence.eventId}")

        val result = completeOccurrenceUseCase(
            event = event,
            originalStartDateTime = occurrence.originalStartDateTime,
            zoneId = occurrence.zoneId,
            actualExecutionDateTime = actualExecutionDateTime
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
                            status = OccurrenceStatus.COMPLETED,
                            completedAt = timeProvider.nowInstant(),
                            actualExecutionDateTime = actualExecutionDateTime
                        )
                        occurrenceExceptionRepository.saveException(exception)
                    }
                }
            }
        }

        // Cancel pending notifications for this specific occurrence
        if (notificationRepository != null && alarmScheduler != null) {
            val pendingInstances = notificationRepository.getInstancesForEvent(occurrence.eventId)
            for (instance in pendingInstances) {
                if (instance.occurrenceOriginalStartDateTime == occurrence.originalStartDateTime &&
                    instance.occurrenceOriginalZoneId == occurrence.zoneId &&
                    instance.isPending
                ) {
                    alarmScheduler.cancelAlarm(instance.requestCode, NotificationConstants.ACTION_ALARM)
                    notificationRepository.updateStatus(instance.id, NotificationInstanceStatus.DELIVERED)
                }
            }
        }
    }
}
