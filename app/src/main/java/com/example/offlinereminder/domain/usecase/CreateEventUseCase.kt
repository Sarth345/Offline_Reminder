package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository

/**
 * Domain use case to validate, persist, and schedule notifications for a new [Event].
 */
class CreateEventUseCase(
    private val eventRepository: EventRepository,
    private val validateEventUseCase: ValidateEventUseCase = ValidateEventUseCase(),
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase? = null,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(event: Event): Result<Unit> {
        val validationResult = validateEventUseCase(event)
        if (validationResult is Result.Failure) {
            return validationResult
        }

        val now = timeProvider.nowInstant()
        val eventToSave = event.copy(updatedAt = now)

        val saveResult = eventRepository.saveEvent(eventToSave)
        if (saveResult is Result.Failure) {
            return saveResult
        }

        if (eventToSave.lifecycleStatus == EventLifecycleStatus.ACTIVE && scheduleNotificationsForEventUseCase != null) {
            scheduleNotificationsForEventUseCase(eventToSave)
        }

        return Result.Success(Unit)
    }
}
