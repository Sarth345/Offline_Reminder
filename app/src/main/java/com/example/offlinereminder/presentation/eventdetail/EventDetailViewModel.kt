package com.example.offlinereminder.presentation.eventdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.usecase.CancelNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * ViewModel managing immutable state and user actions for the Event Detail screen.
 */
class EventDetailViewModel(
    private val getEventByIdUseCase: GetEventByIdUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val getEventGroupsUseCase: GetEventGroupsUseCase,
    private val getTagsUseCase: GetTagsUseCase,
    private val eventRepository: EventRepository,
    private val occurrenceExceptionRepository: OccurrenceExceptionRepository,
    private val recurrenceEngine: RecurrenceEngine = DefaultRecurrenceEngine(),
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase,
    private val skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase,
    private val rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase? = null,
    private val deleteEventUseCase: DeleteEventUseCase? = null,
    private val cancelNotificationsForEventUseCase: CancelNotificationsForEventUseCase? = null,
    private val scheduleNotificationsForEventUseCase: ScheduleNotificationsForEventUseCase? = null,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventDetailUiState(isLoading = true))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private var currentEventId: String? = null

    fun onAction(action: EventDetailAction) {
        when (action) {
            is EventDetailAction.LoadEvent -> loadEvent(action.eventId)
            is EventDetailAction.ToggleFavorite -> toggleFavorite()
            is EventDetailAction.MoveToTrash -> moveToTrash()
            is EventDetailAction.RestoreFromTrash -> restoreFromTrash()
            is EventDetailAction.DeletePermanently -> deletePermanently()
            is EventDetailAction.CompleteOccurrence -> completeOccurrence(action.occurrence, action.actualExecutionDateTime)
            is EventDetailAction.SkipOccurrence -> skipOccurrence(action.occurrence)
            is EventDetailAction.RescheduleOccurrence -> rescheduleOccurrence(action.occurrence, action.newStartDateTime, action.newEndDateTime)
            is EventDetailAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun loadEvent(eventId: String) {
        currentEventId = eventId
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getEventByIdUseCase(eventId)
                .catch { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load event")
                    }
                }
                .collect { event ->
                    if (event == null) {
                        _uiState.update { it.copy(isLoading = false, event = null, errorMessage = "Event not found") }
                        return@collect
                    }
                    populateEventDetails(event)
                }
        }
    }

    private suspend fun populateEventDetails(event: Event) {
        val category = getCategoriesUseCase.getOnce(event.categoryId)
        val eventType = getEventTypesUseCase.getOnce(event.eventTypeId)
        val allGroups = getEventGroupsUseCase().firstOrNull() ?: emptyList()
        val eventGroups = allGroups.filter { event.groupIds.contains(it.id) }
        val allTags = getTagsUseCase().firstOrNull() ?: emptyList()
        val eventTags = allTags.filter { event.tagIds.contains(it.id) }

        val nowLdt = timeProvider.nowLocalDateTime()
        val horizonEnd = nowLdt.plusDays(30)
        val exceptions = occurrenceExceptionRepository.getExceptionsInRange(nowLdt.minusDays(1), horizonEnd)
            .filter { it.eventId == event.id }

        val projectedOccurrences = recurrenceEngine.projectOccurrences(
            event = event,
            from = nowLdt,
            to = horizonEnd,
            exceptions = exceptions,
            evaluateOverdue = true,
            timeProvider = timeProvider
        )

        _uiState.update {
            it.copy(
                isLoading = false,
                event = event,
                category = category,
                eventType = eventType,
                groups = eventGroups,
                tags = eventTags,
                occurrences = projectedOccurrences,
                isFavorite = event.isFavorite,
                errorMessage = null
            )
        }
    }

    private fun toggleFavorite() {
        val event = _uiState.value.event ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val newFav = !event.isFavorite
            val result = eventRepository.setFavorite(event.id, newFav)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to update favorite") }
            }
        }
    }

    private fun moveToTrash() {
        val eventId = currentEventId ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            cancelNotificationsForEventUseCase?.invoke(eventId)
            val result = eventRepository.moveToTrash(eventId)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to move event to trash") }
            }
        }
    }

    private fun restoreFromTrash() {
        val eventId = currentEventId ?: return
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

    private fun deletePermanently() {
        val eventId = currentEventId ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val result = if (deleteEventUseCase != null) {
                deleteEventUseCase.invoke(eventId)
            } else {
                cancelNotificationsForEventUseCase?.invoke(eventId)
                eventRepository.deleteEvent(eventId)
            }
            if (result is Result.Success) {
                _uiState.update { it.copy(isDeleted = true) }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to delete event") }
            }
        }
    }

    private fun completeOccurrence(occurrence: EventOccurrence, actualExecutionDateTime: LocalDateTime?) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = completeOccurrenceActionUseCase(occurrence, actualExecutionDateTime)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to complete occurrence") }
            } else {
                _uiState.value.event?.let { populateEventDetails(it) }
            }
        }
    }

    private fun skipOccurrence(occurrence: EventOccurrence) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = skipOccurrenceActionUseCase(occurrence)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to skip occurrence") }
            } else {
                _uiState.value.event?.let { populateEventDetails(it) }
            }
        }
    }

    private fun rescheduleOccurrence(
        occurrence: EventOccurrence,
        newStartDateTime: LocalDateTime,
        newEndDateTime: LocalDateTime?
    ) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = rescheduleOccurrenceActionUseCase?.invoke(
                occurrence = occurrence,
                newStartDateTime = newStartDateTime,
                newEndDateTime = newEndDateTime
            )
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to reschedule occurrence") }
            } else {
                _uiState.value.event?.let { populateEventDetails(it) }
            }
        }
    }
}
