package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus

/**
 * Moves an event to Trash (soft delete).
 */
class TrashEventUseCase(
    private val timeProvider: TimeProvider
) {
    operator fun invoke(event: Event): Event {
        return event.copy(
            lifecycleStatus = EventLifecycleStatus.TRASHED,
            deletedAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )
    }
}

/**
 * Restores an event from Trash or Archive back to ACTIVE status.
 */
class RestoreEventUseCase(
    private val timeProvider: TimeProvider
) {
    operator fun invoke(event: Event): Event {
        return event.copy(
            lifecycleStatus = EventLifecycleStatus.ACTIVE,
            deletedAt = null,
            updatedAt = timeProvider.nowInstant()
        )
    }
}

/**
 * Archives an active event.
 */
class ArchiveEventUseCase(
    private val timeProvider: TimeProvider
) {
    operator fun invoke(event: Event): Event {
        return event.copy(
            lifecycleStatus = EventLifecycleStatus.ARCHIVED,
            deletedAt = null,
            updatedAt = timeProvider.nowInstant()
        )
    }
}
