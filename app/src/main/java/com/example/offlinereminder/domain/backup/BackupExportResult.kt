package com.example.offlinereminder.domain.backup

/**
 * Result metadata produced by a successful backup export.
 */
data class BackupExportResult(
    val backupVersion: Int,
    val totalEvents: Int,
    val totalCategories: Int,
    val totalGroups: Int,
    val totalAttachments: Int,
    val exportedAt: String,
    val archiveSizeBytes: Long
)
