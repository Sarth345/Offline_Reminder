package com.example.offlinereminder.presentation.archive

import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.Event
import com.example.offlinereminder.domain.model.EventType

data class ArchivedEventItem(
    val event: Event,
    val categoryName: String,
    val eventTypeName: String
)

data class ArchiveUiState(
    val archivedEvents: List<ArchivedEventItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)
