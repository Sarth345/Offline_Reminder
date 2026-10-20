package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Use case to create a new EventType with validation and referential integrity.
 */
class CreateEventTypeUseCase(
    private val eventTypeRepository: EventTypeRepository,
    private val categoryRepository: CategoryRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        name: String,
        categoryId: String,
        key: String = name.lowercase().replace(" ", "_"),
        defaultIcon: String? = null,
        defaultColor: Int? = null,
        aliases: List<String> = emptyList()
    ): Result<EventType> = Result.runCatching {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            throw IllegalArgumentException("Event type name cannot be blank")
        }

        // Validate category exists
        val category = categoryRepository.getCategory(categoryId)
            ?: throw IllegalArgumentException("Category not found for ID: $categoryId")

        // Validate unique key/name
        val existing = eventTypeRepository.getAllEventTypes().first()
        if (existing.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            throw IllegalArgumentException("An event type with name '$trimmedName' already exists")
        }

        val eventType = EventType(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            key = key.ifBlank { trimmedName.lowercase().replace(" ", "_") },
            categoryId = category.id,
            defaultColor = defaultColor,
            defaultIcon = defaultIcon,
            aliases = aliases,
            isCustom = true,
            isPinned = false,
            createdAt = timeProvider.nowInstant()
        )
        eventTypeRepository.saveEventType(eventType)
        eventType
    }
}

/**
 * Use case to update an existing EventType.
 */
class UpdateEventTypeUseCase(
    private val eventTypeRepository: EventTypeRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(eventType: EventType): Result<Unit> = Result.runCatching {
        val trimmedName = eventType.name.trim()
        if (trimmedName.isBlank()) {
            throw IllegalArgumentException("Event type name cannot be blank")
        }

        // Validate category exists
        categoryRepository.getCategory(eventType.categoryId)
            ?: throw IllegalArgumentException("Category not found for ID: ${eventType.categoryId}")

        // Validate unique name among other types
        val existing = eventTypeRepository.getAllEventTypes().first()
        if (existing.any { it.id != eventType.id && it.name.equals(trimmedName, ignoreCase = true) }) {
            throw IllegalArgumentException("Another event type with name '$trimmedName' already exists")
        }

        eventTypeRepository.saveEventType(eventType.copy(name = trimmedName))
    }
}

/**
 * Use case to safely delete an EventType, verifying no existing events reference it.
 */
class DeleteEventTypeUseCase(
    private val eventTypeRepository: EventTypeRepository,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(eventTypeId: String): Result<Unit> = Result.runCatching {
        val eventType = eventTypeRepository.getEventType(eventTypeId)
            ?: throw IllegalArgumentException("Event type not found")

        // Check if any events reference this event type across all lifecycle states (ACTIVE, ARCHIVED, TRASHED)
        val totalEventCount = eventRepository.countEventsByEventType(eventTypeId)
        if (totalEventCount > 0) {
            throw IllegalStateException(
                "Cannot delete event type '${eventType.name}' because it is in use by $totalEventCount reminder(s)."
            )
        }

        eventTypeRepository.deleteEventType(eventTypeId)
    }
}
