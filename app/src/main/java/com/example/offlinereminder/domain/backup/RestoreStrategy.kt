package com.example.offlinereminder.domain.backup

/**
 * Deterministic strategy for resolving conflicts during database restoration.
 */
enum class RestoreStrategy {
    /**
     * Wipes all existing local database tables and restores state cleanly from the backup.
     */
    REPLACE_ALL,

    /**
     * Preserves existing data not in the backup, but overwrites matching IDs with backup versions.
     */
    MERGE_OVERWRITE,

    /**
     * Preserves existing local data for any matching IDs, and only inserts records that do not exist locally.
     */
    MERGE_KEEP_EXISTING
}
