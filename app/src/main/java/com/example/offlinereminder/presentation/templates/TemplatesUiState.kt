package com.example.offlinereminder.presentation.templates

import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.EventTemplate
import com.example.offlinereminder.domain.model.EventType

data class TemplatesUiState(
    val templates: List<EventTemplate> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    val eventTypes: Map<String, EventType> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val createdEventId: String? = null
)
