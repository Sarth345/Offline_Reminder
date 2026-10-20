package com.example.offlinereminder.presentation.groups

import com.example.offlinereminder.domain.model.EventGroup

/**
 * User actions for Groups management.
 */
sealed interface GroupsAction {
    data class Search(val query: String) : GroupsAction
    data class CreateGroup(
        val name: String,
        val description: String = "",
        val color: Int? = null,
        val icon: String? = null
    ) : GroupsAction
    data class UpdateGroup(val group: EventGroup) : GroupsAction
    data class DeleteGroup(val groupId: String) : GroupsAction
    data object ClearError : GroupsAction
}

/**
 * User actions for Group Detail screen.
 */
sealed interface GroupDetailAction {
    data class LoadGroup(val groupId: String) : GroupDetailAction
    data class UpdateGroup(val name: String, val description: String) : GroupDetailAction
    data object DeleteGroup : GroupDetailAction
    data class RemoveEventFromGroup(val eventId: String) : GroupDetailAction
    data object ClearError : GroupDetailAction
}
