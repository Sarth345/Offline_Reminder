package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.EventType
import kotlinx.coroutines.flow.Flow

/**
 * Pure Kotlin repository interface for EventType catalog persistence and search.
 */
interface EventTypeRepository {
    fun getAllEventTypes(): Flow<List<EventType>>
    fun getEventTypesByCategory(categoryId: String): Flow<List<EventType>>
    fun searchEventTypes(query: String): Flow<List<EventType>>
    suspend fun getEventType(id: String): EventType?
    suspend fun getEventTypeByKey(key: String): EventType?
    suspend fun saveEventType(eventType: EventType): Result<Unit>
    suspend fun deleteEventType(id: String): Result<Unit>
}
