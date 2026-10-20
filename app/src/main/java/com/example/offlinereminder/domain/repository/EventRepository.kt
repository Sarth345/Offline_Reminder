package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Pure Kotlin repository interface for Event persistence and observation.
 */
interface EventRepository {
    fun getEventById(id: String): Flow<Event?>
    suspend fun getEvent(id: String): Event?
    fun getAllActiveEvents(): Flow<List<Event>>
    fun getAllEvents(): Flow<List<Event>>
    fun getEventsByLifecycleStatus(status: EventLifecycleStatus): Flow<List<Event>>
    fun getFavoriteEvents(): Flow<List<Event>>
    fun getEventsByCategory(categoryId: String): Flow<List<Event>>
    fun getEventsByEventType(eventTypeId: String): Flow<List<Event>>
    suspend fun countEventsByCategory(categoryId: String): Int
    suspend fun countEventsByEventType(eventTypeId: String): Int
    fun getSubEvents(parentEventId: String): Flow<List<Event>>
    fun getEventsInDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Event>>
    suspend fun saveEvent(event: Event): Result<Unit>
    suspend fun deleteEvent(id: String): Result<Unit>
    suspend fun moveToTrash(id: String): Result<Unit>
    suspend fun restoreFromTrash(id: String): Result<Unit>
    suspend fun setFavorite(id: String, isFavorite: Boolean): Result<Unit>
}
