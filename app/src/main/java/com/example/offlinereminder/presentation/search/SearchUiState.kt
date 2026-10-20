package com.example.offlinereminder.presentation.search

import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventFilterParams
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.Tag
import java.time.LocalDate

/**
 * Immutable UI state for advanced event search and discovery.
 */
data class SearchUiState(
    val filterParams: EventFilterParams = EventFilterParams(),
    val results: List<Event> = emptyList(),
    val isLoading: Boolean = false,
    val isFilterSheetVisible: Boolean = false,
    val availableCategories: List<Category> = emptyList(),
    val availableEventTypes: List<EventType> = emptyList(),
    val availableGroups: List<EventGroup> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val errorMessage: String? = null
) {
    val searchQuery: String get() = filterParams.query
    val selectedCategoryIds: Set<String> get() = filterParams.categoryIds
    val selectedEventTypeIds: Set<String> get() = filterParams.eventTypeIds
    val selectedGroupIds: Set<String> get() = filterParams.groupIds
    val selectedTagIds: Set<String> get() = filterParams.tagIds
    val selectedPriorities: Set<Priority> get() = filterParams.priorities
    val selectedLifecycleStatuses: Set<EventLifecycleStatus> get() = filterParams.lifecycleStatuses
    val onlyFavorites: Boolean get() = filterParams.onlyFavorites
    val onlyOverdue: Boolean get() = filterParams.onlyOverdue
    val startDate: LocalDate? get() = filterParams.startDate
    val endDate: LocalDate? get() = filterParams.endDate

    val activeFilterCount: Int get() = filterParams.activeFilterCount
    val hasActiveFilters: Boolean get() = filterParams.hasActiveFilters

    val isEmptyResults: Boolean get() = !isLoading && results.isEmpty()
}
