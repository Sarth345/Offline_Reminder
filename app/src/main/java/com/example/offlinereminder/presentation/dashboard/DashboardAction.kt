package com.example.offlinereminder.presentation.dashboard

import com.example.offlinereminder.domain.model.EventOccurrence
import java.time.LocalDateTime

/**
 * User actions and events for the Dashboard screen (Unidirectional Data Flow).
 */
sealed interface DashboardAction {
    data object Refresh : DashboardAction
    data class CompleteOccurrence(
        val occurrence: EventOccurrence,
        val actualExecutionDateTime: LocalDateTime? = null
    ) : DashboardAction
    data class SkipOccurrence(val occurrence: EventOccurrence) : DashboardAction
    data class RescheduleOccurrence(
        val occurrence: EventOccurrence,
        val newStartDateTime: LocalDateTime,
        val newEndDateTime: LocalDateTime? = null
    ) : DashboardAction
    data class ToggleFavorite(val eventId: String) : DashboardAction
    data object ClearError : DashboardAction
}
