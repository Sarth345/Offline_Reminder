package com.example.offlinereminder.presentation.eventlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.resolveEventDisplayStatus
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.usecase.CancelNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing immutable state, filtering, search, and actions for Event collections.
 */
class EventListViewModel(
    private val getEventsUseCase: GetEventsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val getEventGroupsUseCase: GetEventGroupsUseCase,
    private val getTagsUseCase: GetTagsUseCase,
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository? = null,
    private val deleteEventUseCase: DeleteEventUseCase? = null,
    private val cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase? = null,
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase? = null,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventListUiState(isLoading = true))
    val uiState: StateFlow<EventListUiState> = _uiState.asStateFlow()

    private val currentStatus = MutableStateFlow(EventLifecycleStatus.ACTIVE)

    init {
        loadMetadata()
        observeEvents()
    }

    fun onAction(action: EventListAction) {
        when (action) {
            is EventListAction.Search -> {
                _uiState.update { it.copy(searchQuery = action.query) }
                applyFilters()
            }
            is EventListAction.FilterStatus -> {
                currentStatus.value = action.status
                _uiState.update { it.copy(selectedStatus = action.status) }
            }
            is EventListAction.FilterCategory -> {
                _uiState.update { it.copy(selectedCategoryId = action.categoryId) }
                applyFilters()
            }
            is EventListAction.FilterEventType -> {
                _uiState.update { it.copy(selectedEventTypeId = action.eventTypeId) }
                applyFilters()
            }
            is EventListAction.FilterGroup -> {
                _uiState.update { it.copy(selectedGroupId = action.groupId) }
                applyFilters()
            }
            is EventListAction.FilterTag -> {
                _uiState.update { it.copy(selectedTagId = action.tagId) }
                applyFilters()
            }
            is EventListAction.ToggleOnlyFavorites -> {
                _uiState.update { it.copy(onlyFavorites = action.onlyFavorites) }
                applyFilters()
            }
            is EventListAction.ToggleFavorite -> toggleFavorite(action.eventId, action.isFavorite)
            is EventListAction.MoveToTrash -> moveToTrash(action.eventId)
            is EventListAction.RestoreFromTrash -> restoreFromTrash(action.eventId)
            is EventListAction.DeletePermanently -> deletePermanently(action.eventId)
            is EventListAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
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
            }.catch { /* non-fatal metadata loading */ }.collect {}
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeEvents() {
        viewModelScope.launch(dispatcherProvider.main) {
            currentStatus.flatMapLatest { status ->
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val exceptionsFlow = occurrenceExceptionRepository?.getAllExceptionsFlow()
                    ?: kotlinx.coroutines.flow.flowOf(emptyList())
                combine(
                    getEventsUseCase.getByStatus(status),
                    exceptionsFlow
                ) { events, exceptions ->
                    events to exceptions
                }
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load events"
                    )
                }
            }.collect { (events, exceptions) ->
                val nowLdt = timeProvider.nowLocalDateTime()
                val statusMap = events.associate { event ->
                    event.id to resolveEventDisplayStatus(event, exceptions, nowLdt)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        events = events,
                        eventStatusMap = statusMap,
                        errorMessage = null
                    )
                }
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        val query = state.searchQuery.trim()

        val filtered = state.events.filter { event ->
            val matchesQuery = query.isEmpty() ||
                event.title.contains(query, ignoreCase = true) ||
                event.description.contains(query, ignoreCase = true)

            val matchesCategory = state.selectedCategoryId == null || event.categoryId == state.selectedCategoryId
            val matchesEventType = state.selectedEventTypeId == null || event.eventTypeId == state.selectedEventTypeId
            val matchesGroup = state.selectedGroupId == null || event.groupIds.contains(state.selectedGroupId)
            val matchesTag = state.selectedTagId == null || event.tagIds.contains(state.selectedTagId)
            val matchesFavorite = !state.onlyFavorites || event.isFavorite

            matchesQuery && matchesCategory && matchesEventType && matchesGroup && matchesTag && matchesFavorite
        }

        _uiState.update { it.copy(filteredEvents = filtered) }
    }

    private fun toggleFavorite(eventId: String, isFavorite: Boolean) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = eventRepository.setFavorite(eventId, isFavorite)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to update favorite") }
            }
        }
    }

    private fun moveToTrash(eventId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            cancelNotificationsForEventUseCase?.invoke(eventId)
            val result = eventRepository.moveToTrash(eventId)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to move event to trash") }
            }
        }
    }

    private fun restoreFromTrash(eventId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = eventRepository.restoreFromTrash(eventId)
            if (result is Result.Success) {
                val restored = eventRepository.getEvent(eventId)
                if (restored != null && scheduleNotificationsForEventUseCase != null) {
                    scheduleNotificationsForEventUseCase.invoke(restored)
                }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to restore event") }
            }
        }
    }

    private fun deletePermanently(eventId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = if (deleteEventUseCase != null) {
                deleteEventUseCase.invoke(eventId)
            } else {
                cancelNotificationsForEventUseCase?.invoke(eventId)
                eventRepository.deleteEvent(eventId)
            }
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to delete event") }
            }
        }
    }
}
