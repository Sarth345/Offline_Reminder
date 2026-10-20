package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.EventTypeDao
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.repository.EventTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EventTypeRepositoryImpl(
    private val eventTypeDao: EventTypeDao
) : EventTypeRepository {

    override fun getAllEventTypes(): Flow<List<EventType>> =
        eventTypeDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    override fun getEventTypesByCategory(categoryId: String): Flow<List<EventType>> =
        eventTypeDao.getByCategoryFlow(categoryId).map { entities -> entities.map { it.toDomain() } }

    override fun searchEventTypes(query: String): Flow<List<EventType>> =
        eventTypeDao.searchByNameOrAliasFlow(query).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEventType(id: String): EventType? =
        eventTypeDao.getById(id)?.toDomain()

    override suspend fun getEventTypeByKey(key: String): EventType? =
        eventTypeDao.getByKey(key)?.toDomain()

    override suspend fun saveEventType(eventType: EventType): Result<Unit> =
        Result.runCatching {
            eventTypeDao.insert(eventType.toEntity())
        }

    override suspend fun deleteEventType(id: String): Result<Unit> =
        Result.runCatching {
            eventTypeDao.deleteById(id)
        }
}
