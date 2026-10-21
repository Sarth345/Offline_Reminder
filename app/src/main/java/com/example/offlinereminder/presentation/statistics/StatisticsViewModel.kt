package com.example.offlinereminder.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinereminder.core.dispatcher.DispatcherProvider
import com.example.offlinereminder.domain.statistics.EventStatistics
import com.example.offlinereminder.domain.statistics.ProductivityInsight
import com.example.offlinereminder.domain.usecase.GetProductivityInsightsUseCase
import com.example.offlinereminder.domain.usecase.GetStatisticsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val statistics: EventStatistics? = null,
    val insights: List<ProductivityInsight> = emptyList(),
    val errorMessage: String? = null
)

class StatisticsViewModel(
    private val getStatisticsUseCase: GetStatisticsUseCase,
    private val getProductivityInsightsUseCase: GetProductivityInsightsUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    fun loadStatistics() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val stats = getStatisticsUseCase()
                val insights = getProductivityInsightsUseCase.generateInsights(stats)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statistics = stats,
                        insights = insights,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to compute statistics"
                    )
                }
            }
        }
    }
}
