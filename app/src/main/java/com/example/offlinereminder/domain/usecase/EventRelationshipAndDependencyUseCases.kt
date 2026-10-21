package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.EventDependency
import com.example.offlinereminder.domain.model.EventRelationship
import com.example.offlinereminder.domain.model.RelationshipType
import com.example.offlinereminder.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow

class AddEventDependencyUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        sourceEventId: String,
        targetEventId: String,
        type: DependencyType = DependencyType.PREREQUISITE
    ): Result<Unit> {
        if (sourceEventId.isBlank() || targetEventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event IDs cannot be blank"))
        }
        if (sourceEventId == targetEventId) {
            return Result.Failure(IllegalArgumentException("An event cannot depend on itself: $sourceEventId"))
        }

        val source = eventRepository.getEvent(sourceEventId)
            ?: return Result.Failure(IllegalArgumentException("Source event with id $sourceEventId not found"))
        val target = eventRepository.getEvent(targetEventId)
            ?: return Result.Failure(IllegalArgumentException("Target event with id $targetEventId not found"))

        val dependency = EventDependency(
            sourceEventId = source.id,
            targetEventId = target.id,
            type = type
        )
        return eventRepository.addDependency(dependency)
    }
}

class RemoveEventDependencyUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        sourceEventId: String,
        targetEventId: String
    ): Result<Unit> {
        if (sourceEventId.isBlank() || targetEventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event IDs cannot be blank"))
        }
        return eventRepository.removeDependency(sourceEventId, targetEventId)
    }
}

class GetEventDependenciesUseCase(
    private val eventRepository: EventRepository
) {
    operator fun invoke(eventId: String): Flow<List<EventDependency>> =
        eventRepository.getDependenciesForEvent(eventId)
}

class GetEventDependentsUseCase(
    private val eventRepository: EventRepository
) {
    operator fun invoke(eventId: String): Flow<List<EventDependency>> =
        eventRepository.getDependentsOfEvent(eventId)
}

class AddEventRelationshipUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        sourceEventId: String,
        targetEventId: String,
        type: RelationshipType = RelationshipType.RELATED_TO
    ): Result<Unit> {
        if (sourceEventId.isBlank() || targetEventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event IDs cannot be blank"))
        }
        if (sourceEventId == targetEventId) {
            return Result.Failure(IllegalArgumentException("An event cannot relate to itself: $sourceEventId"))
        }

        val source = eventRepository.getEvent(sourceEventId)
            ?: return Result.Failure(IllegalArgumentException("Source event with id $sourceEventId not found"))
        val target = eventRepository.getEvent(targetEventId)
            ?: return Result.Failure(IllegalArgumentException("Target event with id $targetEventId not found"))

        val relationship = EventRelationship(
            sourceEventId = source.id,
            targetEventId = target.id,
            type = type
        )
        return eventRepository.addRelationship(relationship)
    }
}

class RemoveEventRelationshipUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        sourceEventId: String,
        targetEventId: String
    ): Result<Unit> {
        if (sourceEventId.isBlank() || targetEventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event IDs cannot be blank"))
        }
        return eventRepository.removeRelationship(sourceEventId, targetEventId)
    }
}

class GetEventRelationshipsUseCase(
    private val eventRepository: EventRepository
) {
    operator fun invoke(eventId: String): Flow<List<EventRelationship>> =
        eventRepository.getRelationshipsForEvent(eventId)
}
