package com.example.offlinereminder.presentation.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.GetUpcomingOccurrencesUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * ViewModel managing immutable state and user actions for the Upcoming screen.
 */
class UpcomingViewModel(
    private val getUpcomingOccurrencesUseCase: GetUpcomingOccurrencesUseCase,
    private val completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase,
    private val skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase,
    private val rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase? = null,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpcomingUiState(isLoading = true))
    val uiState: StateFlow<UpcomingUiState> = _uiState.asStateFlow()

    init {
        loadUpcoming()
    }

    fun onAction(action: UpcomingAction) {
        when (action) {
            is UpcomingAction.Refresh -> loadUpcoming()
            is UpcomingAction.CompleteOccurrence -> completeOccurrence(action.occurrence, action.actualExecutionDateTime)
            is UpcomingAction.SkipOccurrence -> skipOccurrence(action.occurrence)
            is UpcomingAction.RescheduleOccurrence -> rescheduleOccurrence(action.occurrence, action.newStartDateTime, action.newEndDateTime)
            is UpcomingAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun loadUpcoming() {
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getUpcomingOccurrencesUseCase()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load upcoming occurrences"
                        )
                    }
                }
                .collect { list ->
                    val grouped = list.groupBy { it.occurrence.effectiveStartDateTime.toLocalDate() }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            occurrences = list,
                            occurrencesByDate = grouped,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    private fun completeOccurrence(occurrence: EventOccurrence, actualExecutionDateTime: LocalDateTime?) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = completeOccurrenceActionUseCase(occurrence, actualExecutionDateTime)
            if (result is Result.Failure) {
                _uiState.update {
                    it.copy(errorMessage = result.error.message ?: "Failed to complete occurrence")
                }
            }
        }
    }

    private fun skipOccurrence(occurrence: EventOccurrence) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = skipOccurrenceActionUseCase(occurrence)
            if (result is Result.Failure) {
                _uiState.update {
                    it.copy(errorMessage = result.error.message ?: "Failed to skip occurrence")
                }
            }
        }
    }

    private fun rescheduleOccurrence(
        occurrence: EventOccurrence,
        newStartDateTime: LocalDateTime,
        newEndDateTime: LocalDateTime?
    ) {
        viewModelScope.launch(dispatcherProvider.main) {
            val result = rescheduleOccurrenceActionUseCase?.invoke(
                occurrence = occurrence,
                newStartDateTime = newStartDateTime,
                newEndDateTime = newEndDateTime
            )
            if (result is Result.Failure) {
                _uiState.update {
                    it.copy(errorMessage = result.error.message ?: "Failed to reschedule occurrence")
                }
            }
        }
    }
}
