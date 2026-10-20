package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Domain use case to observe filtered collections of [Event] aggregates.
 */
class GetEventsUseCase(
    private val eventRepository: EventRepository
) {
    fun getAllActive(): Flow<List<Event>> = eventRepository.getAllActiveEvents()

    fun getAll(): Flow<List<Event>> = eventRepository.getAllEvents()

    fun getByStatus(status: EventLifecycleStatus): Flow<List<Event>> =
        eventRepository.getEventsByLifecycleStatus(status)

    fun getFavorites(): Flow<List<Event>> = eventRepository.getFavoriteEvents()

    fun getByCategory(categoryId: String): Flow<List<Event>> =
        eventRepository.getEventsByCategory(categoryId)

    fun getByEventType(eventTypeId: String): Flow<List<Event>> =
        eventRepository.getEventsByEventType(eventTypeId)

    fun getSubEvents(parentEventId: String): Flow<List<Event>> =
        eventRepository.getSubEvents(parentEventId)

    fun getInDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Event>> =
        eventRepository.getEventsInDateRange(startDate, endDate)
}
