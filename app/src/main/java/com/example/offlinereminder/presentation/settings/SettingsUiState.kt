package com.example.offlinereminder.presentation.settings

import com.example.offlinereminder.domain.backup.BackupExportResult
import com.example.offlinereminder.domain.backup.RestoreStrategy
import com.example.offlinereminder.domain.backup.RestoreSummary

/**
 * Immutable UI state model for Settings / Backup & Restore management.
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastExportResult: BackupExportResult? = null,
    val lastRestoreSummary: RestoreSummary? = null,
    val selectedStrategy: RestoreStrategy = RestoreStrategy.REPLACE_ALL,
    val successMessage: String? = null,
    val errorMessage: String? = null
)
