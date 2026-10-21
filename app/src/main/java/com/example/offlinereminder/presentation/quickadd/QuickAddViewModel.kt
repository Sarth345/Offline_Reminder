package com.example.offlinereminder.presentation.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.quickadd.CreateEventFromQuickAddUseCase
import com.example.offlinereminder.domain.quickadd.QuickAddParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing immutable state, live deterministic parsing,
 * and persistence for Quick Add.
 */
class QuickAddViewModel(
    private val parser: QuickAddParser,
    private val createEventFromQuickAddUseCase: CreateEventFromQuickAddUseCase,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _uiState.asStateFlow()

    fun onAction(action: QuickAddAction) {
        when (action) {
            is QuickAddAction.UpdateInput -> updateInput(action.text)
            is QuickAddAction.Save -> saveDraft()
            is QuickAddAction.Reset -> reset()
            is QuickAddAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun updateInput(text: String) {
        val draft = if (text.isBlank()) null else parser.parse(text)
        _uiState.update {
            it.copy(
                input = text,
                draft = draft,
                saveSuccess = false,
                errorMessage = null
            )
        }
    }

    private fun saveDraft() {
        val draft = _uiState.value.draft ?: return
        if (!draft.isValid) return

        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = createEventFromQuickAddUseCase(draft)
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                            savedEventId = result.data.id,
                            errorMessage = null
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = false,
                            errorMessage = result.error.message ?: "Failed to save reminder"
                        )
                    }
                }
            }
        }
    }

    private fun reset() {
        _uiState.value = QuickAddUiState()
    }
}
