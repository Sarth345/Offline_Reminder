package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.repository.EventGroupRepository
import kotlinx.coroutines.flow.Flow

/**
 * Domain use case to validate and persist an EventGroup.
 */
class SaveGroupUseCase(
    private val eventGroupRepository: EventGroupRepository
) {
    suspend operator fun invoke(group: EventGroup): Result<Unit> {
        if (group.name.isBlank()) {
            return Result.Failure(IllegalArgumentException("Group name cannot be blank"))
        }
        return eventGroupRepository.saveGroup(group)
    }
}

/**
 * Domain use case to delete an EventGroup.
 * Note: Group deletion deletes only the group entity and membership cross-references;
 * it NEVER deletes the events themselves.
 */
class DeleteGroupUseCase(
    private val eventGroupRepository: EventGroupRepository
) {
    suspend operator fun invoke(groupId: String): Result<Unit> =
        eventGroupRepository.deleteGroup(groupId)
}

/**
 * Domain use case to observe a specific EventGroup by ID.
 */
class GetGroupByIdUseCase(
    private val eventGroupRepository: EventGroupRepository
) {
    operator fun invoke(groupId: String): Flow<EventGroup?> =
        eventGroupRepository.getGroupById(groupId)

    suspend fun getOnce(groupId: String): EventGroup? =
        eventGroupRepository.getGroup(groupId)
}

/**
 * Domain use case to observe active Events belonging to a specific EventGroup.
 */
class GetActiveEventsForGroupUseCase(
    private val eventGroupRepository: EventGroupRepository
) {
    operator fun invoke(groupId: String): Flow<List<Event>> =
        eventGroupRepository.getActiveEventsForGroup(groupId)
}
