package com.example.offlinereminder.presentation.settings

import com.example.offlinereminder.domain.backup.RestoreStrategy
import java.io.InputStream
import java.io.OutputStream

/**
 * User actions supported on the Settings / Backup & Restore screen.
 */
sealed interface SettingsAction {
    data class ExportBackup(val outputStream: OutputStream) : SettingsAction
    data class ImportBackup(
        val inputStream: InputStream,
        val strategy: RestoreStrategy = RestoreStrategy.REPLACE_ALL
    ) : SettingsAction
    data class SelectRestoreStrategy(val strategy: RestoreStrategy) : SettingsAction
    data object DismissMessages : SettingsAction
}
