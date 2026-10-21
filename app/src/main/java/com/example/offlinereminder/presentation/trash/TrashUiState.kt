package com.example.offlinereminder.presentation.trash

import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventType

data class TrashedEventItem(
    val event: Event,
    val categoryName: String,
    val eventTypeName: String,
    val daysRemainingBeforePurge: Int
)

data class TrashUiState(
    val trashedEvents: List<TrashedEventItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)
