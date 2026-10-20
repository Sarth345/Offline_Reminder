package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.repository.EventRepository

/**
 * Domain use case to permanently delete an [com.example.offlinereminder.domain.model.Event],
 * cancelling any scheduled notification alarms.
 */
class DeleteEventUseCase(
    private val eventRepository: EventRepository,
    private val cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase? = null
) {
    suspend operator fun invoke(eventId: String): Result<Unit> {
        cancelNotificationsForEventUseCase?.invoke(eventId)
        return eventRepository.deleteEvent(eventId)
    }
}
