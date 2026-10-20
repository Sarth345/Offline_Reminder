package com.example.offlinereminder.presentation.eventeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * ViewModel managing state, validation, catalogs, and saving for Event creation and editing.
 */
class EventEditorViewModel(
    private val getEventByIdUseCase: GetEventByIdUseCase,
    private val createEventUseCase: CreateEventUseCase,
    private val updateEventUseCase: UpdateEventUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val getEventGroupsUseCase: GetEventGroupsUseCase,
    private val getTagsUseCase: GetTagsUseCase,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventEditorUiState(isLoading = true))
    val uiState: StateFlow<EventEditorUiState> = _uiState.asStateFlow()

    init {
        loadCatalogs()
    }

    fun onAction(action: EventEditorAction) {
        when (action) {
            is EventEditorAction.InitWithEvent -> initWithEvent(action.eventId)
            is EventEditorAction.InitWithDefaults -> initWithDefaults(action.initialDate, action.initialEventTypeId)
            is EventEditorAction.UpdateTitle -> _uiState.update { it.copy(title = action.title, validationError = null) }
            is EventEditorAction.UpdateNotes -> _uiState.update { it.copy(notes = action.notes) }
            is EventEditorAction.UpdatePriority -> _uiState.update { it.copy(priority = action.priority) }
            is EventEditorAction.SelectCategory -> _uiState.update { it.copy(categoryId = action.categoryId, validationError = null) }
            is EventEditorAction.SelectEventType -> _uiState.update { it.copy(eventTypeId = action.eventTypeId, validationError = null) }
            is EventEditorAction.SearchEventType -> searchEventType(action.query)
            is EventEditorAction.UpdateTemporal -> _uiState.update { it.copy(temporal = action.temporal, validationError = null) }
            is EventEditorAction.UpdateRecurrence -> _uiState.update { it.copy(recurrenceRule = action.rule) }
            is EventEditorAction.AddNotificationSchedule -> addNotificationSchedule(action.schedule)
            is EventEditorAction.RemoveNotificationSchedule -> removeNotificationSchedule(action.scheduleId)
            is EventEditorAction.UpdateChecklist -> _uiState.update { it.copy(checklist = action.checklist) }
            is EventEditorAction.ToggleGroup -> toggleGroup(action.groupId)
            is EventEditorAction.ToggleTag -> toggleTag(action.tagId)
            is EventEditorAction.AddAttachment -> addAttachment(action.attachment)
            is EventEditorAction.RemoveAttachment -> removeAttachment(action.attachmentId)
            is EventEditorAction.SaveEvent -> saveEvent()
            is EventEditorAction.ClearError -> _uiState.update { it.copy(errorMessage = null, validationError = null) }
        }
    }

    private fun loadCatalogs() {
        viewModelScope.launch(dispatcherProvider.main) {
            combine(
                getCategoriesUseCase(),
                getEventTypesUseCase(),
                getEventGroupsUseCase(),
                getTagsUseCase()
            ) { categories, types, groups, tags ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        availableCategories = categories,
                        availableEventTypes = types,
                        filteredEventTypes = types,
                        availableGroups = groups,
                        availableTags = tags,
                        // Set default category / type if unset and available
                        categoryId = if (it.categoryId.isBlank() && categories.isNotEmpty()) categories.first().id else it.categoryId,
                        eventTypeId = if (it.eventTypeId.isBlank() && types.isNotEmpty()) types.first().id else it.eventTypeId
                    )
                }
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load catalogs"
                    )
                }
            }.collect {}
        }
    }

    private fun initWithEvent(eventId: String?) {
        if (eventId == null) {
            // New event with default temporal today
            val defaultTemporal = EventDateTime.DateOnly(timeProvider.nowLocalDate())
            _uiState.update {
                it.copy(
                    isEditing = false,
                    eventId = null,
                    temporal = it.temporal ?: defaultTemporal
                )
            }
            return
        }

        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true, isEditing = true, eventId = eventId) }
            val event = getEventByIdUseCase.getOnce(eventId)
            if (event != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditing = true,
                        eventId = event.id,
                        title = event.title,
                        notes = event.description.ifEmpty { null },
                        priority = event.priority,
                        categoryId = event.categoryId,
                        eventTypeId = event.eventTypeId,
                        temporal = event.temporal,
                        recurrenceRule = event.recurrenceRule,
                        notificationSchedules = event.notificationSchedules,
                        checklist = event.checklist,
                        groupIds = event.groupIds,
                        tagIds = event.tagIds,
                        attachments = event.attachments,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Event not found with ID: $eventId")
                }
            }
        }
    }

    private fun initWithDefaults(initialDate: LocalDate?, initialEventTypeId: String?) {
        _uiState.update {
            it.copy(
                temporal = if (initialDate != null) EventDateTime.DateOnly(initialDate) else it.temporal,
                eventTypeId = initialEventTypeId ?: it.eventTypeId
            )
        }
    }

    private fun searchEventType(query: String) {
        val filtered = if (query.isBlank()) {
            _uiState.value.availableEventTypes
        } else {
            _uiState.value.availableEventTypes.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.key.contains(query, ignoreCase = true) ||
                    it.aliases.any { alias -> alias.contains(query, ignoreCase = true) }
            }
        }
        _uiState.update {
            it.copy(
                eventTypeSearchQuery = query,
                filteredEventTypes = filtered
            )
        }
    }

    private fun addNotificationSchedule(schedule: NotificationSchedule) {
        _uiState.update {
            it.copy(notificationSchedules = it.notificationSchedules + schedule)
        }
    }

    private fun removeNotificationSchedule(scheduleId: String) {
        _uiState.update {
            it.copy(notificationSchedules = it.notificationSchedules.filter { s -> s.id != scheduleId })
        }
    }

    private fun toggleGroup(groupId: String) {
        _uiState.update {
            val current = it.groupIds
            val updated = if (current.contains(groupId)) current - groupId else current + groupId
            it.copy(groupIds = updated)
        }
    }

    private fun toggleTag(tagId: String) {
        _uiState.update {
            val current = it.tagIds
            val updated = if (current.contains(tagId)) current - tagId else current + tagId
            it.copy(tagIds = updated)
        }
    }

    private fun addAttachment(attachment: AttachmentMetadata) {
        _uiState.update {
            it.copy(attachments = it.attachments + attachment)
        }
    }

    private fun removeAttachment(attachmentId: String) {
        _uiState.update {
            it.copy(attachments = it.attachments.filter { a -> a.id != attachmentId })
        }
    }

    private fun saveEvent() {
        val state = _uiState.value
        val title = state.title.trim()
        if (title.isEmpty()) {
            _uiState.update { it.copy(validationError = "Event title must not be blank") }
            return
        }
        if (state.categoryId.isBlank()) {
            _uiState.update { it.copy(validationError = "Please select a category") }
            return
        }
        if (state.eventTypeId.isBlank()) {
            _uiState.update { it.copy(validationError = "Please select an event type") }
            return
        }
        val temporal = state.temporal ?: EventDateTime.DateOnly(timeProvider.nowLocalDate())

        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, validationError = null) }

            val event = Event(
                id = state.eventId ?: UUID.randomUUID().toString(),
                title = title,
                description = state.notes?.trim() ?: "",
                priority = state.priority,
                categoryId = state.categoryId,
                eventTypeId = state.eventTypeId,
                lifecycleStatus = EventLifecycleStatus.ACTIVE,
                isFavorite = false,
                temporal = temporal,
                recurrenceRule = state.recurrenceRule,
                notificationSchedules = state.notificationSchedules,
                checklist = state.checklist,
                groupIds = state.groupIds,
                tagIds = state.tagIds,
                attachments = state.attachments,
                createdAt = timeProvider.nowInstant(),
                updatedAt = timeProvider.nowInstant()
            )

            val result = if (state.isEditing) {
                updateEventUseCase(event)
            } else {
                createEventUseCase(event)
            }

            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.error.message ?: "Failed to save event"
                        )
                    }
                }
            }
        }
    }
}
