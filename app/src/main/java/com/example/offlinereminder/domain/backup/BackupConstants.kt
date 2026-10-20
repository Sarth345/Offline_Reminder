package com.example.offlinereminder.domain.backup

/**
 * Constants governing versioning and archive structure for local backups.
 */
object BackupConstants {
    const val CURRENT_BACKUP_VERSION = 1
    const val MIN_SUPPORTED_BACKUP_VERSION = 0
    const val BACKUP_JSON_ENTRY_NAME = "backup.json"
    const val ATTACHMENTS_DIR = "attachments"
    const val DEFAULT_APP_VERSION = "1.0"
}
