package com.example.offlinereminder.presentation.settings

import com.example.offlinereminder.domain.backup.BackupExportResult
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.RestoreSummary
import com.example.offlinereminder.domain.model.AppSettings
import com.example.offlinereminder.domain.model.Category
import com.example.offlinereminder.domain.model.EventType

/**
 * Immutable UI state model for Settings, Taxonomy, and Backup management.
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val categories: List<Category> = emptyList(),
    val eventTypes: List<EventType> = emptyList(),
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastExportResult: BackupExportResult? = null,
    val lastRestoreSummary: RestoreSummary? = null,
    val selectedStrategy: RestoreStrategy = RestoreStrategy.REPLACE_ALL,
    val successMessage: String? = null,
    val errorMessage: String? = null
)
