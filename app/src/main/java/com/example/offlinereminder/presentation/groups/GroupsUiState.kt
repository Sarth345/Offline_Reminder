package com.example.offlinereminder.presentation.groups

import com.example.offlinereminder.domain.model.EventGroup

/**
 * Immutable UI state for the Groups management screen.
 */
data class GroupsUiState(
    val isLoading: Boolean = false,
    val groups: List<EventGroup> = emptyList(),
    val filteredGroups: List<EventGroup> = emptyList(),
    val eventCounts: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && filteredGroups.isEmpty()
}

/**
 * Immutable UI state for the Group Details screen.
 */
data class GroupDetailUiState(
    val isLoading: Boolean = false,
    val group: EventGroup? = null,
    val events: List<com.example.offlinereminder.domain.model.Event> = emptyList(),
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && events.isEmpty()
}
