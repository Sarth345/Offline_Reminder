package com.example.offlinereminder.presentation.eventdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.DependencyType
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.OccurrenceStatus
import com.example.offlinereminder.domain.model.resolveEventDisplayStatus
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.RelationshipType
import com.example.offlinereminder.domain.recurrence.DefaultRecurrenceEngine
import com.example.offlinereminder.domain.recurrence.RecurrenceEngine
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.OccurrenceExceptionRepository
import com.example.offlinereminder.domain.usecase.AddEventDependencyUseCase
import com.example.offlinereminder.domain.usecase.AddEventRelationshipUseCase
import com.example.offlinereminder.domain.usecase.ArchiveEventUseCase
import com.example.offlinereminder.domain.usecase.CancelNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.CreateEventTemplateUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventUseCase
import com.example.offlinereminder.domain.usecase.DetectEventConflictsUseCase
import com.example.offlinereminder.domain.usecase.DuplicateEventUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetCustomSoundsUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventDependenciesUseCase
import com.example.offlinereminder.domain.usecase.GetEventDependentsUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventRelationshipsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetNotificationProfilesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.PermanentlyDeleteEventUseCase
import com.example.offlinereminder.domain.usecase.RemoveEventDependencyUseCase
import com.example.offlinereminder.domain.usecase.RemoveEventRelationshipUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.RestoreArchivedEventUseCase
import com.example.offlinereminder.domain.usecase.RestoreTrashedEventUseCase
import com.example.offlinereminder.domain.usecase.ScheduleNotificationsForEventUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.TrashEventUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID

