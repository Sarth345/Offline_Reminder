package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDependency
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventRelationship
import kotlinx.coroutines.flow.Flow
import java.time.Instant
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
    suspend fun archiveEvent(id: String): Result<Unit>
    suspend fun restoreFromArchive(id: String): Result<Unit>
    suspend fun emptyTrash(): Result<Int>
    suspend fun purgeExpiredTrash(cutoff: Instant): Result<Int>
    suspend fun setFavorite(id: String, isFavorite: Boolean): Result<Unit>

    // Dependencies
    suspend fun addDependency(dependency: EventDependency): Result<Unit>
    suspend fun removeDependency(sourceEventId: String, targetEventId: String): Result<Unit>
    fun getDependenciesForEvent(eventId: String): Flow<List<EventDependency>>
    fun getDependentsOfEvent(eventId: String): Flow<List<EventDependency>>

    // Relationships
    suspend fun addRelationship(relationship: EventRelationship): Result<Unit>
    suspend fun removeRelationship(sourceEventId: String, targetEventId: String): Result<Unit>
    fun getRelationshipsForEvent(eventId: String): Flow<List<EventRelationship>>
}
