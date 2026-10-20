package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Tag
import com.example.offlinereminder.domain.repository.CategoryRepository
import com.example.offlinereminder.domain.repository.EventGroupRepository
import com.example.offlinereminder.domain.repository.EventTypeRepository
import com.example.offlinereminder.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow

/**
 * Domain use case to observe and query Categories.
 */
class GetCategoriesUseCase(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(): Flow<List<Category>> = categoryRepository.getAllCategories()

    suspend fun getOnce(id: String): Category? = categoryRepository.getCategory(id)
}

/**
 * Domain use case to observe and search EventTypes.
 */
class GetEventTypesUseCase(
    private val eventTypeRepository: EventTypeRepository
) {
    operator fun invoke(): Flow<List<EventType>> = eventTypeRepository.getAllEventTypes()

    fun search(query: String): Flow<List<EventType>> = eventTypeRepository.searchEventTypes(query)

    suspend fun getOnce(id: String): EventType? = eventTypeRepository.getEventType(id)
}

/**
 * Domain use case to observe and query EventGroups.
 */
class GetEventGroupsUseCase(
    private val eventGroupRepository: EventGroupRepository
) {
    operator fun invoke(): Flow<List<EventGroup>> = eventGroupRepository.getAllGroups()

    fun getForEvent(eventId: String): Flow<List<EventGroup>> = eventGroupRepository.getGroupsForEvent(eventId)

    suspend fun getOnce(id: String): EventGroup? = eventGroupRepository.getGroup(id)
}

/**
 * Domain use case to observe and query Tags.
 */
class GetTagsUseCase(
    private val tagRepository: TagRepository
) {
    operator fun invoke(): Flow<List<Tag>> = tagRepository.getAllTags()

    fun getForEvent(eventId: String): Flow<List<Tag>> = tagRepository.getTagsForEvent(eventId)

    suspend fun getOnce(id: String): Tag? = tagRepository.getTag(id)
}
