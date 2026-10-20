package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.EventGroupDao
import com.example.offlinereminder.data.local.entity.EventGroupCrossRef
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.repository.EventGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EventGroupRepositoryImpl(
    private val eventGroupDao: EventGroupDao
) : EventGroupRepository {

    override fun getAllGroups(): Flow<List<EventGroup>> =
        eventGroupDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    override fun getGroupById(id: String): Flow<EventGroup?> =
        eventGroupDao.getByIdFlow(id).map { it?.toDomain() }

    override suspend fun getGroup(id: String): EventGroup? =
        eventGroupDao.getById(id)?.toDomain()

    override fun searchGroups(query: String): Flow<List<EventGroup>> =
        eventGroupDao.searchByNameFlow(query).map { entities -> entities.map { it.toDomain() } }

    override fun getGroupsForEvent(eventId: String): Flow<List<EventGroup>> =
        eventGroupDao.getGroupsForEventFlow(eventId).map { entities -> entities.map { it.toDomain() } }

    override fun getActiveEventsForGroup(groupId: String): Flow<List<Event>> =
        eventGroupDao.getActiveEventsWithDetailsForGroupFlow(groupId)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun saveGroup(group: EventGroup): Result<Unit> =
        Result.runCatching {
            eventGroupDao.insert(group.toEntity())
        }

    override suspend fun deleteGroup(id: String): Result<Unit> =
        Result.runCatching {
            eventGroupDao.deleteById(id)
        }

    override suspend fun addEventToGroup(eventId: String, groupId: String): Result<Unit> =
        Result.runCatching {
            eventGroupDao.insertCrossRef(EventGroupCrossRef(eventId = eventId, groupId = groupId))
        }

    override suspend fun removeEventFromGroup(eventId: String, groupId: String): Result<Unit> =
        Result.runCatching {
            eventGroupDao.removeEventFromGroup(eventId = eventId, groupId = groupId)
        }
}
