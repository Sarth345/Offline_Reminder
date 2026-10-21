package com.example.offlinereminder.data.repository

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDependency
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventRelationship
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.OccurrenceException
import com.example.offlinereminder.domain.model.Tag
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.CustomSoundRepository
import com.example.offlinereminder.domain.repository.EventGroupRepository
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.EventTemplateRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import com.example.offlinereminder.domain.repository.NotificationProfileRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * In-memory test double for [EventRepository].
 */
class InMemoryEventRepository : EventRepository {
    val events = MutableStateFlow<Map<String, Event>>(emptyMap())

    fun clear() {
        events.value = emptyMap()
    }

    override fun getEventById(id: String): Flow<Event?> =
        events.map { it[id] }

    override suspend fun getEvent(id: String): Event? =
        events.value[id]

    override fun getAllActiveEvents(): Flow<List<Event>> =
        events.map { map -> map.values.filter { it.lifecycleStatus == EventLifecycleStatus.ACTIVE } }

    override fun getAllEvents(): Flow<List<Event>> =
        events.map { it.values.toList() }

    override fun getEventsByLifecycleStatus(status: EventLifecycleStatus): Flow<List<Event>> =
        events.map { map -> map.values.filter { it.lifecycleStatus == status } }

    override fun getFavoriteEvents(): Flow<List<Event>> =
        events.map { map -> map.values.filter { it.isFavorite && it.lifecycleStatus == EventLifecycleStatus.ACTIVE } }

    override fun getEventsByCategory(categoryId: String): Flow<List<Event>> =
        events.map { map -> map.values.filter { it.categoryId == categoryId && it.lifecycleStatus == EventLifecycleStatus.ACTIVE } }

    override fun getEventsByEventType(eventTypeId: String): Flow<List<Event>> =
        events.map { map -> map.values.filter { it.eventTypeId == eventTypeId && it.lifecycleStatus == EventLifecycleStatus.ACTIVE } }

    override fun getSubEvents(parentEventId: String): Flow<List<Event>> =
        events.map { map -> map.values.filter { it.parentEventId == parentEventId } }

    override fun getEventsInDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Event>> =
        events.map { map ->
            map.values.filter {
                val eventStart = it.temporal.startDate
                !eventStart.isBefore(startDate) && !eventStart.isAfter(endDate)
            }
        }

    override suspend fun saveEvent(event: Event): Result<Unit> = Result.runCatching {
        val current = events.value.toMutableMap()
        current[event.id] = event
        events.value = current
    }

    override suspend fun deleteEvent(id: String): Result<Unit> = Result.runCatching {
        val current = events.value.toMutableMap()
        current.remove(id)
        events.value = current
    }

    override suspend fun moveToTrash(id: String): Result<Unit> = Result.runCatching {
        val event = events.value[id] ?: return@runCatching
        saveEvent(event.copy(lifecycleStatus = EventLifecycleStatus.TRASHED))
    }

    override suspend fun restoreFromTrash(id: String): Result<Unit> = Result.runCatching {
        val event = events.value[id] ?: return@runCatching
        saveEvent(event.copy(lifecycleStatus = EventLifecycleStatus.ACTIVE))
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean): Result<Unit> = Result.runCatching {
        val event = events.value[id] ?: return@runCatching
        saveEvent(event.copy(isFavorite = isFavorite))
    }

    override suspend fun countEventsByCategory(categoryId: String): Int =
        events.value.values.count { it.categoryId == categoryId }

    override suspend fun countEventsByEventType(eventTypeId: String): Int =
        events.value.values.count { it.eventTypeId == eventTypeId }

    override suspend fun archiveEvent(id: String): Result<Unit> = Result.runCatching {
        val event = events.value[id] ?: return@runCatching
        saveEvent(event.copy(lifecycleStatus = EventLifecycleStatus.ARCHIVED))
    }

    override suspend fun restoreFromArchive(id: String): Result<Unit> = Result.runCatching {
        val event = events.value[id] ?: return@runCatching
        saveEvent(event.copy(lifecycleStatus = EventLifecycleStatus.ACTIVE))
    }

    override suspend fun emptyTrash(): Result<Int> = Result.runCatching {
        val trashed = events.value.values.filter { it.lifecycleStatus == EventLifecycleStatus.TRASHED }
        val current = events.value.toMutableMap()
        trashed.forEach { current.remove(it.id) }
        events.value = current
        trashed.size
    }

    override suspend fun purgeExpiredTrash(cutoff: Instant): Result<Int> = Result.runCatching {
        val expired = events.value.values.filter { 
            it.lifecycleStatus == EventLifecycleStatus.TRASHED && it.deletedAt != null && !it.deletedAt.isAfter(cutoff)
        }
        val current = events.value.toMutableMap()
        expired.forEach { current.remove(it.id) }
        events.value = current
        expired.size
    }

