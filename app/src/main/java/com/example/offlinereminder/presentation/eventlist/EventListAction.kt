package com.example.offlinereminder.presentation.eventlist

import com.example.offlinereminder.domain.model.EventLifecycleStatus

/**
 * User actions and events for Event List screens (Unidirectional Data Flow).
 */
sealed interface EventListAction {
    data class Search(val query: String) : EventListAction
    data class FilterStatus(val status: EventLifecycleStatus) : EventListAction
    data class FilterCategory(val categoryId: String?) : EventListAction
    data class FilterEventType(val eventTypeId: String?) : EventListAction
    data class FilterGroup(val groupId: String?) : EventListAction
    data class FilterTag(val tagId: String?) : EventListAction
    data class ToggleOnlyFavorites(val onlyFavorites: Boolean) : EventListAction
    data class ToggleFavorite(val eventId: String, val isFavorite: Boolean) : EventListAction
    data class MoveToTrash(val eventId: String) : EventListAction
    data class RestoreFromTrash(val eventId: String) : EventListAction
    data class DeletePermanently(val eventId: String) : EventListAction
    data object ClearError : EventListAction
}
