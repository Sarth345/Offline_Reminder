package com.example.offlinereminder.presentation.eventeditor

import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.model.Tag

/**
 * Immutable UI State for the Create / Edit Event screen.
 */
data class EventEditorUiState(
    val isEditing: Boolean = false,
    val eventId: String? = null,
    val title: String = "",
    val notes: String? = null,
    val priority: Priority = Priority.NORMAL,
    val categoryId: String = "",
    val eventTypeId: String = "",
    val temporal: EventDateTime? = null,
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