    private val dependencies = MutableStateFlow<List<EventDependency>>(emptyList())
    private val relationships = MutableStateFlow<List<EventRelationship>>(emptyList())

    override suspend fun addDependency(dependency: EventDependency): Result<Unit> = Result.runCatching {
        dependencies.value = dependencies.value.filterNot { 
            it.sourceEventId == dependency.sourceEventId && it.targetEventId == dependency.targetEventId 
        } + dependency
    }

    override suspend fun removeDependency(sourceEventId: String, targetEventId: String): Result<Unit> = Result.runCatching {
        dependencies.value = dependencies.value.filterNot { 
            it.sourceEventId == sourceEventId && it.targetEventId == targetEventId 
        }
    }

    override fun getDependenciesForEvent(eventId: String): Flow<List<EventDependency>> =
        dependencies.map { list -> list.filter { it.sourceEventId == eventId } }

    override fun getDependentsOfEvent(eventId: String): Flow<List<EventDependency>> =
        dependencies.map { list -> list.filter { it.targetEventId == eventId } }

    override suspend fun addRelationship(relationship: EventRelationship): Result<Unit> = Result.runCatching {
        relationships.value = relationships.value.filterNot {
            (it.sourceEventId == relationship.sourceEventId && it.targetEventId == relationship.targetEventId) ||
            (it.sourceEventId == relationship.targetEventId && it.targetEventId == relationship.sourceEventId)
        } + relationship
    }

    override suspend fun removeRelationship(sourceEventId: String, targetEventId: String): Result<Unit> = Result.runCatching {
        relationships.value = relationships.value.filterNot {
            (it.sourceEventId == sourceEventId && it.targetEventId == targetEventId) ||
            (it.sourceEventId == targetEventId && it.targetEventId == sourceEventId)
        }
    }

    override fun getRelationshipsForEvent(eventId: String): Flow<List<EventRelationship>> =
        relationships.map { list -> 
            list.filter { it.sourceEventId == eventId || it.targetEventId == eventId }
        }
}

/**
 * In-memory test double for [CategoryRepository].
 */
class InMemoryCategoryRepository : CategoryRepository {
    val categories = MutableStateFlow<Map<String, Category>>(emptyMap())

    override fun getAllCategories(): Flow<List<Category>> =
        categories.map { it.values.toList() }

    override suspend fun getCategory(id: String): Category? =
        categories.value[id]

    override suspend fun saveCategory(category: Category): Result<Unit> = Result.runCatching {
        val current = categories.value.toMutableMap()
        current[category.id] = category
        categories.value = current
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = Result.runCatching {
        val current = categories.value.toMutableMap()
        current.remove(id)
        categories.value = current
    }
}

/**
 * In-memory test double for [EventTypeRepository].
 */
class InMemoryEventTypeRepository : EventTypeRepository {
    val types = MutableStateFlow<Map<String, EventType>>(emptyMap())

    override fun getAllEventTypes(): Flow<List<EventType>> =
        types.map { it.values.toList() }

    override fun getEventTypesByCategory(categoryId: String): Flow<List<EventType>> =
        types.map { map -> map.values.filter { it.categoryId == categoryId } }

    override fun searchEventTypes(query: String): Flow<List<EventType>> =
        types.map { map ->
            if (query.isBlank()) map.values.toList()
            else map.values.filter { it.name.contains(query, ignoreCase = true) || it.key.contains(query, ignoreCase = true) }
        }

    override suspend fun getEventType(id: String): EventType? =
        types.value[id]

    override suspend fun getEventTypeByKey(key: String): EventType? =
        types.value.values.firstOrNull { it.key == key }

    override suspend fun saveEventType(eventType: EventType): Result<Unit> = Result.runCatching {
        val current = types.value.toMutableMap()
        current[eventType.id] = eventType
        types.value = current
    }

