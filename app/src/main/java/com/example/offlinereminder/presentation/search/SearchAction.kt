package com.example.offlinereminder.presentation.search

import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.Priority
import java.time.LocalDate

/**
 * User actions for Search and Advanced Filtering.
 */
sealed interface SearchAction {
    data class QueryChanged(val query: String) : SearchAction
    data class ToggleCategory(val categoryId: String) : SearchAction
    data class ToggleEventType(val eventTypeId: String) : SearchAction
    data class ToggleGroup(val groupId: String) : SearchAction
    data class ToggleTag(val tagId: String) : SearchAction
    data class TogglePriority(val priority: Priority) : SearchAction
    data class ToggleLifecycleStatus(val status: EventLifecycleStatus) : SearchAction
    data object ToggleFavorites : SearchAction
    data object ToggleOverdue : SearchAction
    data class SetDateRange(val startDate: LocalDate?, val endDate: LocalDate?) : SearchAction
    data object ClearFilters : SearchAction
    data object ClearSearch : SearchAction
    data object OpenFilterSheet : SearchAction
    data object CloseFilterSheet : SearchAction
    data class ToggleFavorite(val eventId: String, val isFavorite: Boolean) : SearchAction
    data object ClearError : SearchAction
    data object Refresh : SearchAction
}
