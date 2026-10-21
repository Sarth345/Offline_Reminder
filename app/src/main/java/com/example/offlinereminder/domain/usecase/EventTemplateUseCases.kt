package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.EventTemplateRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.UUID

class CreateEventTemplateUseCase(
    private val templateRepository: EventTemplateRepository,
    private val categoryRepository: CategoryRepository,
    private val eventTypeRepository: EventTypeRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(template: EventTemplate): Result<EventTemplate> {
        if (template.name.isBlank()) {
            return Result.Failure(IllegalArgumentException("Template name cannot be blank"))
        }
        val category = categoryRepository.getCategory(template.categoryId)
            ?: return Result.Failure(IllegalArgumentException("Referenced category does not exist: ${template.categoryId}"))
        val eventType = eventTypeRepository.getEventType(template.eventTypeId)
            ?: return Result.Failure(IllegalArgumentException("Referenced event type does not exist: ${template.eventTypeId}"))

        val now = timeProvider.nowInstant()
        val templateToSave = template.copy(
            id = if (template.id.isBlank()) UUID.randomUUID().toString() else template.id,
            name = template.name.trim(),
            createdAt = now,
            updatedAt = now
        )

        return when (val saveResult = templateRepository.saveTemplate(templateToSave)) {
            is Result.Success -> Result.Success(templateToSave)
            is Result.Failure -> saveResult
        }
    }
}

class UpdateEventTemplateUseCase(
    private val templateRepository: EventTemplateRepository,
    private val categoryRepository: CategoryRepository,
    private val eventTypeRepository: EventTypeRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(template: EventTemplate): Result<EventTemplate> {
        if (template.name.isBlank()) {
            return Result.Failure(IllegalArgumentException("Template name cannot be blank"))
        }
        val existing = templateRepository.getTemplateById(template.id)
            ?: return Result.Failure(IllegalArgumentException("Template with id ${template.id} not found"))

        val category = categoryRepository.getCategory(template.categoryId)
            ?: return Result.Failure(IllegalArgumentException("Referenced category does not exist: ${template.categoryId}"))
        val eventType = eventTypeRepository.getEventType(template.eventTypeId)
            ?: return Result.Failure(IllegalArgumentException("Referenced event type does not exist: ${template.eventTypeId}"))

        val updated = template.copy(
            name = template.name.trim(),
            createdAt = existing.createdAt,
            updatedAt = timeProvider.nowInstant()
        )

        return when (val saveResult = templateRepository.saveTemplate(updated)) {
            is Result.Success -> Result.Success(updated)
            is Result.Failure -> saveResult
        }
    }
}

class GetEventTemplatesUseCase(
    private val templateRepository: EventTemplateRepository
) {
    operator fun invoke(): Flow<List<EventTemplate>> = templateRepository.getAllTemplates()
}

class GetEventTemplateUseCase(
    private val templateRepository: EventTemplateRepository
) {
    suspend operator fun invoke(id: String): EventTemplate? = templateRepository.getTemplateById(id)
}

class DeleteEventTemplateUseCase(
    private val templateRepository: EventTemplateRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) {
            return Result.Failure(IllegalArgumentException("Template id cannot be blank"))
        }
        return templateRepository.deleteTemplate(id)
    }
}

class CreateEventFromTemplateUseCase(
    private val templateRepository: EventTemplateRepository,
    private val eventRepository: EventRepository,
    private val categoryRepository: CategoryRepository,
    private val eventTypeRepository: EventTypeRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        templateId: String,
        startDateTime: LocalDateTime,
        titleOverride: String? = null,
        customMetadata: Map<String, String> = emptyMap(),
        groupIds: Set<String> = emptySet()
    ): Result<Event> {
        val template = templateRepository.getTemplateById(templateId)
            ?: return Result.Failure(IllegalArgumentException("Template with id $templateId not found"))

        val category = categoryRepository.getCategory(template.categoryId)
            ?: return Result.Failure(IllegalArgumentException("Template refers to missing category: ${template.categoryId}"))
        val eventType = eventTypeRepository.getEventType(template.eventTypeId)
            ?: return Result.Failure(IllegalArgumentException("Template refers to missing event type: ${template.eventTypeId}"))

        val title = (titleOverride?.takeIf { it.isNotBlank() } ?: template.name).trim()
        val now = timeProvider.nowInstant()
        val eventId = UUID.randomUUID().toString()

        val temporal: EventDateTime = if (template.isAllDay) {
            EventDateTime.DateOnly(startDate = startDateTime.toLocalDate())
        } else if (template.defaultDuration.isZero) {
            EventDateTime.DateTime(dateTime = startDateTime)
        } else {
            EventDateTime.DateTimeRange(
                startDateTime = startDateTime,
                endDateTime = startDateTime.plus(template.defaultDuration)
            )
        }

        val freshSchedules = template.notificationSchedules.map { schedule ->
            schedule.copy(id = UUID.randomUUID().toString())
        }

        val freshChecklist = template.checklist?.let { cl ->
            Checklist(
                id = UUID.randomUUID().toString(),
                title = cl.title,
                items = cl.items.map { item ->
                    item.copy(id = UUID.randomUUID().toString(), isCompleted = false)
                }
            )
        }

        val event = Event(
            id = eventId,
            title = title,
            description = template.description,
            eventTypeId = template.eventTypeId,
            categoryId = template.categoryId,
            priority = template.priority,
            temporal = temporal,
            location = template.location,
            recurrenceRule = template.recurrenceRule,
            notificationSchedules = freshSchedules,
            checklist = freshChecklist,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            isFavorite = false,
            groupIds = groupIds,
            tagIds = template.tagIds,
            parentEventId = null,
            templateId = template.id,
            customMetadata = customMetadata,
            attachments = emptyList(),
            deletedAt = null,
            createdAt = now,
            updatedAt = now
        )

        return when (val saveResult = eventRepository.saveEvent(event)) {
            is Result.Success -> Result.Success(event)
            is Result.Failure -> saveResult
        }
    }
}