/**
 * ViewModel managing immutable state and user actions for the Event Detail screen.
 * Implements complete event management, sub-events, checklists, attachments,
 * dependencies, relationships, templates, and lifecycle safety.
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
    private val trashEventUseCase: TrashEventUseCase? = null,
    private val restoreTrashedEventUseCase: RestoreTrashedEventUseCase? = null,
    private val archiveEventUseCase: ArchiveEventUseCase? = null,
    private val restoreArchivedEventUseCase: RestoreArchivedEventUseCase? = null,
    private val permanentlyDeleteEventUseCase: PermanentlyDeleteEventUseCase? = null,
    private val duplicateEventUseCase: DuplicateEventUseCase? = null,
    private val createEventTemplateUseCase: CreateEventTemplateUseCase? = null,
    private val addEventDependencyUseCase: AddEventDependencyUseCase? = null,
    private val removeEventDependencyUseCase: RemoveEventDependencyUseCase? = null,
    private val getEventDependenciesUseCase: GetEventDependenciesUseCase? = null,
    private val getEventDependentsUseCase: GetEventDependentsUseCase? = null,
    private val addEventRelationshipUseCase: AddEventRelationshipUseCase? = null,
    private val removeEventRelationshipUseCase: RemoveEventRelationshipUseCase? = null,
    private val getEventRelationshipsUseCase: GetEventRelationshipsUseCase? = null,
    private val detectEventConflictsUseCase: DetectEventConflictsUseCase? = null,
    private val getNotificationProfilesUseCase: GetNotificationProfilesUseCase? = null,
    private val getCustomSoundsUseCase: GetCustomSoundsUseCase? = null,
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
            is EventDetailAction.ArchiveEvent -> archiveEvent()
            is EventDetailAction.RestoreFromArchive -> restoreFromArchive()
            is EventDetailAction.DeletePermanently -> deletePermanently()
            is EventDetailAction.DuplicateEvent -> duplicateEvent()
            is EventDetailAction.CreateTemplateFromEvent -> createTemplate(action.templateName)
            is EventDetailAction.ToggleChecklistItem -> toggleChecklistItem(action.itemId)
            is EventDetailAction.AddChecklistItem -> addChecklistItem(action.text)
            is EventDetailAction.DeleteChecklistItem -> deleteChecklistItem(action.itemId)
            is EventDetailAction.AddDependency -> addDependency(action.targetEventId, action.type)
            is EventDetailAction.RemoveDependency -> removeDependency(action.targetEventId)
            is EventDetailAction.AddRelationship -> addRelationship(action.targetEventId, action.type)
            is EventDetailAction.RemoveRelationship -> removeRelationship(action.targetEventId)
            is EventDetailAction.AddReminder -> addReminder(action.schedule)
            is EventDetailAction.RemoveReminder -> removeReminder(action.scheduleId)
            is EventDetailAction.AddAttachment -> addAttachment(action.attachment)
            is EventDetailAction.RemoveAttachment -> removeAttachment(action.attachmentId)
            is EventDetailAction.CompleteOccurrence -> completeOccurrence(action.occurrence, action.actualExecutionDateTime)
            is EventDetailAction.SkipOccurrence -> skipOccurrence(action.occurrence)
            is EventDetailAction.RescheduleOccurrence -> rescheduleOccurrence(action.occurrence, action.newStartDateTime, action.newEndDateTime)
            is EventDetailAction.UndoOccurrenceAction -> undoOccurrenceAction(action.occurrenceId)
            is EventDetailAction.ClearDuplicatedEventId -> _uiState.update { it.copy(duplicatedEventId = null) }
            is EventDetailAction.ClearMessage -> _uiState.update { it.copy(actionMessage = null) }
            is EventDetailAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun loadEvent(eventId: String) {
        currentEventId = eventId
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            kotlinx.coroutines.flow.combine(
                getEventByIdUseCase(eventId),
                occurrenceExceptionRepository.getExceptionsForEvent(eventId)
            ) { event, exceptions ->
                event to exceptions
            }.catch { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load event")
                }
            }.collect { (event, exceptions) ->
                if (event == null) {
                    _uiState.update { it.copy(isLoading = false, event = null, errorMessage = "Event not found") }
                    return@collect
                }
                populateEventDetails(event, exceptions)
            }
        }
    }

    private suspend fun populateEventDetails(event: Event, exceptions: List<com.example.offlinereminder.domain.model.OccurrenceException> = emptyList()) {
        val category = getCategoriesUseCase.getOnce(event.categoryId)
        val eventType = getEventTypesUseCase.getOnce(event.eventTypeId)
        val allGroups = getEventGroupsUseCase().firstOrNull() ?: emptyList()
        val eventGroups = allGroups.filter { event.groupIds.contains(it.id) }
        val allTags = getTagsUseCase().firstOrNull() ?: emptyList()
        val eventTags = allTags.filter { event.tagIds.contains(it.id) }

        // Load Parent Event if applicable
        val parent = event.parentEventId?.let { parentId ->
            eventRepository.getEvent(parentId)
        }

        // Load Sub-events
        val subEvents = runCatching {
            eventRepository.getSubEvents(event.id).first()
        }.getOrDefault(emptyList())

        // Load Dependencies and Dependents
        val deps = getEventDependenciesUseCase?.invoke(event.id)?.firstOrNull() ?: emptyList()
        val dependents = getEventDependentsUseCase?.invoke(event.id)?.firstOrNull() ?: emptyList()

        // Load Relationships
        val rels = getEventRelationshipsUseCase?.invoke(event.id)?.firstOrNull() ?: emptyList()

        // Resolve names for linked events (prerequisites, dependents, relationships)
        val linkedIds = (deps.map { it.targetEventId } + dependents.map { it.sourceEventId } +
            rels.map { if (it.sourceEventId == event.id) it.targetEventId else it.sourceEventId }).toSet()
        val resolvedMap = mutableMapOf<String, Event>()
        for (id in linkedIds) {
            eventRepository.getEvent(id)?.let { resolvedMap[id] = it }
        }

        // Detect conflicts for active events
        val conflictDetector = detectEventConflictsUseCase
        val conflicts = if (event.isActive && conflictDetector != null) {
            runCatching { conflictDetector.invoke(event) }.getOrDefault(emptyList())
        } else emptyList()

        // Load notification profiles and custom sounds for labels
        val profiles = getNotificationProfilesUseCase?.invoke()?.firstOrNull() ?: emptyList()
        val profileMap = profiles.associateBy { it.id }
        val sounds = getCustomSoundsUseCase?.invoke()?.firstOrNull() ?: emptyList()
        val soundMap = sounds.associateBy { it.id }

        // Available events for creating dependencies/relationships
        val availableEvents = runCatching {
            eventRepository.getAllActiveEvents().first().filter { it.id != event.id }
        }.getOrDefault(emptyList())

        // Project occurrences within bounded horizon (including past lookback for overdue/completed)
        val nowLdt = timeProvider.nowLocalDateTime()
        val horizonStart = if (event.recurrenceRule != null) nowLdt.minusDays(14) else event.temporal.startLocalDateTime().minusDays(1)
        val horizonEnd = nowLdt.plusDays(30)

        val projectedOccurrences = if (!event.isTrashed) {
            val eventExceptions = exceptions.ifEmpty {
                occurrenceExceptionRepository.getExceptionsInRange(horizonStart, horizonEnd)
                    .filter { it.eventId == event.id }
            }

            recurrenceEngine.projectOccurrences(
                event = event,
                from = horizonStart,
                to = horizonEnd,
                exceptions = eventExceptions,
                evaluateOverdue = true,
                timeProvider = timeProvider
            )
        } else emptyList()

        val primaryOcc = if (event.recurrenceRule == null) {
            projectedOccurrences.firstOrNull() ?: event.createInitialOccurrence().let { nominal ->
                val exc = exceptions.find { it.occurrenceId == nominal.occurrenceId }
                if (exc != null) {
                    nominal.copy(
                        status = exc.status,
                        completedAt = exc.completedAt,
                        rescheduledStartDateTime = exc.rescheduledStartDateTime,
                        rescheduledEndDateTime = exc.rescheduledEndDateTime,
                        rescheduledZoneId = exc.rescheduledZoneId,
                        actualExecutionDateTime = exc.actualExecutionDateTime
                    )
                } else if (nominal.effectiveStartDateTime.isBefore(nowLdt)) {
                    nominal.copy(status = OccurrenceStatus.OVERDUE)
                } else {
                    nominal
                }
            }
        } else {
            projectedOccurrences.find { it.status == OccurrenceStatus.OVERDUE }
                ?: projectedOccurrences.find { it.effectiveStartDateTime.toLocalDate() == nowLdt.toLocalDate() }
                ?: projectedOccurrences.find { it.status == OccurrenceStatus.PENDING }
                ?: projectedOccurrences.firstOrNull()
        }

        val displayStatus = resolveEventDisplayStatus(
            event = event,
            occurrence = primaryOcc,
            nowLdt = nowLdt
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
                primaryOccurrence = primaryOcc,
                displayStatus = displayStatus,
                parentEvent = parent,
                subEvents = subEvents,
                dependencies = deps,
                dependents = dependents,
                relationships = rels,
                resolvedLinkedEvents = resolvedMap,
                conflicts = conflicts,
                notificationProfiles = profileMap,
                customSounds = soundMap,
                availableEventsForLinking = availableEvents,
                availableNotificationProfiles = profiles,
                availableCustomSounds = sounds,
                isFavorite = event.isFavorite,
                errorMessage = null
            )
        }
    }

    private fun undoOccurrenceAction(occurrenceId: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            val result = occurrenceExceptionRepository.deleteByOccurrenceId(occurrenceId)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to undo occurrence action") }
            }
        }
    }

    private fun checkActiveLifecycle(operationName: String): Boolean {
        val event = _uiState.value.event
        if (event == null) return false
        if (event.isTrashed) {
            _uiState.update { it.copy(errorMessage = "Cannot $operationName a trashed event. Restore it first.") }
            return false
        }
        return true
    }

    private fun toggleFavorite() {
        if (!checkActiveLifecycle("favorite")) return
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
        val event = _uiState.value.event ?: return
        if (event.isTrashed) return
        viewModelScope.launch(dispatcherProvider.main) {
            cancelNotificationsForEventUseCase?.invoke(event.id)
            val result = if (trashEventUseCase != null) {
                trashEventUseCase.invoke(event.id)
            } else {
                eventRepository.moveToTrash(event.id)
            }
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to move event to trash") }
            } else {
                _uiState.update { it.copy(actionMessage = "Moved to Trash") }
            }
        }
    }

    private fun restoreFromTrash() {
        val event = _uiState.value.event ?: return
        if (!event.isTrashed) return
        viewModelScope.launch(dispatcherProvider.main) {
            val result = if (restoreTrashedEventUseCase != null) {
                restoreTrashedEventUseCase.invoke(event.id)
            } else {
                eventRepository.restoreFromTrash(event.id)
            }
            if (result is Result.Success) {
                val restored = eventRepository.getEvent(event.id)
                if (restored != null && scheduleNotificationsForEventUseCase != null) {
                    scheduleNotificationsForEventUseCase.invoke(restored)
                }
                _uiState.update { it.copy(actionMessage = "Event restored") }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to restore event") }
            }
        }
    }

    private fun archiveEvent() {
        if (!checkActiveLifecycle("archive")) return
        val event = _uiState.value.event ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            cancelNotificationsForEventUseCase?.invoke(event.id)
            val result = if (archiveEventUseCase != null) {
                archiveEventUseCase.invoke(event.id)
            } else {
                eventRepository.archiveEvent(event.id)
            }
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to archive event") }
            } else {
                _uiState.update { it.copy(actionMessage = "Event archived") }
            }
        }
    }

    private fun restoreFromArchive() {
        val event = _uiState.value.event ?: return
        if (!event.isArchived) return
        viewModelScope.launch(dispatcherProvider.main) {
            val result = if (restoreArchivedEventUseCase != null) {
                restoreArchivedEventUseCase.invoke(event.id)
            } else {
                eventRepository.restoreFromArchive(event.id)
            }
            if (result is Result.Success) {
                val restored = eventRepository.getEvent(event.id)
                if (restored != null && scheduleNotificationsForEventUseCase != null) {
                    scheduleNotificationsForEventUseCase.invoke(restored)
                }
                _uiState.update { it.copy(actionMessage = "Event restored from archive") }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to restore event from archive") }
            }
        }
    }

    private fun deletePermanently() {
        val eventId = currentEventId ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            cancelNotificationsForEventUseCase?.invoke(eventId)
            val result = if (permanentlyDeleteEventUseCase != null) {
                permanentlyDeleteEventUseCase.invoke(eventId)
            } else if (deleteEventUseCase != null) {
                deleteEventUseCase.invoke(eventId)
            } else {
                eventRepository.deleteEvent(eventId)
            }
            if (result is Result.Success) {
                _uiState.update { it.copy(isDeleted = true) }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to permanently delete event") }
            }
        }
    }

    private fun duplicateEvent() {
        if (!checkActiveLifecycle("duplicate")) return
        val eventId = currentEventId ?: return
        val duplicateUseCase = duplicateEventUseCase ?: run {
            _uiState.update { it.copy(errorMessage = "Duplicate service not configured") }
            return
        }
        viewModelScope.launch(dispatcherProvider.main) {
            val result = duplicateUseCase(eventId)
            when (result) {
                is Result.Success -> {
                    val newEvent = result.data
                    scheduleNotificationsForEventUseCase?.invoke(newEvent)
                    _uiState.update {
                        it.copy(
                            duplicatedEventId = newEvent.id,
                            actionMessage = "Event duplicated as '${newEvent.title}'"
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to duplicate event") }
                }
            }
        }
    }

    private fun createTemplate(templateName: String) {
        if (!checkActiveLifecycle("create template from")) return
        val event = _uiState.value.event ?: return
        val templateUseCase = createEventTemplateUseCase ?: run {
            _uiState.update { it.copy(errorMessage = "Template service not configured") }
            return
        }
        viewModelScope.launch(dispatcherProvider.main) {
            val template = EventTemplate(
                id = UUID.randomUUID().toString(),
                name = templateName.ifBlank { "${event.title} Template" },
                description = event.description,
                eventTypeId = event.eventTypeId,
                categoryId = event.categoryId,
                priority = event.priority,
                isAllDay = event.temporal.isAllDay,
                defaultDuration = java.time.Duration.ZERO,
                location = event.location,
                recurrenceRule = event.recurrenceRule,
                notificationSchedules = event.notificationSchedules,
                checklist = event.checklist,
                tagIds = event.tagIds,
                createdAt = timeProvider.nowInstant(),
                updatedAt = timeProvider.nowInstant()
            )
            val result = templateUseCase(template)
            if (result is Result.Success) {
                _uiState.update { it.copy(actionMessage = "Template '${template.name}' created") }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to create template") }
            }
        }
    }

    private fun toggleChecklistItem(itemId: String) {
        if (!checkActiveLifecycle("update checklist of")) return
        val event = _uiState.value.event ?: return
        val currentChecklist = event.checklist ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val updatedItems = currentChecklist.items.map { item ->
                if (item.id == itemId) item.copy(isCompleted = !item.isCompleted) else item
            }
            val updatedChecklist = currentChecklist.copy(items = updatedItems)
            val updatedEvent = event.copy(checklist = updatedChecklist, updatedAt = timeProvider.nowInstant())
            val result = eventRepository.saveEvent(updatedEvent)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to update checklist item") }
            } else {
                populateEventDetails(updatedEvent)
            }
        }
    }

    private fun addChecklistItem(text: String) {
        if (!checkActiveLifecycle("add checklist item to")) return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val event = _uiState.value.event ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val currentChecklist = event.checklist ?: Checklist(
                id = UUID.randomUUID().toString(),
                title = "Checklist",
                items = emptyList()
            )
            val newItem = ChecklistItem(
                id = UUID.randomUUID().toString(),
                text = trimmed,
                isCompleted = false,
                sortOrder = currentChecklist.items.size
            )
            val updatedChecklist = currentChecklist.copy(items = currentChecklist.items + newItem)
            val updatedEvent = event.copy(checklist = updatedChecklist, updatedAt = timeProvider.nowInstant())
            val result = eventRepository.saveEvent(updatedEvent)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to add checklist item") }
            } else {
                populateEventDetails(updatedEvent)
            }
        }
    }

    private fun deleteChecklistItem(itemId: String) {
        if (!checkActiveLifecycle("delete checklist item from")) return
        val event = _uiState.value.event ?: return
        val currentChecklist = event.checklist ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val updatedItems = currentChecklist.items.filter { it.id != itemId }
            val updatedChecklist = currentChecklist.copy(items = updatedItems)
            val updatedEvent = event.copy(checklist = updatedChecklist, updatedAt = timeProvider.nowInstant())
            val result = eventRepository.saveEvent(updatedEvent)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to delete checklist item") }
            } else {
                populateEventDetails(updatedEvent)
            }
        }
    }

    private fun addDependency(targetEventId: String, type: DependencyType) {
        if (!checkActiveLifecycle("add dependency to")) return
        val eventId = currentEventId ?: return
        val addUseCase = addEventDependencyUseCase ?: run {
            _uiState.update { it.copy(errorMessage = "Dependency service not configured") }
            return
        }
        viewModelScope.launch(dispatcherProvider.main) {
            val result = addUseCase(sourceEventId = eventId, targetEventId = targetEventId, type = type)
            if (result is Result.Success) {
                _uiState.value.event?.let { populateEventDetails(it) }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to add dependency") }
            }
        }
    }

    private fun removeDependency(targetEventId: String) {
        if (!checkActiveLifecycle("remove dependency from")) return
        val eventId = currentEventId ?: return
        val removeUseCase = removeEventDependencyUseCase ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val result = removeUseCase(sourceEventId = eventId, targetEventId = targetEventId)
            if (result is Result.Success) {
                _uiState.value.event?.let { populateEventDetails(it) }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to remove dependency") }
            }
        }
    }

    private fun addRelationship(targetEventId: String, type: RelationshipType) {
        if (!checkActiveLifecycle("add relationship to")) return
        val eventId = currentEventId ?: return
        val addUseCase = addEventRelationshipUseCase ?: run {
            _uiState.update { it.copy(errorMessage = "Relationship service not configured") }
            return
        }
        viewModelScope.launch(dispatcherProvider.main) {
            val result = addUseCase(sourceEventId = eventId, targetEventId = targetEventId, type = type)
            if (result is Result.Success) {
                _uiState.value.event?.let { populateEventDetails(it) }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to add relationship") }
            }
        }
    }

    private fun removeRelationship(targetEventId: String) {
        if (!checkActiveLifecycle("remove relationship from")) return
        val eventId = currentEventId ?: return
        val removeUseCase = removeEventRelationshipUseCase ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val result = removeUseCase(sourceEventId = eventId, targetEventId = targetEventId)
            if (result is Result.Success) {
                _uiState.value.event?.let { populateEventDetails(it) }
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to remove relationship") }
            }
        }
    }

    private fun addReminder(schedule: NotificationSchedule) {
        if (!checkActiveLifecycle("add reminder to")) return
        val event = _uiState.value.event ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val updatedSchedules = event.notificationSchedules + schedule
            val updatedEvent = event.copy(notificationSchedules = updatedSchedules, updatedAt = timeProvider.nowInstant())
            val result = eventRepository.saveEvent(updatedEvent)
            if (result is Result.Success) {
                scheduleNotificationsForEventUseCase?.invoke(updatedEvent)
                populateEventDetails(updatedEvent)
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to add reminder") }
            }
        }
    }

    private fun removeReminder(scheduleId: String) {
        if (!checkActiveLifecycle("remove reminder from")) return
        val event = _uiState.value.event ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val updatedSchedules = event.notificationSchedules.filter { it.id != scheduleId }
            val updatedEvent = event.copy(notificationSchedules = updatedSchedules, updatedAt = timeProvider.nowInstant())
            val result = eventRepository.saveEvent(updatedEvent)
            if (result is Result.Success) {
                cancelNotificationsForEventUseCase?.invoke(event.id)
                scheduleNotificationsForEventUseCase?.invoke(updatedEvent)
                populateEventDetails(updatedEvent)
            } else if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to remove reminder") }
            }
        }
    }

    private fun addAttachment(attachment: AttachmentMetadata) {
        if (!checkActiveLifecycle("add attachment to")) return
        val event = _uiState.value.event ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val updatedAttachments = event.attachments + attachment
            val updatedEvent = event.copy(attachments = updatedAttachments, updatedAt = timeProvider.nowInstant())
            val result = eventRepository.saveEvent(updatedEvent)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to save attachment") }
            } else {
                populateEventDetails(updatedEvent)
            }
        }
    }

    private fun removeAttachment(attachmentId: String) {
        if (!checkActiveLifecycle("remove attachment from")) return
        val event = _uiState.value.event ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val updatedAttachments = event.attachments.filter { it.id != attachmentId }
            val updatedEvent = event.copy(attachments = updatedAttachments, updatedAt = timeProvider.nowInstant())
            val result = eventRepository.saveEvent(updatedEvent)
            if (result is Result.Failure) {
                _uiState.update { it.copy(errorMessage = result.error.message ?: "Failed to remove attachment") }
            } else {
                populateEventDetails(updatedEvent)
            }
        }
    }

    private fun completeOccurrence(occurrence: EventOccurrence, actualExecutionDateTime: LocalDateTime?) {
        if (!checkActiveLifecycle("complete")) return
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
        if (!checkActiveLifecycle("skip")) return
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
        if (!checkActiveLifecycle("reschedule")) return
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
