package com.example.offlinereminder.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.usecase.CancelNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.EmptyTrashUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.PermanentlyDeleteEventUseCase
import com.example.offlinereminder.domain.usecase.RestoreTrashedEventUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

class TrashViewModel(
    private val eventRepository: EventRepository,
    private val restoreTrashedEventUseCase: RestoreTrashedEventUseCase,
    private val permanentlyDeleteEventUseCase: PermanentlyDeleteEventUseCase,
    private val emptyTrashUseCase: EmptyTrashUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase? = null,
    private val cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase? = null,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState(isLoading = true))
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun onAction(action: TrashAction) {
        when (action) {
            is TrashAction.LoadTrash -> loadData()
            is TrashAction.RestoreEvent -> restoreEvent(action.eventId)
            is TrashAction.PermanentlyDelete -> permanentlyDelete(action.eventId)
            is TrashAction.EmptyTrash -> emptyTrash()
            is TrashAction.ClearMessage -> _uiState.update { it.copy(actionMessage = null, errorMessage = null) }
        }
    }

    private fun loadData() {
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true) }
            val categories = getCategoriesUseCase().firstOrNull()?.associateBy { it.id } ?: emptyMap()
            val eventTypes = getEventTypesUseCase().firstOrNull()?.associateBy { it.id } ?: emptyMap()

            eventRepository.getEventsByLifecycleStatus(EventLifecycleStatus.TRASHED).collect { trashedEvents ->
                val now = timeProvider.nowInstant()
                val items = trashedEvents.map { event ->
                    val deletedAt = event.deletedAt ?: now
                    val daysElapsed = ChronoUnit.DAYS.between(deletedAt, now).toInt()
                    val daysRemaining = (30 - daysElapsed).coerceAtLeast(0)

                    TrashedEventItem(
                        event = event,
                        categoryName = categories[event.categoryId]?.name ?: "General",
                        eventTypeName = eventTypes[event.eventTypeId]?.name ?: "Event",
                        daysRemainingBeforePurge = daysRemaining
                    )
                }
                _uiState.update {
                    it.copy(
                        trashedEvents = items,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun restoreEvent(eventId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = restoreTrashedEventUseCase(eventId)) {
                is Result.Success -> {
                    val restored = eventRepository.getEvent(eventId)
                    if (restored != null && scheduleNotificationsForEventUseCase != null) {
                        scheduleNotificationsForEventUseCase.invoke(restored)
                    }
                    _uiState.update { it.copy(actionMessage = "Event restored to active reminders") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to restore event") }
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

    private fun emptyTrash() {
        viewModelScope.launch(dispatcherProvider.main) {
            val trashed = eventRepository.getEventsByLifecycleStatus(EventLifecycleStatus.TRASHED).firstOrNull() ?: emptyList()
            trashed.forEach { cancelNotificationsForEventUseCase?.invoke(it.id) }
            when (val result = emptyTrashUseCase()) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Emptied trash (${result.data} items permanently deleted)") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to empty trash") }
                }
            }
        }
    }
}
