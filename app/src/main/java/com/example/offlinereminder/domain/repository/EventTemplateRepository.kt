package com.example.offlinereminder.domain.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.EventTemplate
import kotlinx.coroutines.flow.Flow

interface EventTemplateRepository {
    fun getAllTemplates(): Flow<List<EventTemplate>>
    suspend fun getTemplateById(id: String): EventTemplate?
    suspend fun saveTemplate(template: EventTemplate): Result<Unit>
    suspend fun deleteTemplate(id: String): Result<Unit>
    suspend fun countTemplatesByCategory(categoryId: String): Int
    suspend fun countTemplatesByEventType(eventTypeId: String): Int
}
