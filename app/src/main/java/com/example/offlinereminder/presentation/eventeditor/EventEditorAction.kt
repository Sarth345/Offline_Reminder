package com.example.offlinereminder.presentation.eventeditor

import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceRule
import java.time.LocalDate

/**
 * User actions and events for the Event Editor screen (Unidirectional Data Flow).
 */
sealed interface EventEditorAction {
    data class InitWithEvent(val eventId: String?) : EventEditorAction
    data class InitWithDefaults(val initialDate: LocalDate?, val initialEventTypeId: String?) : EventEditorAction
    data class UpdateTitle(val title: String) : EventEditorAction
    data class UpdateNotes(val notes: String?) : EventEditorAction
    data class UpdatePriority(val priority: Priority) : EventEditorAction
    data class SelectCategory(val categoryId: String) : EventEditorAction
    data class SelectEventType(val eventTypeId: String) : EventEditorAction
    data class SearchEventType(val query: String) : EventEditorAction
    data class UpdateTemporal(val temporal: EventDateTime) : EventEditorAction
    data class UpdateRecurrence(val rule: RecurrenceRule?) : EventEditorAction
    data class AddNotificationSchedule(val schedule: NotificationSchedule) : EventEditorAction
    data class RemoveNotificationSchedule(val scheduleId: String) : EventEditorAction
    data class UpdateChecklist(val checklist: Checklist?) : EventEditorAction
    data class ToggleGroup(val groupId: String) : EventEditorAction
    data class ToggleTag(val tagId: String) : EventEditorAction
    data class AddAttachment(val attachment: AttachmentMetadata) : EventEditorAction
    data class RemoveAttachment(val attachmentId: String) : EventEditorAction
    data object SaveEvent : EventEditorAction
    data object ClearError : EventEditorAction
}
