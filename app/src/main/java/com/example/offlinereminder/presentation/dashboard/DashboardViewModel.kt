package com.example.offlinereminder.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.GetDashboardOccurrencesUseCase
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
 * ViewModel managing immutable state and user actions for the Smart Dashboard screen.
 */
class DashboardViewModel(
    private val getDashboardOccurrencesUseCase: GetDashboardOccurrencesUseCase,
    private val completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase,
    private val skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase,
    private val rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.Refresh -> loadDashboard()
            is DashboardAction.CompleteOccurrence -> completeOccurrence(action.occurrence, action.actualExecutionDateTime)
            is DashboardAction.SkipOccurrence -> skipOccurrence(action.occurrence)
            is DashboardAction.RescheduleOccurrence -> rescheduleOccurrence(action.occurrence, action.newStartDateTime, action.newEndDateTime)
            is DashboardAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getDashboardOccurrencesUseCase()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load dashboard occurrences"
                        )
                    }
                }
                .collect { dashboard ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            todayOccurrences = dashboard.today,
                            overdueOccurrences = dashboard.overdue,
                            tomorrowOccurrences = dashboard.tomorrow,
                            upcomingOccurrences = dashboard.upcoming,
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
            val result = rescheduleOccurrenceActionUseCase(
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
