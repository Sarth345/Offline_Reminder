package com.example.offlinereminder.domain.usecase

import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event

class AddEventToGroupUseCase(
    private val timeProvider: TimeProvider
) {
    operator fun invoke(event: Event, groupId: String): Event {
        if (event.groupIds.contains(groupId)) return event
        return event.copy(
            groupIds = event.groupIds + groupId,
            updatedAt = timeProvider.nowInstant()
        )
    }
}

class RemoveEventFromGroupUseCase(
    private val timeProvider: TimeProvider
) {
    operator fun invoke(event: Event, groupId: String): Event {
        if (!event.groupIds.contains(groupId)) return event
        return event.copy(
            groupIds = event.groupIds - groupId,
            updatedAt = timeProvider.nowInstant()
        )
    }
}

class AddTagToEventUseCase(
    private val timeProvider: TimeProvider
) {
    operator fun invoke(event: Event, tagId: String): Event {
        if (event.tagIds.contains(tagId)) return event
        return event.copy(
            tagIds = event.tagIds + tagId,
            updatedAt = timeProvider.nowInstant()
        )
    }
}

class RemoveTagFromEventUseCase(
    private val timeProvider: TimeProvider
) {
    operator fun invoke(event: Event, tagId: String): Event {
        if (!event.tagIds.contains(tagId)) return event
        return event.copy(
            tagIds = event.tagIds - tagId,
            updatedAt = timeProvider.nowInstant()
        )
    }
}
