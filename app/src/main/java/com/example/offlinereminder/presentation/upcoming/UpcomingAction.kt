package com.example.offlinereminder.presentation.upcoming

import com.example.offlinereminder.domain.model.EventOccurrence
import java.time.LocalDateTime

/**
 * User actions and events for the Upcoming screen (Unidirectional Data Flow).
 */
sealed interface UpcomingAction {
    data object Refresh : UpcomingAction
    data class CompleteOccurrence(
        val occurrence: EventOccurrence,
        val actualExecutionDateTime: LocalDateTime? = null
    ) : UpcomingAction
    data class SkipOccurrence(val occurrence: EventOccurrence) : UpcomingAction
    data class RescheduleOccurrence(
        val occurrence: EventOccurrence,
        val newStartDateTime: LocalDateTime,
        val newEndDateTime: LocalDateTime? = null
    ) : UpcomingAction
    data object ClearError : UpcomingAction
}
