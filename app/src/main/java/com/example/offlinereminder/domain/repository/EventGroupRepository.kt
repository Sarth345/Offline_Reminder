package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventGroup
import kotlinx.coroutines.flow.Flow

/**
 * Pure Kotlin repository interface for EventGroup persistence and membership.
 */
interface EventGroupRepository {
    fun getAllGroups(): Flow<List<EventGroup>>
    fun getGroupById(id: String): Flow<EventGroup?>
    suspend fun getGroup(id: String): EventGroup?
    fun searchGroups(query: String): Flow<List<EventGroup>>
    fun getGroupsForEvent(eventId: String): Flow<List<EventGroup>>
    fun getActiveEventsForGroup(groupId: String): Flow<List<Event>>
    suspend fun saveGroup(group: EventGroup): Result<Unit>
    suspend fun deleteGroup(id: String): Result<Unit>
    suspend fun addEventToGroup(eventId: String, groupId: String): Result<Unit>
    suspend fun removeEventFromGroup(eventId: String, groupId: String): Result<Unit>
}