    override suspend fun deleteEventType(id: String): Result<Unit> = Result.runCatching {
        val current = types.value.toMutableMap()
        current.remove(id)
        types.value = current
    }
}

/**
 * In-memory test double for [EventGroupRepository].
 */
class InMemoryEventGroupRepository(
    private val eventRepository: InMemoryEventRepository? = null
) : EventGroupRepository {
    val groups = MutableStateFlow<Map<String, EventGroup>>(emptyMap())
    val memberships = MutableStateFlow<Map<String, Set<String>>>(emptyMap()) // eventId -> setOf(groupId)
    val customGroupEvents = MutableStateFlow<Map<String, List<Event>>>(emptyMap())

    override fun getAllGroups(): Flow<List<EventGroup>> =
        groups.map { it.values.toList() }

    override fun getGroupById(id: String): Flow<EventGroup?> =
        groups.map { it[id] }

    override suspend fun getGroup(id: String): EventGroup? =
        groups.value[id]

    override fun searchGroups(query: String): Flow<List<EventGroup>> =
        groups.map { map ->
            if (query.isBlank()) map.values.toList()
            else map.values.filter { it.name.contains(query, ignoreCase = true) }
        }

    override fun getGroupsForEvent(eventId: String): Flow<List<EventGroup>> =
        combine(groups, memberships) { groupMap, membershipMap ->
            val set = membershipMap[eventId] ?: emptySet()
            groupMap.values.filter { set.contains(it.id) }
        }

    override fun getActiveEventsForGroup(groupId: String): Flow<List<Event>> {
        if (eventRepository != null) {
            return combine(eventRepository.events, memberships) { eventMap, membershipMap ->
                val matchingEventIds = membershipMap.filter { it.value.contains(groupId) }.keys
                matchingEventIds.mapNotNull { id ->
                    val evt = eventMap[id]
                    if (evt != null && evt.lifecycleStatus == EventLifecycleStatus.ACTIVE) evt else null
                }
            }
        }
        return customGroupEvents.map { it[groupId] ?: emptyList() }
    }

    override suspend fun saveGroup(group: EventGroup): Result<Unit> = Result.runCatching {
        val current = groups.value.toMutableMap()
        current[group.id] = group
        groups.value = current
    }

    override suspend fun deleteGroup(id: String): Result<Unit> = Result.runCatching {
        val current = groups.value.toMutableMap()
        current.remove(id)
        groups.value = current
        val currentMemberships = memberships.value.mapValues { (_, groupSet) -> groupSet - id }
        memberships.value = currentMemberships
    }

    override suspend fun addEventToGroup(eventId: String, groupId: String): Result<Unit> = Result.runCatching {
        val current = memberships.value.toMutableMap()
        val set = current[eventId]?.toMutableSet() ?: mutableSetOf()
        set.add(groupId)
        current[eventId] = set
        memberships.value = current
    }

    override suspend fun removeEventFromGroup(eventId: String, groupId: String): Result<Unit> = Result.runCatching {
        val current = memberships.value.toMutableMap()
        val set = current[eventId]?.toMutableSet() ?: mutableSetOf()
        set.remove(groupId)
        current[eventId] = set
        memberships.value = current
    }
}

/**
 * In-memory test double for [TagRepository].
 */
class InMemoryTagRepository : TagRepository {
    val tags = MutableStateFlow<Map<String, Tag>>(emptyMap())
    val memberships = mutableMapOf<String, MutableSet<String>>() // eventId -> setOf(tagId)

    override fun getAllTags(): Flow<List<Tag>> =
        tags.map { it.values.toList() }

    override fun searchTags(query: String): Flow<List<Tag>> =
        tags.map { map ->
            if (query.isBlank()) map.values.toList()
            else map.values.filter { it.name.contains(query, ignoreCase = true) }
        }

    override suspend fun getTag(id: String): Tag? =
        tags.value[id]

    override suspend fun getTagByNormalizedName(name: String): Tag? =
        tags.value.values.firstOrNull { it.normalizedName == name.lowercase().trim() }

    override fun getTagsForEvent(eventId: String): Flow<List<Tag>> =
        tags.map { map ->
            val set = memberships[eventId] ?: emptySet()
            map.values.filter { set.contains(it.id) }
        }

    override fun getActiveEventsForTag(tagId: String): Flow<List<Event>> =
        MutableStateFlow(emptyList())

    override suspend fun saveTag(tag: Tag): Result<Unit> = Result.runCatching {
        val current = tags.value.toMutableMap()
        current[tag.id] = tag
        tags.value = current
    }

    override suspend fun deleteTag(id: String): Result<Unit> = Result.runCatching {
        val current = tags.value.toMutableMap()
        current.remove(id)
        tags.value = current
    }

    override suspend fun addTagToEvent(eventId: String, tagId: String): Result<Unit> = Result.runCatching {
        memberships.getOrPut(eventId) { mutableSetOf() }.add(tagId)
    }

    override suspend fun removeTagFromEvent(eventId: String, tagId: String): Result<Unit> = Result.runCatching {
        memberships[eventId]?.remove(tagId)
    }
}

/**
 * In-memory test double for [OccurrenceExceptionRepository].
 */
class InMemoryOccurrenceExceptionRepository : OccurrenceExceptionRepository {
    val exceptions = MutableStateFlow<Map<String, OccurrenceException>>(emptyMap())

    fun clear() {
        exceptions.value = emptyMap()
    }

    override fun getExceptionsForEvent(eventId: String): Flow<List<OccurrenceException>> =
        exceptions.map { map -> map.values.filter { it.eventId == eventId } }

