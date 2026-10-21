package com.example.offlinereminder.presentation.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.usecase.ExportEventsUseCase
import com.example.offlinereminder.domain.usecase.ExportFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream

data class ExportUiState(
    val selectedFormat: ExportFormat = ExportFormat.ICS,
    val includeArchived: Boolean = false,
    val isExporting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class ExportViewModel(
    private val exportEventsUseCase: ExportEventsUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun selectFormat(format: ExportFormat) {
        _uiState.update { it.copy(selectedFormat = format, successMessage = null, errorMessage = null) }
    }

    fun setIncludeArchived(include: Boolean) {
        _uiState.update { it.copy(includeArchived = include) }
    }

    fun exportToStream(outputStream: OutputStream) {
        _uiState.update { it.copy(isExporting = true, successMessage = null, errorMessage = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val format = _uiState.value.selectedFormat
                val includeArchived = _uiState.value.includeArchived
                val result = exportEventsUseCase(format, includeArchived)
                when (result) {
                    is Result.Success -> {
                        outputStream.bufferedWriter().use { writer ->
                            writer.write(result.data)
                        }
                        _uiState.update {
                            it.copy(
                                isExporting = false,
                                successMessage = "Successfully exported as ${format.name}!"
                            )
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update {
                            it.copy(
                                isExporting = false,
                                errorMessage = result.error.message ?: "Export failed"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = e.message ?: "Failed to write export file"
                    )
                }
            } finally {
                runCatching { outputStream.close() }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
