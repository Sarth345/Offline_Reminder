package com.example.offlinereminder.presentation.eventlist

import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDisplayStatus
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Tag

/**
 * Immutable UI State for the Event List / Management screens.
 */
data class EventListUiState(
    val isLoading: Boolean = false,
    val events: List<Event> = emptyList(),
    val filteredEvents: List<Event> = emptyList(),
    val eventStatusMap: Map<String, EventDisplayStatus> = emptyMap(),
    val searchQuery: String = "",
    val selectedStatus: EventLifecycleStatus = EventLifecycleStatus.ACTIVE,
    val selectedCategoryId: String? = null,
    val selectedEventTypeId: String? = null,
    val selectedGroupId: String? = null,
    val selectedTagId: String? = null,
    val onlyFavorites: Boolean = false,
    val availableCategories: List<Category> = emptyList(),
    val availableEventTypes: List<EventType> = emptyList(),
    val availableGroups: List<EventGroup> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && filteredEvents.isEmpty()
}
