package com.example.offlinereminder.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.EventFilterParams
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.SearchEventsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing reactive search, advanced filter state, and metadata loading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchEventsUseCase: SearchEventsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val getEventGroupsUseCase: GetEventGroupsUseCase,
    private val getTagsUseCase: GetTagsUseCase,
    private val eventRepository: EventRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val currentParams = MutableStateFlow(EventFilterParams())

    private val _uiState = MutableStateFlow(SearchUiState(isLoading = true))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadMetadata()
        observeSearchResults()
    }

    private fun loadMetadata() {
        viewModelScope.launch(dispatcherProvider.main) {
            combine(
                getCategoriesUseCase(),
                getEventTypesUseCase(),
                getEventGroupsUseCase(),
                getTagsUseCase()
            ) { categories, types, groups, tags ->
                _uiState.update {
                    it.copy(
                        availableCategories = categories,
                        availableEventTypes = types,
                        availableGroups = groups,
                        availableTags = tags
                    )
                }
            }.catch { /* non-fatal metadata loading failure */ }.collect {}
        }
    }

    private fun observeSearchResults() {
        viewModelScope.launch(dispatcherProvider.main) {
            currentParams.flatMapLatest { params ->
                _uiState.update { it.copy(isLoading = true, filterParams = params, errorMessage = null) }
                searchEventsUseCase(params)
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to execute search"
                    )
                }
            }.collect { events ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = events,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> {
                currentParams.update { it.copy(query = action.query) }
            }
            is SearchAction.ToggleCategory -> {
                currentParams.update {
                    val newSet = it.categoryIds.toMutableSet()
                    if (newSet.contains(action.categoryId)) newSet.remove(action.categoryId)
                    else newSet.add(action.categoryId)
                    it.copy(categoryIds = newSet)
                }
            }
            is SearchAction.ToggleEventType -> {
                currentParams.update {
                    val newSet = it.eventTypeIds.toMutableSet()
                    if (newSet.contains(action.eventTypeId)) newSet.remove(action.eventTypeId)
                    else newSet.add(action.eventTypeId)
                    it.copy(eventTypeIds = newSet)
                }
            }
            is SearchAction.ToggleGroup -> {
                currentParams.update {
                    val newSet = it.groupIds.toMutableSet()
                    if (newSet.contains(action.groupId)) newSet.remove(action.groupId)
                    else newSet.add(action.groupId)
                    it.copy(groupIds = newSet)
                }
            }
            is SearchAction.ToggleTag -> {
                currentParams.update {
                    val newSet = it.tagIds.toMutableSet()
                    if (newSet.contains(action.tagId)) newSet.remove(action.tagId)
                    else newSet.add(action.tagId)
                    it.copy(tagIds = newSet)
                }
            }
            is SearchAction.TogglePriority -> {
                currentParams.update {
                    val newSet = it.priorities.toMutableSet()
                    if (newSet.contains(action.priority)) newSet.remove(action.priority)
                    else newSet.add(action.priority)
                    it.copy(priorities = newSet)
                }
            }
            is SearchAction.ToggleLifecycleStatus -> {
                currentParams.update {
                    val newSet = it.lifecycleStatuses.toMutableSet()
                    if (newSet.contains(action.status)) newSet.remove(action.status)
                    else newSet.add(action.status)
                    it.copy(lifecycleStatuses = newSet)
                }
            }
            is SearchAction.ToggleFavorites -> {
                currentParams.update { it.copy(onlyFavorites = !it.onlyFavorites) }
            }
            is SearchAction.ToggleOverdue -> {
                currentParams.update { it.copy(onlyOverdue = !it.onlyOverdue) }
            }
            is SearchAction.SetDateRange -> {
                currentParams.update { it.copy(startDate = action.startDate, endDate = action.endDate) }
            }
            is SearchAction.ClearFilters -> {
                currentParams.update {
                    it.copy(
                        categoryIds = emptySet(),
                        eventTypeIds = emptySet(),
                        groupIds = emptySet(),
                        tagIds = emptySet(),
                        priorities = emptySet(),
                        lifecycleStatuses = setOf(EventLifecycleStatus.ACTIVE),
                        onlyFavorites = false,
                        onlyOverdue = false,
                        startDate = null,
                        endDate = null
                    )
                }
            }
            is SearchAction.ClearSearch -> {
                currentParams.update { it.copy(query = "") }
            }
            is SearchAction.OpenFilterSheet -> {
                _uiState.update { it.copy(isFilterSheetVisible = true) }
            }
            is SearchAction.CloseFilterSheet -> {
                _uiState.update { it.copy(isFilterSheetVisible = false) }
            }
            is SearchAction.ToggleFavorite -> {
                toggleFavorite(action.eventId, action.isFavorite)
            }
            is SearchAction.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is SearchAction.Refresh -> {
                currentParams.update { it.copy() }
            }
        }
    }

    private fun toggleFavorite(eventId: String, isFavorite: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = eventRepository.setFavorite(eventId, isFavorite)) {
                is Result.Failure -> _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                is Result.Success -> {}
            }
        }
    }
}
