package com.example.offlinereminder.domain.quickadd

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Tag
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventGroupRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import com.example.offlinereminder.domain.repository.TagRepository
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * Domain use case executing Quick Add persistence.
 * Resolves or creates categories, event types, groups, and tags,
 * constructs the canonical [Event], and delegates to [CreateEventUseCase].
 */
class CreateEventFromQuickAddUseCase(
    private val createEventUseCase: CreateEventUseCase,
    private val categoryRepository: CategoryRepository,
    private val eventTypeRepository: EventTypeRepository,
    private val eventGroupRepository: EventGroupRepository,
    private val tagRepository: TagRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(draft: QuickAddDraft): Result<Event> {
        if (!draft.isValid) {
            val errorMsg = draft.validationErrors.firstOrNull() ?: "Invalid Quick Add draft"
            return Result.Failure(IllegalArgumentException(errorMsg))
        }

        // 1. Resolve Category
        val categories = runCatching { categoryRepository.getAllCategories().first() }.getOrDefault(emptyList())
        val categoryId = if (!draft.categoryName.isNullOrBlank()) {
            val existing = categories.firstOrNull { it.name.equals(draft.categoryName, ignoreCase = true) }
            if (existing != null) {
                existing.id
            } else {
                val newCat = Category(
                    id = UUID.randomUUID().toString(),
                    name = draft.categoryName,
                    color = 0xFF4285F4.toInt()
                )
                categoryRepository.saveCategory(newCat)
                newCat.id
            }
        } else {
            categories.firstOrNull()?.id ?: run {
                val defaultCat = Category(
                    id = UUID.randomUUID().toString(),
                    name = "General",
                    color = 0xFF4285F4.toInt()
                )
                categoryRepository.saveCategory(defaultCat)
                defaultCat.id
            }
        }

        // 2. Resolve EventType
        val eventTypes = runCatching { eventTypeRepository.getAllEventTypes().first() }.getOrDefault(emptyList())
        val eventTypeId = eventTypes.firstOrNull()?.id ?: "type_reminder"

        // 3. Resolve Groups
        val groupIds = mutableSetOf<String>()
        if (draft.groupNames.isNotEmpty()) {
            val allGroups = runCatching { eventGroupRepository.getAllGroups().first() }.getOrDefault(emptyList())
            for (groupName in draft.groupNames) {
                val existing = allGroups.firstOrNull { it.name.equals(groupName, ignoreCase = true) }
                if (existing != null) {
                    groupIds.add(existing.id)
                } else {
                    val now = timeProvider.nowInstant()
                    val newGroup = EventGroup(
                        id = UUID.randomUUID().toString(),
                        name = groupName,
                        createdAt = now,
                        updatedAt = now
                    )
                    eventGroupRepository.saveGroup(newGroup)
                    groupIds.add(newGroup.id)
                }
            }
        }

        // 4. Resolve Tags
        val tagIds = mutableSetOf<String>()
        if (draft.tagNames.isNotEmpty()) {
            for (tagName in draft.tagNames) {
                val normalized = Tag.normalize(tagName)
                val existing = tagRepository.getTagByNormalizedName(normalized)
                if (existing != null) {
                    tagIds.add(existing.id)
                } else {
                    val newTag = Tag(
                        id = UUID.randomUUID().toString(),
                        name = tagName,
                        normalizedName = normalized
                    )
                    tagRepository.saveTag(newTag)
                    tagIds.add(newTag.id)
                }
            }
        }

        // 5. Construct Temporal representation
        val zone = timeProvider.systemZoneId()
        val temporal: EventDateTime = when {
            draft.date != null && draft.time != null -> {
                EventDateTime.DateTime(
                    dateTime = LocalDateTime.of(draft.date, draft.time),
                    zoneId = zone
                )
            }
            draft.date != null && draft.time == null -> {
                EventDateTime.DateOnly(startDate = draft.date)
            }
            draft.date == null && draft.time != null -> {
                EventDateTime.DateTime(
                    dateTime = LocalDateTime.of(timeProvider.nowLocalDate(), draft.time),
                    zoneId = zone
                )
            }
            else -> {
                EventDateTime.DateOnly(startDate = timeProvider.nowLocalDate())
            }
        }

        // 6. Notification Schedule
        val notificationSchedules = mutableListOf<NotificationSchedule>()
        if (draft.reminderMinutesBefore != null) {
            val schedule = if (draft.reminderMinutesBefore == 0) {
                NotificationSchedule.atStart()
            } else {
                NotificationSchedule.beforeStart(Duration.ofMinutes(draft.reminderMinutesBefore.toLong()))
            }
            notificationSchedules.add(schedule)
        }

        val now = timeProvider.nowInstant()
        val event = Event(
            id = UUID.randomUUID().toString(),
            title = draft.title,
            description = "",
            eventTypeId = eventTypeId,
            categoryId = categoryId,
            priority = draft.priority,
            temporal = temporal,
            recurrenceRule = draft.recurrenceRule,
            notificationSchedules = notificationSchedules,
            groupIds = groupIds,
            tagIds = tagIds,
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )

        return when (val saveResult = createEventUseCase(event)) {
            is Result.Success -> Result.Success(event)
            is Result.Failure -> saveResult
        }
    }
}
