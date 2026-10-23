package com.example.offlinereminder.presentation.eventdetail

import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDependency
import com.example.offlinereminder.domain.model.EventDisplayStatus
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.EventRelationship
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.ScheduleConflict
import com.example.offlinereminder.domain.model.Tag

/**
 * Immutable UI State for the Event Detail screen.
 * Encapsulates the entire event management surface: metadata, occurrences,
 * checklists, sub-events, attachments, dependencies, relationships,
 * conflicts, and notification profiles.
 */
data class EventDetailUiState(
    val isLoading: Boolean = false,
    val event: Event? = null,
    val category: Category? = null,
    val eventType: EventType? = null,
    val groups: List<EventGroup> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val occurrences: List<EventOccurrence> = emptyList(),
    val primaryOccurrence: EventOccurrence? = null,
    val displayStatus: EventDisplayStatus = EventDisplayStatus.ACTIVE,
    val parentEvent: Event? = null,
    val subEvents: List<Event> = emptyList(),
    val dependencies: List<EventDependency> = emptyList(),
    val dependents: List<EventDependency> = emptyList(),
    val relationships: List<EventRelationship> = emptyList(),
    val resolvedLinkedEvents: Map<String, Event> = emptyMap(),
    val conflicts: List<ScheduleConflict> = emptyList(),
    val notificationProfiles: Map<String, NotificationProfile> = emptyMap(),
    val customSounds: Map<String, CustomSoundMetadata> = emptyMap(),
    val availableEventsForLinking: List<Event> = emptyList(),
    val availableNotificationProfiles: List<NotificationProfile> = emptyList(),
    val availableCustomSounds: List<CustomSoundMetadata> = emptyList(),
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val duplicatedEventId: String? = null,
    val actionMessage: String? = null,
    val errorMessage: String? = null
) {
    val totalChecklistCount: Int
        get() = event?.checklist?.items?.size ?: 0

    val completedChecklistCount: Int
        get() = event?.checklist?.items?.count { it.isCompleted } ?: 0

    val checklistProgress: Float
        get() = if (totalChecklistCount > 0) completedChecklistCount.toFloat() / totalChecklistCount.toFloat() else 0f
}
