package com.example.offlinereminder.presentation.eventeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.model.AttachmentMetadata
import com.example.offlinereminder.domain.model.Checklist
import com.example.offlinereminder.domain.model.ChecklistItem
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventLifecycleStatus
import com.example.offlinereminder.domain.model.LocationData
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.Priority
import com.example.offlinereminder.domain.model.RecurrenceRule
import com.example.offlinereminder.domain.repository.EventRepository
import com.example.offlinereminder.domain.repository.SettingsRepository
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.DetectEventConflictsUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetCustomSoundsUseCase
import com.example.offlinereminder.domain.usecase.GetEventByIdUseCase
import com.example.offlinereminder.domain.usecase.GetEventGroupsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTemplateUseCase
import com.example.offlinereminder.domain.usecase.GetEventTemplatesUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetNotificationProfilesUseCase
import com.example.offlinereminder.domain.usecase.GetTagsUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * ViewModel managing state, validation, catalogs, templates, conflict detection,
 * and saving for Event creation and advanced editing.
 */
class EventEditorViewModel(
    private val getEventByIdUseCase: GetEventByIdUseCase,
    private val createEventUseCase: CreateEventUseCase,
    private val updateEventUseCase: UpdateEventUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val getEventGroupsUseCase: GetEventGroupsUseCase,
    private val getTagsUseCase: GetTagsUseCase,
    private val eventRepository: EventRepository? = null,
    private val getEventTemplatesUseCase: GetEventTemplatesUseCase? = null,
    private val getEventTemplateUseCase: GetEventTemplateUseCase? = null,
    private val getNotificationProfilesUseCase: GetNotificationProfilesUseCase? = null,
    private val getCustomSoundsUseCase: GetCustomSoundsUseCase? = null,
    private val detectEventConflictsUseCase: DetectEventConflictsUseCase? = null,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val settingsRepository: SettingsRepository? = null,
    private val editRecurringSeriesUseCase: com.example.offlinereminder.domain.usecase.EditRecurringSeriesUseCase? = null,
    private val getSavedLocationsUseCase: com.example.offlinereminder.domain.usecase.GetSavedLocationsUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventEditorUiState(isLoading = true))
    val uiState: StateFlow<EventEditorUiState> = _uiState.asStateFlow()

    private var originalEvent: Event? = null

    init {
        loadCatalogs()
    }

    fun onAction(action: EventEditorAction) {
        when (action) {
            is EventEditorAction.InitWithEvent -> initWithEvent(action.eventId)
            is EventEditorAction.InitWithDefaults -> initWithDefaults(action.initialDate, action.initialEventTypeId, action.parentEventId)
            is EventEditorAction.ApplyTemplate -> applyTemplate(action.templateId)
            is EventEditorAction.SetParentEventId -> setParentEventId(action.parentId)
            is EventEditorAction.UpdateTitle -> _uiState.update { it.copy(title = action.title, validationError = null) }
            is EventEditorAction.UpdateNotes -> _uiState.update { it.copy(notes = action.notes) }
            is EventEditorAction.UpdateLocation -> _uiState.update { it.copy(locationDisplayName = action.locationName) }
            is EventEditorAction.UpdatePriority -> _uiState.update { it.copy(priority = action.priority) }
            is EventEditorAction.SelectCategory -> _uiState.update { it.copy(categoryId = action.categoryId, validationError = null) }
            is EventEditorAction.SelectEventType -> _uiState.update { it.copy(eventTypeId = action.eventTypeId, validationError = null) }
            is EventEditorAction.SearchEventType -> searchEventType(action.query)
            is EventEditorAction.SetDateMode -> setDateMode(action.mode)
            is EventEditorAction.UpdateTimezone -> updateTimezone(action.zoneId)
            is EventEditorAction.UpdateTemporal -> updateTemporal(action.temporal)
            is EventEditorAction.UpdateRecurrence -> _uiState.update { it.copy(recurrenceRule = action.rule) }
            is EventEditorAction.AddNotificationSchedule -> addNotificationSchedule(action.schedule)
            is EventEditorAction.RemoveNotificationSchedule -> removeNotificationSchedule(action.scheduleId)
            is EventEditorAction.UpdateChecklist -> _uiState.update { it.copy(checklist = action.checklist) }
            is EventEditorAction.AddChecklistItem -> addChecklistItem(action.title)
            is EventEditorAction.RemoveChecklistItem -> removeChecklistItem(action.itemId)
            is EventEditorAction.ToggleChecklistItem -> toggleChecklistItem(action.itemId)
            is EventEditorAction.ToggleGroup -> toggleGroup(action.groupId)
            is EventEditorAction.ToggleTag -> toggleTag(action.tagId)
            is EventEditorAction.AddAttachment -> addAttachment(action.attachment)
            is EventEditorAction.RemoveAttachment -> removeAttachment(action.attachmentId)
            is EventEditorAction.SelectSavedLocation -> _uiState.update { it.copy(locationDisplayName = action.savedLocation.name) }
            is EventEditorAction.SaveRecurringSeries -> saveRecurringSeries(action.mode)
            is EventEditorAction.DismissRecurringSeriesDialog -> _uiState.update { it.copy(showRecurringSeriesDialog = false, pendingEditedDraft = null) }
            is EventEditorAction.CheckConflicts -> checkConflicts()
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
                val templates = getEventTemplatesUseCase?.invoke()?.firstOrNull() ?: emptyList()
                val profiles = getNotificationProfilesUseCase?.invoke()?.firstOrNull() ?: emptyList()
                val sounds = getCustomSoundsUseCase?.invoke()?.firstOrNull() ?: emptyList()
                val savedLocations = getSavedLocationsUseCase?.invoke()?.firstOrNull() ?: emptyList()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        availableCategories = categories,
                        availableEventTypes = types,
                        filteredEventTypes = types,
                        availableGroups = groups,
                        availableTags = tags,
                        availableTemplates = templates,
                        availableNotificationProfiles = profiles,
                        availableCustomSounds = sounds,
                        availableSavedLocations = savedLocations,
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
            initWithDefaults(null, null, null)
            return
        }

        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true, isEditing = true, eventId = eventId) }
            val event = getEventByIdUseCase.getOnce(eventId)
            if (event != null) {
                originalEvent = event
                val parentTitle = event.parentEventId?.let { parentId ->
                    eventRepository?.getEvent(parentId)?.title
                }
                val dateMode = when (event.temporal) {
                    is EventDateTime.DateOnly -> EventDateMode.DATE_ONLY
                    is EventDateTime.DateRange -> EventDateMode.DATE_RANGE
                    is EventDateTime.DateTime -> EventDateMode.DATE_TIME
                    is EventDateTime.DateTimeRange -> EventDateMode.DATE_RANGE_TIME
                }
                val zoneId = when (val t = event.temporal) {
                    is EventDateTime.DateTime -> t.zoneId.id
                    is EventDateTime.DateTimeRange -> t.zoneId.id
                    else -> timeProvider.systemZoneId().id
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEditing = true,
                        eventId = event.id,
                        parentEventId = event.parentEventId,
                        parentEventTitle = parentTitle,
                        title = event.title,
                        notes = event.description.ifEmpty { null },
                        locationDisplayName = event.location?.placeName ?: "",
                        priority = event.priority,
                        categoryId = event.categoryId,
                        eventTypeId = event.eventTypeId,
                        temporal = event.temporal,
                        dateMode = dateMode,
                        timeZoneId = zoneId,
                        recurrenceRule = event.recurrenceRule,
                        notificationSchedules = event.notificationSchedules,
                        checklist = event.checklist,
                        groupIds = event.groupIds,
                        tagIds = event.tagIds,
                        attachments = event.attachments,
                        errorMessage = null
                    )
                }
                checkConflicts()
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Event not found with ID: $eventId")
                }
            }
        }
    }

    private fun initWithDefaults(initialDate: LocalDate?, initialEventTypeId: String?, parentEventId: String? = null) {
        viewModelScope.launch(dispatcherProvider.main) {
            val settings = settingsRepository?.getSettings() ?: AppSettings()
            val startDate = initialDate ?: timeProvider.nowLocalDate()
            val nowTime = timeProvider.nowLocalDateTime().toLocalTime().withSecond(0).withNano(0)
            val startDateTime = startDate.atTime(nowTime)
            val duration = settings.defaultEventDurationMinutes.toLong()
            val endDateTime = startDateTime.plusMinutes(duration)
            val defaultTemporal = EventDateTime.DateTimeRange(
                startDateTime = startDateTime,
                endDateTime = endDateTime,
                zoneId = timeProvider.systemZoneId()
            )

            val parentTitle = parentEventId?.let { pId ->
                eventRepository?.getEvent(pId)?.title
            }

            _uiState.update {
                it.copy(
                    isEditing = false,
                    eventId = null,
                    parentEventId = parentEventId,
                    parentEventTitle = parentTitle,
                    temporal = if (initialDate != null) defaultTemporal else (it.temporal ?: defaultTemporal),
                    priority = settings.defaultEventPriority,
                    eventTypeId = initialEventTypeId ?: it.eventTypeId,
                    timeZoneId = timeProvider.systemZoneId().id
                )
            }
            checkConflicts()
        }
    }

    private fun setParentEventId(parentId: String?) {
        viewModelScope.launch(dispatcherProvider.main) {
            val parentTitle = parentId?.let { id -> eventRepository?.getEvent(id)?.title }
            _uiState.update {
                it.copy(
                    parentEventId = parentId,
                    parentEventTitle = parentTitle
                )
            }
        }
    }

    private fun applyTemplate(templateId: String) {
        val getTemplateUseCase = getEventTemplateUseCase ?: return
        viewModelScope.launch(dispatcherProvider.main) {
            val template = getTemplateUseCase(templateId) ?: return@launch
            _uiState.update {
                it.copy(
                    title = template.name,
                    notes = template.description.ifEmpty { null },
                    categoryId = template.categoryId,
                    eventTypeId = template.eventTypeId,
                    priority = template.priority,
                    recurrenceRule = template.recurrenceRule,
                    notificationSchedules = template.notificationSchedules.map { s -> s.copy(id = UUID.randomUUID().toString()) },
                    checklist = template.checklist?.let { cl ->
                        Checklist(
                            id = UUID.randomUUID().toString(),
                            title = cl.title,
                            items = cl.items.map { item -> item.copy(id = UUID.randomUUID().toString(), isCompleted = false) }
                        )
                    },
                    tagIds = template.tagIds
                )
            }
            checkConflicts()
        }
    }

    private fun setDateMode(mode: EventDateMode) {
        val currentTemporal = _uiState.value.temporal ?: EventDateTime.DateOnly(timeProvider.nowLocalDate())
        val zone = runCatching { ZoneId.of(_uiState.value.timeZoneId) }.getOrDefault(timeProvider.systemZoneId())
        val startDate = currentTemporal.startDate
        val startTime = currentTemporal.startLocalDateTime().toLocalTime()

        val newTemporal: EventDateTime = when (mode) {
            EventDateMode.DATE_ONLY -> EventDateTime.DateOnly(startDate = startDate)
            EventDateMode.DATE_RANGE -> EventDateTime.DateRange(
                startDate = startDate,
                endDate = startDate.plusDays(1)
            )
            EventDateMode.DATE_TIME -> EventDateTime.DateTime(
                dateTime = LocalDateTime.of(startDate, startTime),
                zoneId = zone
            )
            EventDateMode.DATE_RANGE_TIME -> EventDateTime.DateTimeRange(
                startDateTime = LocalDateTime.of(startDate, startTime),
                endDateTime = LocalDateTime.of(startDate, startTime).plusHours(1),
                zoneId = zone
            )
        }
        _uiState.update {
            it.copy(
                dateMode = mode,
                temporal = newTemporal,
                validationError = null
            )
        }
        checkConflicts()
    }

    private fun updateTimezone(zoneId: String) {
        val validZone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return
        val current = _uiState.value.temporal
        val newTemporal = when (current) {
            is EventDateTime.DateTime -> current.copy(zoneId = validZone)
            is EventDateTime.DateTimeRange -> current.copy(zoneId = validZone)
            else -> current
        }
        _uiState.update {
            it.copy(
                timeZoneId = zoneId,
                temporal = newTemporal
            )
        }
        checkConflicts()
    }

    private fun updateTemporal(temporal: EventDateTime) {
        _uiState.update { it.copy(temporal = temporal, validationError = null) }
        checkConflicts()
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

    private fun addChecklistItem(title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        val currentChecklist = _uiState.value.checklist ?: Checklist(
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
        _uiState.update {
            it.copy(checklist = currentChecklist.copy(items = currentChecklist.items + newItem))
        }
    }

    private fun removeChecklistItem(itemId: String) {
        val currentChecklist = _uiState.value.checklist ?: return
        _uiState.update {
            it.copy(checklist = currentChecklist.copy(items = currentChecklist.items.filter { item -> item.id != itemId }))
        }
    }

    private fun toggleChecklistItem(itemId: String) {
        val currentChecklist = _uiState.value.checklist ?: return
        _uiState.update {
            it.copy(
                checklist = currentChecklist.copy(
                    items = currentChecklist.items.map { item ->
                        if (item.id == itemId) item.copy(isCompleted = !item.isCompleted) else item
                    }
                )
            )
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

    private fun checkConflicts() {
        val conflictUseCase = detectEventConflictsUseCase ?: return
        val currentTemporal = _uiState.value.temporal ?: return
        val dummyEvent = Event(
            id = _uiState.value.eventId ?: "preview_event",
            title = _uiState.value.title.ifBlank { "New Reminder" },
            eventTypeId = _uiState.value.eventTypeId.ifBlank { "type_default" },
            categoryId = _uiState.value.categoryId.ifBlank { "cat_default" },
            temporal = currentTemporal,
            recurrenceRule = _uiState.value.recurrenceRule,
            createdAt = timeProvider.nowInstant(),
            updatedAt = timeProvider.nowInstant()
        )

        viewModelScope.launch(dispatcherProvider.main) {
            val conflicts = runCatching { conflictUseCase(dummyEvent) }.getOrDefault(emptyList())
            _uiState.update { it.copy(detectedConflicts = conflicts) }
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

            val now = timeProvider.nowInstant()
            val location = if (state.locationDisplayName.isNotBlank()) {
                val matched = state.availableSavedLocations.firstOrNull {
                    it.name.equals(state.locationDisplayName.trim(), ignoreCase = true)
                }
                if (matched != null) {
                    LocationData(
                        id = matched.id,
                        placeName = matched.name,
                        addressText = matched.address,
                        building = matched.building,
                        room = matched.room,
                        latitude = matched.latitude,
                        longitude = matched.longitude,
                        notes = matched.notes,
                        isSavedLocation = true
                    )
                } else {
                    LocationData(
                        id = UUID.randomUUID().toString(),
                        placeName = state.locationDisplayName.trim(),
                        isSavedLocation = false
                    )
                }
            } else null

            val event = Event(
                id = state.eventId ?: UUID.randomUUID().toString(),
                title = title,
                description = state.notes?.trim() ?: "",
                priority = state.priority,
                categoryId = state.categoryId,
                eventTypeId = state.eventTypeId,
                lifecycleStatus = originalEvent?.lifecycleStatus ?: EventLifecycleStatus.ACTIVE,
                isFavorite = originalEvent?.isFavorite ?: false,
                temporal = temporal,
                location = location,
                recurrenceRule = state.recurrenceRule,
                notificationSchedules = state.notificationSchedules,
                checklist = state.checklist,
                groupIds = state.groupIds,
                tagIds = state.tagIds,
                parentEventId = state.parentEventId,
                attachments = state.attachments,
                createdAt = originalEvent?.createdAt ?: now,
                updatedAt = now
            )

            if (state.isEditing && originalEvent?.isRecurring == true && editRecurringSeriesUseCase != null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showRecurringSeriesDialog = true,
                        pendingEditedDraft = event,
                        targetOccurrenceStart = originalEvent?.temporal?.startLocalDateTime()
                    )
                }
                return@launch
            }

            val result = if (state.isEditing) {
                updateEventUseCase(event)
            } else {
                createEventUseCase(event)
            }

            when (result) {
                is Result.Success -> {
                    originalEvent = event
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = true,
                            eventId = event.id,
                            isEditing = true
                        )
                    }
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

    private fun saveRecurringSeries(mode: com.example.offlinereminder.domain.usecase.RecurringSeriesEditMode) {
        val pending = _uiState.value.pendingEditedDraft ?: return
        val orig = originalEvent ?: return
        val startLdt = _uiState.value.targetOccurrenceStart ?: orig.temporal.startLocalDateTime()
        val zone = when (val t = orig.temporal) {
            is EventDateTime.DateTime -> t.zoneId
            is EventDateTime.DateTimeRange -> t.zoneId
            else -> timeProvider.systemZoneId()
        }

        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isSaving = true, showRecurringSeriesDialog = false) }
            val savedEvent: Event = if (editRecurringSeriesUseCase != null) {
                when (val r = editRecurringSeriesUseCase(orig, startLdt, zone, pending, mode)) {
                    is Result.Success -> r.data
                    is Result.Failure -> {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = r.error.message ?: "Failed to update recurring series"
                            )
                        }
                        return@launch
                    }
                }
            } else {
                when (val r = updateEventUseCase(pending)) {
                    is Result.Success -> pending
                    is Result.Failure -> {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = r.error.message ?: "Failed to update recurring series"
                            )
                        }
                        return@launch
                    }
                }
            }

            originalEvent = savedEvent
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isSaved = true,
                    eventId = savedEvent.id,
                    pendingEditedDraft = null
                )
            }
        }
    }
}
