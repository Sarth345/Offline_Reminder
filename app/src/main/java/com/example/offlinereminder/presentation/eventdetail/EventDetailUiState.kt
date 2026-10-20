package com.example.offlinereminder.presentation.eventdetail

import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.Tag

/**
 * Immutable UI State for the Event Detail screen.
 */
data class EventDetailUiState(
    val isLoading: Boolean = false,
    val event: Event? = null,
    val category: Category? = null,
    val eventType: EventType? = null,
    val groups: List<EventGroup> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val occurrences: List<EventOccurrence> = emptyList(),
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null
)
