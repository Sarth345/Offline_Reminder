package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Domain use case coordinating user rescheduling of an occurrence:
 * - Persists occurrence reschedule exception in [OccurrenceExceptionRepository]
 * - Reconciles scheduled notification alarms
 */
class RescheduleOccurrenceActionUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val rescheduleOccurrenceUseCase: RescheduleOccurrenceUseCase = RescheduleOccurrenceUseCase(),
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase? = null,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        occurrence: EventOccurrence,
        newStartDateTime: LocalDateTime,
        newEndDateTime: LocalDateTime? = null,
        newZoneId: ZoneId = occurrence.zoneId
    ): Result<Unit> = Result.runCatching {
        val event = eventRepository.getEvent(occurrence.eventId)
            ?: throw IllegalArgumentException("Event not found with id: ${occurrence.eventId}")

        val result = rescheduleOccurrenceUseCase(
            event = event,
            originalStartDateTime = occurrence.originalStartDateTime,
            originalZoneId = occurrence.zoneId,
            newStartDateTime = newStartDateTime,
            newEndDateTime = newEndDateTime,
            newZoneId = newZoneId
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
                            status = OccurrenceStatus.RESCHEDULED,
                            rescheduledStartDateTime = newStartDateTime,
                            rescheduledEndDateTime = newEndDateTime,
                            rescheduledZoneId = newZoneId
                        )
                        occurrenceExceptionRepository.saveException(exception)
                    }
                }
            }
        }

        if (event.lifecycleStatus == EventLifecycleStatus.ACTIVE && scheduleNotificationsForEventUseCase != null) {
            val nowLdt = timeProvider.nowLocalDateTime()
            val exceptions = occurrenceExceptionRepository.getExceptionsInRange(
                nowLdt.minusDays(1),
                nowLdt.plusDays(30)
            ).filter { it.eventId == event.id }
            scheduleNotificationsForEventUseCase(event, exceptions)
        }
    }
}
