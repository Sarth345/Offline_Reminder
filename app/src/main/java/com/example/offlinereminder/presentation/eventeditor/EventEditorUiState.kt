package com.example.offlinereminder.presentation.eventeditor

import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.CustomSoundMetadata
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.NotificationProfile
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.ScheduleConflict
import com.example.offlinereminder.domain.model.Tag
import java.time.ZoneId

enum class EventDateMode {
    DATE_ONLY,
    DATE_TIME,
    DATE_RANGE,
    DATE_RANGE_TIME
}

/**
 * Immutable UI State for the Create / Edit Event screen.
 */
data class EventEditorUiState(
    val isEditing: Boolean = false,
    val eventId: String? = null,
    val parentEventId: String? = null,
    val parentEventTitle: String? = null,
    val title: String = "",
    val notes: String? = null,
    val locationDisplayName: String = "",
    val priority: Priority = Priority.NORMAL,
    val categoryId: String = "",
    val eventTypeId: String = "",
    val temporal: EventDateTime? = null,
    val dateMode: EventDateMode = EventDateMode.DATE_TIME,
    val timeZoneId: String = ZoneId.systemDefault().id,
    val recurrenceRule: RecurrenceRule? = null,
    val notificationSchedules: List<NotificationSchedule> = emptyList(),
    val checklist: Checklist? = null,
    val groupIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val attachments: List<AttachmentMetadata> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val availableEventTypes: List<EventType> = emptyList(),
    val availableGroups: List<EventGroup> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val availableNotificationProfiles: List<NotificationProfile> = emptyList(),
    val availableCustomSounds: List<CustomSoundMetadata> = emptyList(),
    val availableTemplates: List<EventTemplate> = emptyList(),
    val detectedConflicts: List<ScheduleConflict> = emptyList(),
    val eventTypeSearchQuery: String = "",
    val filteredEventTypes: List<EventType> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val validationError: String? = null,
    val errorMessage: String? = null
) {
    val canSave: Boolean
        get() = title.isNotBlank() &&
            categoryId.isNotBlank() &&
            eventTypeId.isNotBlank() &&
            temporal != null &&
            !isSaving
}
