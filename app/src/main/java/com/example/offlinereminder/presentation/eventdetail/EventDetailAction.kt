package com.example.offlinereminder.presentation.eventdetail

import com.example.offlinereminder.domain.model.EventOccurrence
import java.time.LocalDateTime

/**
 * User actions and events for the Event Detail screen (Unidirectional Data Flow).
 */
sealed interface EventDetailAction {
    data class LoadEvent(val eventId: String) : EventDetailAction
    data object ToggleFavorite : EventDetailAction
    data object MoveToTrash : EventDetailAction
    data object RestoreFromTrash : EventDetailAction
    data object DeletePermanently : EventDetailAction
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
    data object ClearError : EventDetailAction
}
