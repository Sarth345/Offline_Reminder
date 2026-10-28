package com.example.offlinereminder.presentation.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.model.EventOccurrence
import com.example.offlinereminder.domain.model.EventOccurrenceWithEvent
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.GetUpcomingOccurrencesUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ViewModel managing immutable state and user actions for the Upcoming Agenda / Timeline screen.
 */
class UpcomingViewModel(
    private val getUpcomingOccurrencesUseCase: GetUpcomingOccurrencesUseCase,
    private val completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase,
    private val skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase,
    private val rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase? = null,
    private val timeProvider: TimeProvider? = null,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val initialDate: LocalDate = timeProvider?.nowLocalDate() ?: LocalDate.now()
    private val _uiState = MutableStateFlow(
        UpcomingUiState(
            isLoading = true,
            mode = UpcomingMode.NEXT,
            selectedDate = initialDate
        )
    )
    val uiState: StateFlow<UpcomingUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadUpcoming()
    }

    fun onAction(action: UpcomingAction) {
        when (action) {
            is UpcomingAction.Refresh -> loadUpcoming()
            is UpcomingAction.SelectMode -> {
                _uiState.update { it.copy(mode = action.mode) }
                loadUpcoming()
            }
            is UpcomingAction.SelectDate -> {
                _uiState.update { it.copy(mode = UpcomingMode.DATE, selectedDate = action.date) }
                loadUpcoming()
            }
            is UpcomingAction.NavigateDays -> {
                _uiState.update { it.copy(mode = UpcomingMode.DATE, selectedDate = it.selectedDate.plusDays(action.days)) }
                loadUpcoming()
            }
            is UpcomingAction.JumpToToday -> {
                val today = timeProvider?.nowLocalDate() ?: LocalDate.now()
                _uiState.update { it.copy(mode = UpcomingMode.DATE, selectedDate = today) }
                loadUpcoming()
            }
            is UpcomingAction.ChangeHorizon -> {
                _uiState.update { it.copy(horizonDays = action.days) }
                loadUpcoming()
            }
            is UpcomingAction.ToggleIncludeCompleted -> {
                _uiState.update { it.copy(includeCompleted = !it.includeCompleted) }
                loadUpcoming()
            }
            is UpcomingAction.ToggleIncludeOverdue -> {
                _uiState.update { it.copy(includeOverdue = !it.includeOverdue) }
                loadUpcoming()
            }
            is UpcomingAction.CompleteOccurrence -> completeOccurrence(action.occurrence, action.actualExecutionDateTime)
            is UpcomingAction.SkipOccurrence -> skipOccurrence(action.occurrence)
            is UpcomingAction.RescheduleOccurrence -> rescheduleOccurrence(action.occurrence, action.newStartDateTime, action.newEndDateTime)
            is UpcomingAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun loadUpcoming() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(dispatcherProvider.main) {
            val currentState = _uiState.value
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val flow = when (currentState.mode) {
                UpcomingMode.NEXT -> {
                    val today = timeProvider?.nowLocalDate() ?: LocalDate.now()
                    getUpcomingOccurrencesUseCase.nextOccurrencePerEvent(afterDate = today)
                }
                UpcomingMode.OVERDUE -> {
                    val nowLdt = timeProvider?.nowLocalDateTime() ?: LocalDateTime.now()
                    getUpcomingOccurrencesUseCase.overdueOccurrences(asOf = nowLdt, maxPerEvent = 4)
                }
                UpcomingMode.DATE -> {
                    getUpcomingOccurrencesUseCase.occurrencesForDate(selectedDate = currentState.selectedDate)
                }
            }

            flow
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load upcoming occurrences"
                        )
                    }
                }
                .collect { list ->
                    val grouped = mutableMapOf<LocalDate, MutableList<EventOccurrenceWithEvent>>()

                    for (item in list) {
                        val occ = item.occurrence
                        val startDay = occ.effectiveStartDateTime.toLocalDate()
                        val endDay = occ.effectiveEndDateTime?.toLocalDate() ?: startDay
                        val effectiveEndDay = if (endDay.isBefore(startDay)) startDay else endDay

                        var curr = startDay
                        while (!curr.isAfter(effectiveEndDay)) {
                            grouped.getOrPut(curr) { mutableListOf() }.add(item)
                            curr = curr.plusDays(1)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            occurrences = list,
                            occurrencesByDate = grouped.toSortedMap(),
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
