package com.example.offlinereminder.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DefaultDispatcherProvider
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.core.result.Result
import com.example.offlinereminder.core.time.TimeProvider
import com.example.offlinereminder.domain.usecase.CompleteOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.GetCalendarOccurrencesUseCase
import com.example.offlinereminder.domain.usecase.RescheduleOccurrenceActionUseCase
import com.example.offlinereminder.domain.usecase.SkipOccurrenceActionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * ViewModel managing immutable Calendar state, month-bounded recurrence projections,
 * and occurrence actions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val getCalendarOccurrencesUseCase: GetCalendarOccurrencesUseCase,
    private val completeOccurrenceActionUseCase: CompleteOccurrenceActionUseCase,
    private val skipOccurrenceActionUseCase: SkipOccurrenceActionUseCase,
    private val rescheduleOccurrenceActionUseCase: RescheduleOccurrenceActionUseCase,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val today = timeProvider.nowLocalDate()
    private val currentYearMonth = MutableStateFlow(YearMonth.from(today))
    private val currentSelectedDate = MutableStateFlow(today)

    private val _uiState = MutableStateFlow(
        CalendarUiState(
            displayedYearMonth = YearMonth.from(today),
            selectedDate = today,
            isLoading = true
        )
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        observeCalendar()
    }

    private fun observeCalendar() {
        viewModelScope.launch(dispatcherProvider.main) {
            currentYearMonth.flatMapLatest { yearMonth ->
                _uiState.update { it.copy(isLoading = true, displayedYearMonth = yearMonth) }

                val from = yearMonth.minusMonths(1).atDay(1).atStartOfDay()
                val to = yearMonth.plusMonths(1).atEndOfMonth().atTime(23, 59, 59)

                getCalendarOccurrencesUseCase(from, to)
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load calendar"
                    )
                }
            }.collect { occurrences ->
                val grouped = occurrences.groupBy { it.occurrence.effectiveStartDateTime.toLocalDate() }
                val selected = currentSelectedDate.value
                val selectedOccurrences = grouped[selected] ?: emptyList()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthOccurrences = occurrences,
                        occurrencesByDate = grouped,
                        datesWithOccurrences = grouped.keys,
                        selectedDate = selected,
                        selectedDateOccurrences = selectedOccurrences,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun onAction(action: CalendarAction) {
        when (action) {
            is CalendarAction.PreviousMonth -> {
                val prev = currentYearMonth.value.minusMonths(1)
                currentYearMonth.value = prev
            }
            is CalendarAction.NextMonth -> {
                val next = currentYearMonth.value.plusMonths(1)
                currentYearMonth.value = next
            }
            is CalendarAction.SelectDate -> {
                currentSelectedDate.value = action.date
                val occurrences = _uiState.value.occurrencesByDate[action.date] ?: emptyList()
                _uiState.update {
                    it.copy(
                        selectedDate = action.date,
                        selectedDateOccurrences = occurrences
                    )
                }
            }
            is CalendarAction.JumpToToday -> {
                val now = timeProvider.nowLocalDate()
                val ym = YearMonth.from(now)
                currentSelectedDate.value = now
                if (currentYearMonth.value != ym) {
                    currentYearMonth.value = ym
                } else {
                    val occurrences = _uiState.value.occurrencesByDate[now] ?: emptyList()
                    _uiState.update {
                        it.copy(
                            selectedDate = now,
                            selectedDateOccurrences = occurrences
                        )
                    }
                }
            }
            is CalendarAction.Refresh -> {
                // Trigger re-emission
                currentYearMonth.value = currentYearMonth.value
            }
            is CalendarAction.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is CalendarAction.CompleteOccurrence -> completeOccurrence(action)
            is CalendarAction.SkipOccurrence -> skipOccurrence(action)
            is CalendarAction.RescheduleOccurrence -> rescheduleOccurrence(action)
        }
    }

    private fun completeOccurrence(action: CalendarAction.CompleteOccurrence) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = completeOccurrenceActionUseCase(action.occurrence, action.actualExecutionDateTime)) {
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                }
                is Result.Success -> {}
            }
        }
    }

    private fun skipOccurrence(action: CalendarAction.SkipOccurrence) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = skipOccurrenceActionUseCase(action.occurrence)) {
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                }
                is Result.Success -> {}
            }
        }
    }

    private fun rescheduleOccurrence(action: CalendarAction.RescheduleOccurrence) {
        viewModelScope.launch(dispatcherProvider.io) {
            when (val res = rescheduleOccurrenceActionUseCase(action.occurrence, action.newStartDateTime)) {
                is Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = res.message ?: res.error.message) }
                }
                is Result.Success -> {}
            }
        }
    }
}