    override suspend fun getExceptionByOccurrenceId(occurrenceId: String): OccurrenceException? =
        exceptions.value.values.firstOrNull { it.occurrenceId == occurrenceId }

    override fun getExceptionByOccurrenceIdFlow(occurrenceId: String): Flow<OccurrenceException?> =
        exceptions.map { map -> map.values.firstOrNull { it.occurrenceId == occurrenceId } }

    override suspend fun getExceptionsInRange(startLdt: LocalDateTime, endLdt: LocalDateTime): List<OccurrenceException> =
        exceptions.value.values.filter {
            val start = it.originalStartDateTime
            val resched = it.rescheduledStartDateTime
            (!start.isBefore(startLdt) && !start.isAfter(endLdt)) ||
                (resched != null && !resched.isBefore(startLdt) && !resched.isAfter(endLdt))
        }

    override suspend fun saveException(exception: OccurrenceException): Result<Unit> = Result.runCatching {
        val current = exceptions.value.toMutableMap()
        current[exception.id] = exception
        exceptions.value = current
    }

    override suspend fun deleteException(id: String): Result<Unit> = Result.runCatching {
        val current = exceptions.value.toMutableMap()
        current.remove(id)
        exceptions.value = current
    }

    override suspend fun deleteByOccurrenceId(occurrenceId: String): Result<Unit> = Result.runCatching {
        val current = exceptions.value.toMutableMap()
        current.entries.removeIf { it.value.occurrenceId == occurrenceId }
        exceptions.value = current
    }
}

/**
 * In-memory test double for [NotificationProfileRepository].
 */
class InMemoryNotificationProfileRepository : NotificationProfileRepository {
    val profiles = MutableStateFlow<Map<String, NotificationProfile>>(emptyMap())

    override fun getAllProfiles(): Flow<List<NotificationProfile>> =
        profiles.map { it.values.toList() }

    override suspend fun getProfileById(id: String): NotificationProfile? =
        profiles.value[id]

    override suspend fun getDefaultProfile(): NotificationProfile? =
        profiles.value.values.firstOrNull { it.isDefault }

    override suspend fun saveProfile(profile: NotificationProfile): Result<Unit> = Result.runCatching {
        val current = profiles.value.toMutableMap()
        if (profile.isDefault) {
            current.replaceAll { _, p -> p.copy(isDefault = false) }
        }
        current[profile.id] = profile
        profiles.value = current
    }

    override suspend fun deleteProfile(id: String): Result<Unit> = Result.runCatching {
        val current = profiles.value.toMutableMap()
        current.remove(id)
        profiles.value = current
    }

    override suspend fun setDefaultProfile(id: String): Result<Unit> = Result.runCatching {
        val current = profiles.value.mapValues { (key, profile) ->
            profile.copy(isDefault = key == id)
        }
        profiles.value = current
    }
}

/**
 * In-memory test double for [CustomSoundRepository].
 */
class InMemoryCustomSoundRepository : CustomSoundRepository {
    val sounds = MutableStateFlow<Map<String, CustomSoundMetadata>>(emptyMap())

    override fun getAllSounds(): Flow<List<CustomSoundMetadata>> =
        sounds.map { it.values.toList() }

    override suspend fun getSoundById(id: String): CustomSoundMetadata? =
        sounds.value[id]

    override suspend fun saveSound(metadata: CustomSoundMetadata): Result<Unit> = Result.runCatching {
        val current = sounds.value.toMutableMap()
        current[metadata.id] = metadata
        sounds.value = current
    }

    override suspend fun deleteSound(id: String): Result<Unit> = Result.runCatching {
        val current = sounds.value.toMutableMap()
        current.remove(id)
        sounds.value = current
    }
}

/**
 * In-memory test double for [EventTemplateRepository].
 */
class InMemoryEventTemplateRepository : EventTemplateRepository {
    val templates = MutableStateFlow<Map<String, EventTemplate>>(emptyMap())

    override fun getAllTemplates(): Flow<List<EventTemplate>> =
        templates.map { it.values.toList() }

    override suspend fun getTemplateById(id: String): EventTemplate? =
        templates.value[id]

    override suspend fun saveTemplate(template: EventTemplate): Result<Unit> = Result.runCatching {
        val current = templates.value.toMutableMap()
        current[template.id] = template
        templates.value = current
    }

    override suspend fun deleteTemplate(id: String): Result<Unit> = Result.runCatching {
        val current = templates.value.toMutableMap()
        current.remove(id)
        templates.value = current
    }

    override suspend fun countTemplatesByCategory(categoryId: String): Int =
        templates.value.values.count { it.categoryId == categoryId }

    override suspend fun countTemplatesByEventType(eventTypeId: String): Int =
        templates.value.values.count { it.eventTypeId == eventTypeId }
}
