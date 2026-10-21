package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository
import java.time.Duration

/**
 * Moves an event to Trash (soft delete).
 */
class TrashEventUseCase(
    private val timeProvider: TimeProvider,
    private val eventRepository: EventRepository? = null
) {
    operator fun invoke(event: Event): Event {
        return event.copy(
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
    }

    suspend operator fun invoke(eventId: String): Result<Unit> {
        val repo = eventRepository
            ?: return Result.Failure(IllegalStateException("EventRepository not provided to TrashEventUseCase"))
        if (eventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event id cannot be blank"))
        }
        return repo.moveToTrash(eventId)
    }
}

/**
 * Restores an event from Trash back to ACTIVE status.
 */
class RestoreEventUseCase(
    private val timeProvider: TimeProvider,
    private val eventRepository: EventRepository? = null
) {
    operator fun invoke(event: Event): Event {
        return event.copy(
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            deletedAt = null,
            updatedAt = timeProvider.nowInstant()
        )
    }

    suspend operator fun invoke(eventId: String): Result<Unit> {
        val repo = eventRepository
            ?: return Result.Failure(IllegalStateException("EventRepository not provided to RestoreEventUseCase"))
        if (eventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event id cannot be blank"))
        }
        return repo.restoreFromTrash(eventId)
    }
}

/**
 * Restores a trashed event to ACTIVE status (explicit alias).
 */
class RestoreTrashedEventUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(eventId: String): Result<Unit> {
        if (eventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event id cannot be blank"))
        }
        return eventRepository.restoreFromTrash(eventId)
    }
}

/**
 * Archives an active event.
 */
class ArchiveEventUseCase(
    private val timeProvider: TimeProvider,
    private val eventRepository: EventRepository? = null
) {
    operator fun invoke(event: Event): Event {
        return event.copy(
            lifecycleStatus = EventLifecycleStatus.ARCHIVED,
            deletedAt = null,
            updatedAt = timeProvider.nowInstant()
        )
    }

    suspend operator fun invoke(eventId: String): Result<Unit> {
        val repo = eventRepository
            ?: return Result.Failure(IllegalStateException("EventRepository not provided to ArchiveEventUseCase"))
        if (eventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event id cannot be blank"))
        }
        return repo.archiveEvent(eventId)
    }
}

/**
 * Restores an archived event back to ACTIVE status.
 */
class RestoreArchivedEventUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(eventId: String): Result<Unit> {
        if (eventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event id cannot be blank"))
        }
        return eventRepository.restoreFromArchive(eventId)
    }
}

/**
 * Permanently deletes an event from the database.
 */
class PermanentlyDeleteEventUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(eventId: String): Result<Unit> {
        if (eventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Event id cannot be blank"))
        }
        return eventRepository.deleteEvent(eventId)
    }
}

/**
 * Permanently empties all items currently in Trash.
 */
class EmptyTrashUseCase(
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(): Result<Int> {
        return eventRepository.emptyTrash()
    }
}

/**
 * Purges trashed events older than the retention period (default 30 days).
 */
class PurgeExpiredTrashUseCase(
    private val eventRepository: EventRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(retentionDuration: Duration = Duration.ofDays(30)): Result<Int> {
        val cutoff = timeProvider.nowInstant().minus(retentionDuration)
        return eventRepository.purgeExpiredTrash(cutoff)
    }
}
