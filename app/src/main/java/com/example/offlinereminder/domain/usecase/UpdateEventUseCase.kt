package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository

/**
 * Domain use case to validate, persist updates to an existing [Event],
 * and reconcile notification schedules.
 */
class UpdateEventUseCase(
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val validateEventUseCase: ValidateEventUseCase = ValidateEventUseCase(),
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase? = null,
    private val cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase? = null,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(event: Event): Result<Unit> {
        val validationResult = validateEventUseCase(event)
        if (validationResult is Result.Failure) {
            return validationResult
        }

        val updatedEvent = event.copy(updatedAt = timeProvider.nowInstant())
        val saveResult = eventRepository.saveEvent(updatedEvent)
        if (saveResult is Result.Failure) {
            return saveResult
        }

        if (updatedEvent.lifecycleStatus == EventLifecycleStatus.ACTIVE) {
            if (scheduleNotificationsForEventUseCase != null) {
                val nowLdt = timeProvider.nowLocalDateTime()
                val exceptions = occurrenceExceptionRepository.getExceptionsInRange(
                    nowLdt.minusDays(1),
                    nowLdt.plusDays(30)
                ).filter { it.eventId == updatedEvent.id }
                scheduleNotificationsForEventUseCase(updatedEvent, exceptions)
            }
        } else {
            cancelNotificationsForEventUseCase?.invoke(updatedEvent.id)
        }

        return Result.Success(Unit)
    }
}
