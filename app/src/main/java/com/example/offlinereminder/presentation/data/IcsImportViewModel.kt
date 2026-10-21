package com.example.offlinereminder.presentation.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.ics.IcsCalendarPreview
import com.example.offlinereminder.domain.usecase.ConfirmIcsImportUseCase
import com.example.offlinereminder.domain.usecase.PreviewIcsImportUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

data class IcsImportUiState(
    val isAnalyzing: Boolean = false,
    val preview: IcsCalendarPreview? = null,
    val isImporting: Boolean = false,
    val importedCount: Int? = null,
    val errorMessage: String? = null
)

class IcsImportViewModel(
    private val previewIcsImportUseCase: PreviewIcsImportUseCase,
    private val confirmIcsImportUseCase: ConfirmIcsImportUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(IcsImportUiState())
    val uiState: StateFlow<IcsImportUiState> = _uiState.asStateFlow()

    fun analyzeFile(inputStream: InputStream) {
        _uiState.update { it.copy(isAnalyzing = true, preview = null, importedCount = null, errorMessage = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val content = inputStream.bufferedReader().use { it.readText() }
                val result = previewIcsImportUseCase(content)
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                preview = result.data,
                                errorMessage = null
                            )
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                errorMessage = result.error.message ?: "Failed to parse iCalendar file"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = e.message ?: "Error reading selected file"
                    )
                }
            } finally {
                runCatching { inputStream.close() }
            }
        }
    }

    fun confirmImport() {
        val preview = _uiState.value.preview ?: return
        _uiState.update { it.copy(isImporting = true, errorMessage = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            val result = confirmIcsImportUseCase(preview.candidateEvents, preview.candidateExceptions)
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            preview = null,
                            importedCount = result.data,
                            errorMessage = null
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = result.error.message ?: "Failed to import events"
                        )
                    }
                }
            }
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(preview = null, errorMessage = null) }
    }

    fun clearImportedStatus() {
        _uiState.update { it.copy(importedCount = null) }
    }
}
