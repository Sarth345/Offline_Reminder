package com.example.offlinereminder.presentation.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.usecase.CancelNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.PermanentlyDeleteEventUseCase
import com.example.offlinereminder.domain.usecase.RestoreArchivedEventUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.TrashEventUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArchiveViewModel(
    private val eventRepository: EventRepository,
    private val restoreArchivedEventUseCase: RestoreArchivedEventUseCase,
    private val trashEventUseCase: TrashEventUseCase,
    private val permanentlyDeleteEventUseCase: PermanentlyDeleteEventUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase? = null,
    private val cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase? = null,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState(isLoading = true))
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun onAction(action: ArchiveAction) {
        when (action) {
            is ArchiveAction.LoadArchive -> loadData()
            is ArchiveAction.UnarchiveEvent -> unarchiveEvent(action.eventId)
            is ArchiveAction.MoveToTrash -> moveToTrash(action.eventId)
            is ArchiveAction.PermanentlyDelete -> permanentlyDelete(action.eventId)
            is ArchiveAction.ClearMessage -> _uiState.update { it.copy(actionMessage = null, errorMessage = null) }
        }
    }

    private fun loadData() {
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true) }
            val categories = getCategoriesUseCase().firstOrNull()?.associateBy { it.id } ?: emptyMap()
            val eventTypes = getEventTypesUseCase().firstOrNull()?.associateBy { it.id } ?: emptyMap()

            eventRepository.getEventsByLifecycleStatus(EventLifecycleStatus.ARCHIVED).collect { archivedEvents ->
                val items = archivedEvents.map { event ->
                    ArchivedEventItem(
                        event = event,
                        categoryName = categories[event.categoryId]?.name ?: "General",
                        eventTypeName = eventTypes[event.eventTypeId]?.name ?: "Event"
                    )
                }
                _uiState.update {
                    it.copy(
                        archivedEvents = items,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun unarchiveEvent(eventId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = restoreArchivedEventUseCase(eventId)) {
                is Result.Success -> {
                    val restored = eventRepository.getEvent(eventId)
                    if (restored != null && scheduleNotificationsForEventUseCase != null) {
                        scheduleNotificationsForEventUseCase.invoke(restored)
                    }
                    _uiState.update { it.copy(actionMessage = "Event restored to active reminders") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to unarchive event") }
                }
            }
        }
    }

    private fun moveToTrash(eventId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            cancelNotificationsForEventUseCase?.invoke(eventId)
            when (val result = trashEventUseCase(eventId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Event moved to trash") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to move event to trash") }
                }
            }
        }
    }

    private fun permanentlyDelete(eventId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            cancelNotificationsForEventUseCase?.invoke(eventId)
            when (val result = permanentlyDeleteEventUseCase(eventId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Event permanently deleted") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to delete event") }
                }
            }
        }
    }
}
