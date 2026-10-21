package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.data.local.dao.EventTemplateDao
import com.example.offlinereminder.data.local.mapper.toDomain
import com.example.offlinereminder.data.local.mapper.toEntity
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.repository.EventTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EventTemplateRepositoryImpl(
    private val eventTemplateDao: EventTemplateDao
) : EventTemplateRepository {

    override fun getAllTemplates(): Flow<List<EventTemplate>> =
        eventTemplateDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getTemplateById(id: String): EventTemplate? =
        eventTemplateDao.getById(id)?.toDomain()

    override suspend fun saveTemplate(template: EventTemplate): Result<Unit> = Result.runCatching {
        eventTemplateDao.insert(template.toEntity())
    }

    override suspend fun deleteTemplate(id: String): Result<Unit> = Result.runCatching {
        eventTemplateDao.deleteById(id)
    }

    override suspend fun countTemplatesByCategory(categoryId: String): Int =
        eventTemplateDao.countTemplatesByCategory(categoryId)

    override suspend fun countTemplatesByEventType(eventTypeId: String): Int =
        eventTemplateDao.countTemplatesByEventType(eventTypeId)
}
