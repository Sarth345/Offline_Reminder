package com.example.offlinereminder.presentation.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.SystemTimeProvider
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.usecase.CreateEventFromTemplateUseCase
import com.example.offlinereminder.domain.usecase.DeleteEventTemplateUseCase
import com.example.offlinereminder.domain.usecase.GetCategoriesUseCase
import com.example.offlinereminder.domain.usecase.GetEventTemplatesUseCase
import com.example.offlinereminder.domain.usecase.GetEventTypesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplatesViewModel(
    private val getEventTemplatesUseCase: GetEventTemplatesUseCase,
    private val createEventFromTemplateUseCase: CreateEventFromTemplateUseCase,
    private val deleteEventTemplateUseCase: DeleteEventTemplateUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getEventTypesUseCase: GetEventTypesUseCase,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesUiState(isLoading = true))
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun onAction(action: TemplatesAction) {
        when (action) {
            is TemplatesAction.LoadTemplates -> loadData()
            is TemplatesAction.CreateEventFromTemplate -> createEvent(action.templateId)
            is TemplatesAction.DeleteTemplate -> deleteTemplate(action.templateId)
            is TemplatesAction.ClearMessage -> _uiState.update { it.copy(actionMessage = null, errorMessage = null) }
            is TemplatesAction.ClearCreatedEvent -> _uiState.update { it.copy(createdEventId = null) }
        }
    }

    private fun loadData() {
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true) }
            val categories = getCategoriesUseCase().firstOrNull()?.associateBy { it.id } ?: emptyMap()
            val eventTypes = getEventTypesUseCase().firstOrNull()?.associateBy { it.id } ?: emptyMap()

            getEventTemplatesUseCase().collect { templates ->
                _uiState.update {
                    it.copy(
                        templates = templates,
                        categories = categories,
                        eventTypes = eventTypes,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun createEvent(templateId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            val now = timeProvider.nowLocalDateTime()
            when (val result = createEventFromTemplateUseCase(templateId = templateId, startDateTime = now)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            actionMessage = "Event created from template: ${result.data.title}",
                            createdEventId = result.data.id
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(errorMessage = result.error.message ?: "Failed to create event from template")
                    }
                }
            }
        }
    }

    private fun deleteTemplate(templateId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = deleteEventTemplateUseCase(templateId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(actionMessage = "Template deleted successfully") }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(errorMessage = result.error.message ?: "Failed to delete template")
                    }
                }
            }
        }
    }
}
