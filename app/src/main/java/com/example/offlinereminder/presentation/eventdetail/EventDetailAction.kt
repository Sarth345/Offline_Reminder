package com.example.offlinereminder.presentation.eventdetail

import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.RelationshipType
import java.time.LocalDateTime

/**
 * User actions and events for the Event Detail screen (Unidirectional Data Flow).
 */
sealed interface EventDetailAction {
    data class LoadEvent(val eventId: String) : EventDetailAction
    data object ToggleFavorite : EventDetailAction
    data object MoveToTrash : EventDetailAction
    data object RestoreFromTrash : EventDetailAction
    data object ArchiveEvent : EventDetailAction
    data object RestoreFromArchive : EventDetailAction
    data object DeletePermanently : EventDetailAction
    data object DuplicateEvent : EventDetailAction
    data class CreateTemplateFromEvent(val templateName: String) : EventDetailAction

    // Checklist Actions
    data class ToggleChecklistItem(val itemId: String) : EventDetailAction
    data class AddChecklistItem(val text: String) : EventDetailAction
    data class DeleteChecklistItem(val itemId: String) : EventDetailAction

    // Dependency & Relationship Actions
    data class AddDependency(val targetEventId: String, val type: DependencyType = DependencyType.PREREQUISITE) : EventDetailAction
    data class RemoveDependency(val targetEventId: String) : EventDetailAction
    data class AddRelationship(val targetEventId: String, val type: RelationshipType = RelationshipType.RELATED_TO) : EventDetailAction
    data class RemoveRelationship(val targetEventId: String) : EventDetailAction

    // Reminders
    data class AddReminder(val schedule: NotificationSchedule) : EventDetailAction
    data class RemoveReminder(val scheduleId: String) : EventDetailAction

    // Attachments
    data class AddAttachment(val attachment: AttachmentMetadata) : EventDetailAction
    data class RemoveAttachment(val attachmentId: String) : EventDetailAction

    // Occurrence Actions
    data class CompleteOccurrence(
        val occurrence: EventOccurrence,
        val actualExecutionDateTime: LocalDateTime? = null
    ) : EventDetailAction
    data class SkipOccurrence(val occurrence: EventOccurrence) : EventDetailAction
    data class RescheduleOccurrence(
        val occurrence: EventOccurrence,
        val newStartDateTime: LocalDateTime,
        val newEndDateTime: LocalDateTime? = null
    ) : EventDetailAction
    data class UndoOccurrenceAction(val occurrenceId: String) : EventDetailAction

    data object ClearDuplicatedEventId : EventDetailAction
    data object ClearMessage : EventDetailAction
    data object ClearError : EventDetailAction
}
