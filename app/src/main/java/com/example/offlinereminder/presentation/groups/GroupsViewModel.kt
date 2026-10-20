package com.example.offlinereminder.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventGroup
import com.example.offlinereminder.domain.repository.EventGroupRepository
import com.example.offlinereminder.domain.usecase.DeleteGroupUseCase
import com.example.offlinereminder.domain.usecase.GetActiveEventsForGroupUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetGroupByIdUseCase
import com.example.offlinereminder.domain.usecase.SaveGroupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel managing Groups listing, searching, creating, updating, and deleting.
 */
class GroupsViewModel(
    private val getEventGroupsUseCase: GetEventGroupsUseCase,
    private val saveGroupUseCase: SaveGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val eventGroupRepository: EventGroupRepository,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState(isLoading = true))
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        observeGroups()
    }

    private fun observeGroups() {
        viewModelScope.launch(dispatcherProvider.main) {
            getEventGroupsUseCase().catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load groups"
                    )
                }
            }.collect { groups ->
                val counts = mutableMapOf<String, Int>()
                for (g in groups) {
                    val events = eventGroupRepository.getActiveEventsForGroup(g.id).firstOrNull() ?: emptyList()
                    counts[g.id] = events.size
                }

                _uiState.update { current ->
                    val filtered = applyFilter(groups, current.searchQuery)
                    current.copy(
                        isLoading = false,
                        groups = groups,
                        filteredGroups = filtered,
                        eventCounts = counts,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun onAction(action: GroupsAction) {
        when (action) {
            is GroupsAction.Search -> {
                _uiState.update { current ->
                    current.copy(
                        searchQuery = action.query,
                        filteredGroups = applyFilter(current.groups, action.query)
                    )
                }
            }
            is GroupsAction.CreateGroup -> createGroup(action)
            is GroupsAction.UpdateGroup -> updateGroup(action.group)
            is GroupsAction.DeleteGroup -> deleteGroup(action.groupId)
            is GroupsAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun applyFilter(groups: List<EventGroup>, query: String): List<EventGroup> {
        if (query.isBlank()) return groups
        val q = query.trim().lowercase()
        return groups.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    private fun createGroup(action: GroupsAction.CreateGroup) {
        viewModelScope.launch(dispatcherProvider.io) {
            val now = timeProvider.nowInstant()
            val newGroup = EventGroup(
                id = UUID.randomUUID().toString(),
                name = action.name.trim(),
                description = action.description.trim(),
                color = action.color,
                icon = action.icon,
                createdAt = now,
                updatedAt = now
            )
            when (val res = saveGroupUseCase(newGroup)) {
                is Result.Failure -> _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                is Result.Success -> {}
            }
        }
    }

    private fun updateGroup(group: EventGroup) {
        viewModelScope.launch(dispatcherProvider.io) {
            val updated = group.copy(updatedAt = timeProvider.nowInstant())
            when (val res = saveGroupUseCase(updated)) {
                is Result.Failure -> _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                is Result.Success -> {}
            }
        }
    }

    private fun deleteGroup(groupId: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = deleteGroupUseCase(groupId)) {
                is Result.Failure -> _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                is Result.Success -> {}
            }
        }
    }
}

/**
 * ViewModel managing a single EventGroup detail and its associated events.
 */
class GroupDetailViewModel(
    private val getGroupByIdUseCase: GetGroupByIdUseCase,
    private val getActiveEventsForGroupUseCase: GetActiveEventsForGroupUseCase,
    private val saveGroupUseCase: SaveGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val eventGroupRepository: EventGroupRepository,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupDetailUiState(isLoading = true))
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private var currentGroupId: String? = null

    fun onAction(action: GroupDetailAction) {
        when (action) {
            is GroupDetailAction.LoadGroup -> loadGroup(action.groupId)
            is GroupDetailAction.UpdateGroup -> updateGroup(action.name, action.description)
            is GroupDetailAction.DeleteGroup -> deleteGroup()
            is GroupDetailAction.RemoveEventFromGroup -> removeEventFromGroup(action.eventId)
            is GroupDetailAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun loadGroup(groupId: String) {
        currentGroupId = groupId
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true) }

            launch {
                getGroupByIdUseCase(groupId).collect { group ->
                    _uiState.update { it.copy(group = group, isLoading = false) }
                }
            }

            launch {
                getActiveEventsForGroupUseCase(groupId).collect { events ->
                    _uiState.update { it.copy(events = events, isLoading = false) }
                }
            }
        }
    }

    private fun updateGroup(name: String, description: String) {
        val current = _uiState.value.group ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            val updated = current.copy(
                name = name.trim(),
                description = description.trim(),
                updatedAt = timeProvider.nowInstant()
            )
            when (val res = saveGroupUseCase(updated)) {
                is Result.Failure -> _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                is Result.Success -> {}
            }
        }
    }

    private fun deleteGroup() {
        val groupId = currentGroupId ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = deleteGroupUseCase(groupId)) {
                is Result.Failure -> _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                is Result.Success -> _uiState.update { it.copy(isDeleted = true) }
            }
        }
    }

    private fun removeEventFromGroup(eventId: String) {
        val groupId = currentGroupId ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = eventGroupRepository.removeEventFromGroup(eventId, groupId)) {
                is Result.Failure -> _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                is Result.Success -> {}
            }
        }
    }
}
