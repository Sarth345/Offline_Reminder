package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow

/**
 * Domain use case to retrieve a single [Event] aggregate by identifier.
 */
class GetEventByIdUseCase(
    private val eventRepository: EventRepository
) {
    operator fun invoke(eventId: String): Flow<Event?> = eventRepository.getEventById(eventId)

    suspend fun getOnce(eventId: String): Event? = eventRepository.getEvent(eventId)
}
