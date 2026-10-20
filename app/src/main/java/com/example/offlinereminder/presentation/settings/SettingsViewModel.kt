package com.example.offlinereminder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.usecase.ExportBackupUseCase
import com.example.offlinereminder.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

/**
 * ViewModel managing Backup & Restore operations and UI state transitions.
 */
class SettingsViewModel(
    private val exportBackupUseCase: ExportBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onAction(action: SettingsAction) {
        when (action) {
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
