package com.example.offlinereminder.presentation.upcoming

import com.example.offlinereminder.domain.model.EventOccurrence
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * User actions and events for the Upcoming Agenda / Timeline screen (Unidirectional Data Flow).
 */
sealed interface UpcomingAction {
    data object Refresh : UpcomingAction
    data class SelectMode(val mode: UpcomingMode) : UpcomingAction
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
    data class SelectDate(val date: LocalDate) : UpcomingAction
    data class NavigateDays(val days: Long) : UpcomingAction
    data object JumpToToday : UpcomingAction
    data class ChangeHorizon(val days: Long) : UpcomingAction
    data object ToggleIncludeCompleted : UpcomingAction
    data object ToggleIncludeOverdue : UpcomingAction
    data object ClearError : UpcomingAction
}
