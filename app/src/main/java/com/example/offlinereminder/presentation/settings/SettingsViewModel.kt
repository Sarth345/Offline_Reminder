package com.example.offlinereminder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.EventType
import com.example.offlinereminder.domain.usecase.CreateCategoryUseCase
import com.example.offlinereminder.domain.usecase.CreateEventTypeUseCase
import com.example.offlinereminder.domain.usecase.DeleteCategoryUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventTypeUseCase
import com.example.offlinereminder.domain.usecase.ExportBackupUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
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
import java.io.InputStream
import java.io.OutputStream

/**
 * ViewModel managing App Settings, Taxonomy Management, and Backup/Restore.
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
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeCategories()
        observeEventTypes()
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

            // Notifications & Quiet Hours
            is SettingsAction.ToggleSound -> {
                updateSettings { it.copy(soundEnabled = action.enabled) }
            }
            is SettingsAction.ToggleVibration -> {
                updateSettings { it.copy(vibrationEnabled = action.enabled) }
            }
            is SettingsAction.UpdateQuietHours -> {
                updateSettings { it.copy(quietHours = action.quietHours) }
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
                    _uiState.update { it.copy(successMessage = "Event type '${res.data.name}' created") }
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
                    _uiState.update { it.copy(successMessage = "Event type updated") }
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
                    _uiState.update { it.copy(successMessage = "Event type deleted") }
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.error.message ?: "Failed to delete event type") }
                }
            }
        }
    }

    private fun exportBackup(outputStream: OutputStream) {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isLoading = true, isExporting = true, errorMessage = null, successMessage = null) }
            when (val res = exportBackupUseCase(outputStream)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isExporting = false,
                            lastExportResult = res.data,
                            successMessage = "Backup exported successfully (${res.data.totalEvents} reminders, ${res.data.archiveSizeBytes} bytes)."
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isExporting = false,
                            errorMessage = "Export failed: ${res.error.message}"
                        )
                    }
                }
            }
        }
    }

    private fun importBackup(inputStream: InputStream, strategy: RestoreStrategy) {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isLoading = true, isImporting = true, errorMessage = null, successMessage = null) }
            when (val res = restoreBackupUseCase(inputStream, strategy)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isImporting = false,
                            lastRestoreSummary = res.data,
                            successMessage = "Backup restored successfully (${res.data.eventsRestored} reminders, ${res.data.categoriesRestored} categories, ${res.data.groupsRestored} groups)."
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isImporting = false,
                            errorMessage = "Restore failed: ${res.error.message}"
                        )
                    }
                }
            }
        }
    }
}
