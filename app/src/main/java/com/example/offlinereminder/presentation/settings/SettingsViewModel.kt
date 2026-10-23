package com.example.offlinereminder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.data.sound.CustomSoundStorage
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventDateTime
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.model.NotificationSchedule
import com.example.offlinereminder.domain.model.NotificationTriggerType
import com.example.offlinereminder.domain.model.ReminderDeliveryType
import com.example.offlinereminder.domain.model.SoundReference
import com.example.offlinereminder.domain.model.SoundSourceType
import com.example.offlinereminder.domain.notification.AlarmScheduler
import com.example.offlinereminder.domain.usecase.AddCustomSoundUseCase
import com.example.offlinereminder.domain.usecase.CreateCategoryUseCase
import com.example.offlinereminder.domain.usecase.CreateEventUseCase
import com.example.offlinereminder.domain.usecase.CreateEventTypeUseCase
import com.example.offlinereminder.domain.usecase.DeleteCategoryUseCase
import com.example.offlinereminder.domain.usecase.DeleteCustomSoundUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventTypeUseCase
import com.example.offlinereminder.domain.usecase.ExportBackupUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetCustomSoundsUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import com.example.offlinereminder.domain.usecase.GetSettingsUseCase
import com.example.offlinereminder.domain.usecase.ResetSettingsUseCase
import com.example.offlinereminder.domain.usecase.RestoreBackupUseCase
import com.example.offlinereminder.domain.usecase.UpdateCategoryUseCase
import com.example.offlinereminder.domain.usecase.UpdateEventTypeUseCase
import com.example.offlinereminder.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.UUID

/**
 * ViewModel managing App Settings, Taxonomy Management, Notifications & Alarms, and Backup/Restore.
 */
class SettingsViewModel(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val resetSettingsUseCase: ResetSettingsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val createEventTypeUseCase: CreateEventTypeUseCase,
    private val updateEventTypeUseCase: UpdateEventTypeUseCase,
    private val deleteEventTypeUseCase: DeleteEventTypeUseCase,
    private val exportBackupUseCase: ExportBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val alarmScheduler: AlarmScheduler? = null,
    private val getCustomSoundsUseCase: GetCustomSoundsUseCase? = null,
    private val addCustomSoundUseCase: AddCustomSoundUseCase? = null,
    private val deleteCustomSoundUseCase: DeleteCustomSoundUseCase? = null,
    private val customSoundStorage: CustomSoundStorage? = null,
    private val createEventUseCase: CreateEventUseCase? = null,
    private val timeProvider: TimeProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeCategories()
        observeEventTypes()
        observeCustomSounds()
        refreshCapabilities(true)
    }

    private fun observeSettings() {
        viewModelScope.launch(dispatcherProvider.io) {
            getSettingsUseCase().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun observeCategories() {
        viewModelScope.launch(dispatcherProvider.io) {
            getCategoriesUseCase().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    private fun observeEventTypes() {
        viewModelScope.launch(dispatcherProvider.io) {
            getEventTypesUseCase().collect { types ->
                _uiState.update { it.copy(eventTypes = types) }
            }
        }
    }

    private fun observeCustomSounds() {
        val useCase = getCustomSoundsUseCase ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            useCase().collect { sounds ->
                _uiState.update { it.copy(customSounds = sounds) }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            // Appearance & Formatting
            is SettingsAction.UpdateThemeMode -> {
                updateSettings { it.copy(themeMode = action.themeMode) }
            }
            is SettingsAction.UpdateTimeFormat -> {
                updateSettings { it.copy(use24HourFormat = action.use24Hour) }
            }
            is SettingsAction.UpdateDateFormat -> {
                updateSettings { it.copy(dateFormat = action.dateFormat) }
            }
            is SettingsAction.UpdateFirstDayOfWeek -> {
                updateSettings { it.copy(firstDayOfWeek = action.firstDay) }
            }

            // Event Defaults
            is SettingsAction.UpdateDefaultPriority -> {
                updateSettings { it.copy(defaultEventPriority = action.priority) }
            }
            is SettingsAction.UpdateDefaultDuration -> {
                updateSettings { it.copy(defaultEventDurationMinutes = action.minutes) }
            }
            is SettingsAction.UpdateDefaultSnooze -> {
                updateSettings { it.copy(defaultSnoozeDurationMinutes = action.minutes) }
            }

            // Notifications & Alarms
            is SettingsAction.ToggleSound -> {
                updateSettings { it.copy(soundEnabled = action.enabled) }
            }
            is SettingsAction.ToggleVibration -> {
                updateSettings { it.copy(vibrationEnabled = action.enabled) }
            }
            is SettingsAction.UpdateQuietHours -> {
                updateSettings { it.copy(quietHours = action.quietHours) }
            }
            is SettingsAction.ToggleNotificationsEnabled -> {
                updateSettings { it.copy(notificationsEnabled = action.enabled) }
            }
            is SettingsAction.ToggleAlarmModeEnabled -> {
                updateSettings { it.copy(alarmModeEnabled = action.enabled) }
            }
            is SettingsAction.UpdateNotificationSound -> {
                updateSettings { it.copy(notificationSound = action.sound) }
            }
            is SettingsAction.UpdateAlarmSound -> {
                updateSettings { it.copy(alarmSound = action.sound) }
            }
            is SettingsAction.RefreshCapabilities -> {
                refreshCapabilities(action.areNotificationsEnabled)
            }
            is SettingsAction.AddCustomSound -> {
                addCustomSound(
                    title = action.title,
                    fileName = action.fileName,
                    mimeType = action.mimeType,
                    fileSizeBytes = action.fileSizeBytes,
                    inputStream = action.inputStream,
                    applyToNotification = action.applyToNotification,
                    applyToAlarm = action.applyToAlarm
                )
            }
            is SettingsAction.DeleteCustomSound -> {
                deleteCustomSound(action.soundId)
            }
            is SettingsAction.PreviewSound -> {
                _uiState.update { it.copy(previewingSoundType = action.type) }
            }
            is SettingsAction.StopPreviewSound -> {
                _uiState.update { it.copy(previewingSoundType = null) }
            }
            is SettingsAction.ScheduleTestNotification -> {
                scheduleTestNotification(action.isAlarm)
            }

            // Reset
            is SettingsAction.ResetSettings -> resetSettings()

            // Category Management
            is SettingsAction.CreateCategory -> createCategory(action.name, action.color, action.icon, action.sortOrder)
            is SettingsAction.UpdateCategory -> updateCategory(action.category)
            is SettingsAction.DeleteCategory -> deleteCategory(action.categoryId)

            // Event Type Management
            is SettingsAction.CreateEventType -> createEventType(action.name, action.categoryId, action.key, action.defaultIcon, action.defaultColor)
            is SettingsAction.UpdateEventType -> updateEventType(action.eventType)
            is SettingsAction.DeleteEventType -> deleteEventType(action.eventTypeId)

            // Backup & Restore
            is SettingsAction.ExportBackup -> exportBackup(action.outputStream)
            is SettingsAction.ImportBackup -> importBackup(action.inputStream, action.strategy)
            is SettingsAction.SelectRestoreStrategy -> {
                _uiState.update { it.copy(selectedStrategy = action.strategy) }
            }
            is SettingsAction.DismissMessages -> {
                _uiState.update { it.copy(successMessage = null, errorMessage = null) }
            }
        }
    }

    fun refreshCapabilities(areNotificationsEnabled: Boolean) {
        val exactAllowed = alarmScheduler?.canScheduleExactAlarms() ?: true
        _uiState.update {
            it.copy(
                isNotificationAllowed = areNotificationsEnabled,
                isExactAlarmAllowed = exactAllowed
            )
        }
    }

    private fun addCustomSound(
        title: String,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        inputStream: InputStream,
        applyToNotification: Boolean,
        applyToAlarm: Boolean
    ) {
        val storage = customSoundStorage
        val addUseCase = addCustomSoundUseCase
        if (storage == null || addUseCase == null) {
            _uiState.update { it.copy(errorMessage = "Custom sound storage not available") }
            return
        }

        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val ext = fileName.substringAfterLast('.', "").lowercase()
                val allowedExtensions = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus")
                if (ext !in allowedExtensions && !mimeType.startsWith("audio/")) {
                    _uiState.update { it.copy(errorMessage = "Unsupported audio format: .$ext. Allowed: mp3, wav, ogg, m4a, aac, flac, opus") }
                    return@launch
                }
                if (fileSizeBytes <= 0 || fileSizeBytes > 25 * 1024 * 1024) {
                    _uiState.update { it.copy(errorMessage = "File size must be between 1 byte and 25 MB") }
                    return@launch
                }

                val safeFileName = "${UUID.randomUUID()}_${File(fileName).name}"
                storage.copySound(inputStream, safeFileName)

                when (val result = addUseCase(
                    title = title.ifBlank { fileName.substringBeforeLast('.') },
                    fileName = safeFileName,
                    mimeType = mimeType.ifBlank { "audio/*" },
                    fileSizeBytes = fileSizeBytes
                )) {
                    is Result.Success -> {
                        val soundMeta = result.data
                        val soundRef = SoundReference(
                            type = SoundSourceType.CUSTOM,
                            customSoundId = soundMeta.id
                        )
                        if (applyToNotification) {
                            updateSettings { it.copy(notificationSound = soundRef) }
                        }
                        if (applyToAlarm) {
                            updateSettings { it.copy(alarmSound = soundRef) }
                        }
                        _uiState.update { it.copy(successMessage = "Custom sound '${soundMeta.title}' added successfully!") }
                    }
                    is Result.Failure -> {
                        storage.deleteSound(safeFileName)
                        _uiState.update { it.copy(errorMessage = "Failed to save sound metadata: ${result.error.message}") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error importing sound file: ${e.message}") }
            }
        }
    }

    private fun deleteCustomSound(soundId: String) {
        val deleteUseCase = deleteCustomSoundUseCase ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            when (val r = deleteUseCase(soundId)) {
                is Result.Success -> {
                    updateSettings { s ->
                        var updated = s
                        if (s.notificationSound.customSoundId == soundId) {
                            updated = updated.copy(notificationSound = SoundReference(SoundSourceType.DEFAULT))
                        }
                        if (s.alarmSound.customSoundId == soundId) {
                            updated = updated.copy(alarmSound = SoundReference(SoundSourceType.DEFAULT))
                        }
                        updated
                    }
                    _uiState.update { it.copy(successMessage = "Custom sound removed") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = "Cannot delete sound: ${r.error.message}") }
                }
            }
        }
    }

    private fun scheduleTestNotification(isAlarm: Boolean) {
        val createUseCase = createEventUseCase ?: return
        val tp = timeProvider ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            val nowLdt = tp.nowLocalDateTime()
            val targetTime = nowLdt.plusSeconds(5)
            val schedule = NotificationSchedule(
                offsetDuration = Duration.ZERO,
                triggerType = NotificationTriggerType.AT_START,
                deliveryType = if (isAlarm) ReminderDeliveryType.ALARM else ReminderDeliveryType.NOTIFICATION
            )
            val event = Event(
                id = UUID.randomUUID().toString(),
                title = if (isAlarm) "Test Alarm" else "Test Notification",
                description = "Planerize verification alert (5s delay)",
                eventTypeId = _uiState.value.eventTypes.firstOrNull()?.id ?: "type_general",
                categoryId = _uiState.value.categories.firstOrNull()?.id ?: "cat_general",
                temporal = EventDateTime.DateTime(
                    dateTime = targetTime,
                    zoneId = tp.systemZoneId()
                ),
                notificationSchedules = listOf(schedule),
                createdAt = tp.nowInstant(),
                updatedAt = tp.nowInstant()
            )
            when (val r = createUseCase(event)) {
                is Result.Success -> {
                    _uiState.update { it.copy(successMessage = if (isAlarm) "Test alarm scheduled for 5s from now! Lock phone or wait." else "Test notification scheduled for 5s from now! Lock phone or wait.") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = "Failed to schedule test: ${r.error.message}") }
                }
            }
        }
    }

    private fun updateSettings(transform: (com.example.offlinereminder.domain.model.AppSettings) -> com.example.offlinereminder.domain.model.AppSettings) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = updateSettingsUseCase(transform)) {
                is Result.Success -> {
                    _uiState.update { it.copy(settings = res.data) }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = "Failed to update settings: ${res.error.message}") }
                }
            }
        }
    }

    private fun resetSettings() {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = resetSettingsUseCase()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            settings = res.data,
                            successMessage = "Settings reset to defaults"
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = "Failed to reset settings: ${res.error.message}") }
                }
            }
        }
    }

    private fun createCategory(name: String, color: Long, icon: String, sortOrder: Int) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = createCategoryUseCase(name, color.toInt(), icon, sortOrder)) {
                is Result.Success -> {
                    _uiState.update { it.copy(successMessage = "Category '${res.data.name}' created") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.error.message ?: "Failed to create category") }
                }
            }
        }
    }

    private fun updateCategory(category: Category) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = updateCategoryUseCase(category)) {
                is Result.Success -> {
                    _uiState.update { it.copy(successMessage = "Category updated") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.error.message ?: "Failed to update category") }
                }
            }
        }
    }

    private fun deleteCategory(categoryId: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = deleteCategoryUseCase(categoryId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(successMessage = "Category deleted") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.error.message ?: "Failed to delete category") }
                }
            }
        }
    }

    private fun createEventType(name: String, categoryId: String, key: String, defaultIcon: String?, defaultColor: Int?) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = createEventTypeUseCase(name, categoryId, key, defaultIcon, defaultColor)) {
                is Result.Success -> {
                    _uiState.update { it.copy(successMessage = "Event Type '${res.data.name}' created") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.error.message ?: "Failed to create event type") }
                }
            }
        }
    }

    private fun updateEventType(eventType: EventType) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = updateEventTypeUseCase(eventType)) {
                is Result.Success -> {
                    _uiState.update { it.copy(successMessage = "Event Type updated") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.error.message ?: "Failed to update event type") }
                }
            }
        }
    }

    private fun deleteEventType(eventTypeId: String) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = deleteEventTypeUseCase(eventTypeId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(successMessage = "Event Type deleted") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.error.message ?: "Failed to delete event type") }
                }
            }
        }
    }

    private fun exportBackup(outputStream: OutputStream) {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isExporting = true, successMessage = null, errorMessage = null) }
            when (val res = exportBackupUseCase(outputStream)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            lastExportResult = res.data,
                            successMessage = "Backup exported successfully: ${res.data.totalEvents} events, ${res.data.totalCategories} categories"
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            errorMessage = res.error.message ?: "Backup export failed"
                        )
                    }
                }
            }
        }
    }

    private fun importBackup(inputStream: InputStream, strategy: RestoreStrategy) {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isImporting = true, successMessage = null, errorMessage = null) }
            when (val res = restoreBackupUseCase(inputStream, strategy)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            lastRestoreSummary = res.data,
                            successMessage = "Backup restored successfully: ${res.data.eventsRestored} events, ${res.data.categoriesRestored} categories"
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = res.error.message ?: "Backup import failed"
                        )
                    }
                }
            }
        }
    }
}
